package lucheart.zenithexporter.module;

import com.github.rfresh2.EventConsumer;
import com.sun.net.httpserver.BasicAuthenticator;
import com.zenith.module.api.Module;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import lucheart.zenithexporter.metrics.*;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static lucheart.zenithexporter.ZenithExporterPlugin.LOG;
import static lucheart.zenithexporter.ZenithExporterPlugin.PLUGIN_CONFIG;

/**
 * Serves the metrics endpoint. Metrics live as long as the process does and only read zenith's state when
 * scraped, so counters keep their values when the module is toggled or the endpoint is restarted.
 */
public class ZenithExporterModule extends Module {
    private final PrometheusRegistry registry = new PrometheusRegistry();
    private final ServerStatusPoller statusPoller = new ServerStatusPoller();
    private final WorldMetrics worldMetrics;
    private final List<MetricGroup> groups;
    private HTTPServer server;

    public ZenithExporterModule() {
        var instruments = new Instruments(registry, (collector, e) -> LOG.error("Failed collecting {}", collector, e));
        worldMetrics = new WorldMetrics(instruments);
        groups = List.of(
            new ClientMetrics(instruments),
            new QueueMetrics(instruments, statusPoller),
            new ServerMetrics(instruments, statusPoller),
            new PlayerMetrics(instruments),
            new ProxyMetrics(instruments),
            worldMetrics
        );
        // a registry of their own so they can be turned off again without restarting
        var jvmRegistry = new PrometheusRegistry();
        JvmMetrics.builder().register(jvmRegistry);
        registry.register((MultiCollector) () -> PLUGIN_CONFIG.jvmMetrics ? jvmRegistry.scrape() : new MetricSnapshots());
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        var events = new ArrayList<EventConsumer<?>>();
        for (var group : groups) events.addAll(group.events());
        return events;
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
            .setId("zenith-exporter")
            // only looks at packets, and should only count ones no other module cancelled
            .setPriority(Integer.MIN_VALUE)
            .state(ProtocolState.GAME, EntitySpawnPackets.handle(PacketHandlerStateCodec.clientBuilder(), worldMetrics)
                .inbound(ClientboundLevelChunkWithLightPacket.class, worldMetrics::onChunk)
                .inbound(ClientboundSetEntityDataPacket.class, worldMetrics::onEntityData)
                .build())
            .build();
    }

    @Override
    public void onEnable() {
        startServer();
        statusPoller.start();
    }

    @Override
    public void onDisable() {
        statusPoller.stop();
        stopServer();
    }

    /** Applies changed endpoint settings. Returns whether the endpoint is up, if the module is on at all. */
    public synchronized boolean restartServer() {
        if (!isEnabled()) return false;
        stopServer();
        return startServer();
    }

    public void restartStatusPoller() {
        if (isEnabled()) statusPoller.start();
    }

    public synchronized boolean isServing() {
        return server != null;
    }

    public String endpoint() {
        return "http://" + PLUGIN_CONFIG.bindAddress + ":" + PLUGIN_CONFIG.port + "/metrics";
    }

    private synchronized boolean startServer() {
        boolean auth = !PLUGIN_CONFIG.authUsername.isEmpty() && !PLUGIN_CONFIG.authPassword.isEmpty();
        var builder = HTTPServer.builder()
            .hostname(PLUGIN_CONFIG.bindAddress)
            .port(PLUGIN_CONFIG.port)
            .registry(registry);
        if (auth) builder.authenticator(new CredentialsAuthenticator(PLUGIN_CONFIG.authUsername, PLUGIN_CONFIG.authPassword));
        try {
            server = builder.buildAndStart();
        } catch (IOException | RuntimeException e) {
            server = null;
            LOG.error("Failed starting metrics endpoint on {}:{}", PLUGIN_CONFIG.bindAddress, PLUGIN_CONFIG.port, e);
            return false;
        }
        LOG.info("Serving metrics on {}", endpoint());
        if (!auth && !PLUGIN_CONFIG.bindAddress.equals("127.0.0.1") && !PLUGIN_CONFIG.bindAddress.equals("localhost")) {
            LOG.warn("Metrics endpoint is reachable from other hosts without a password");
        }
        return true;
    }

    private synchronized void stopServer() {
        if (server != null) server.stop();
        server = null;
    }

    private static final class CredentialsAuthenticator extends BasicAuthenticator {
        private final byte[] username;
        private final byte[] password;

        CredentialsAuthenticator(String username, String password) {
            super("zenith-exporter");
            this.username = username.getBytes(StandardCharsets.UTF_8);
            this.password = password.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean checkCredentials(String username, String password) {
            // no short-circuit, how far a guess got shouldn't show in the response time
            boolean userMatches = MessageDigest.isEqual(this.username, username.getBytes(StandardCharsets.UTF_8));
            boolean passwordMatches = MessageDigest.isEqual(this.password, password.getBytes(StandardCharsets.UTF_8));
            return userMatches & passwordMatches;
        }
    }
}
