package lucheart.zenithexporter.metrics;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.client.ClientConnectEvent;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.event.client.ClientLoginFailedEvent;
import com.zenith.event.module.AutoReconnectEvent;
import com.zenith.event.server.ServerRestartingEvent;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.core.metrics.Info;
import lucheart.zenithexporter.BuildConstants;

import java.util.List;
import java.util.Locale;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static lucheart.zenithexporter.metrics.ZenithState.inGame;

/** The proxy's connection to the destination server. */
public final class ClientMetrics implements MetricGroup {
    private final Instruments instruments;
    private final Info clientInfo;
    private final Counter connects;
    private final Counter disconnects;
    private final Counter loginFailures;
    private final Counter autoReconnects;
    private final Counter serverRestarts;
    private final Counter bytesReceived;
    private final Counter bytesSent;
    private final Histogram sessionDuration;

    public ClientMetrics(Instruments instruments) {
        this.instruments = instruments;
        instruments.gauge("zenith_client_state", "1 for the state the proxy's client is currently in",
            cb -> {
                var current = ZenithState.current();
                for (var state : ZenithState.ALL) cb.call(state.equals(current) ? 1 : 0, state);
            }, "state");
        instruments.gauge("zenith_client_connected", "Whether the proxy is connected to the server, in queue or not",
            cb -> cb.call(Proxy.getInstance().isConnected() ? 1 : 0));
        instruments.gauge("zenith_client_ping_seconds", "Measured latency to the server",
            cb -> { if (Proxy.getInstance().isConnected()) cb.call(Proxy.getInstance().getClient().getPing() / 1000.0); });
        instruments.gauge("zenith_client_online_duration_seconds", "How long the current session has been online",
            cb -> { if (inGame()) cb.call(Proxy.getInstance().getOnlineTimeSecondsWithQueueSkip()); });
        instruments.gauge("zenith_client_connect_timestamp_seconds", "When the current connection was made",
            cb -> {
                var connectTime = Proxy.getInstance().getConnectTime();
                if (Proxy.getInstance().isConnected() && connectTime != null) cb.call(connectTime.getEpochSecond());
            });
        instruments.gauge("zenith_client_disconnect_timestamp_seconds", "When the proxy last disconnected, while disconnected",
            cb -> {
                if (!Proxy.getInstance().isConnected()) cb.call(Proxy.getInstance().getDisconnectTime().getEpochSecond());
            });
        instruments.gauge("zenith_client_protocol_version", "Protocol version of the connection to the server",
            cb -> { if (Proxy.getInstance().isConnected()) cb.call(Proxy.getInstance().getClient().getProtocolVersionId()); });

        instruments.info("zenith_build", "Versions of ZenithProxy and this plugin", "zenith_version", "mc_version", "exporter_version")
            .setLabelValues(String.valueOf(VERSION), BuildConstants.MC_VERSION, BuildConstants.VERSION);
        clientInfo = instruments.info("zenith_client", "Account and server the proxy is set up with", "username", "server");
        updateClientInfo();

        connects = instruments.counter("zenith_client_connects_total", "Connections made to the server");
        disconnects = instruments.counter("zenith_client_disconnects_total", "Disconnects from the server", "reason");
        for (var reason : DisconnectReasons.ALL) disconnects.initLabelValues(reason);
        loginFailures = instruments.counter("zenith_client_login_failures_total", "Failed account logins");
        autoReconnects = instruments.counter("zenith_client_auto_reconnects_total", "Reconnects scheduled by AutoReconnect");
        serverRestarts = instruments.counter("zenith_server_restart_notices_total", "Server restart messages seen");
        bytesReceived = instruments.counter("zenith_client_received_bytes_total", "Bytes received from the server");
        bytesSent = instruments.counter("zenith_client_sent_bytes_total", "Bytes sent to the server");
        sessionDuration = instruments.histogram("zenith_client_session_duration_seconds", "Online time of finished sessions, not counting queue",
            60, 300, 900, 1800, 3600, 7200, 14400, 28800, 43200, 86400);
    }

    @Override
    public List<EventConsumer<?>> events() {
        return List.of(
            of(ClientConnectEvent.class, this::onConnect),
            of(ClientDisconnectEvent.class, this::onDisconnect),
            of(ClientLoginFailedEvent.class, e -> loginFailures.inc()),
            of(AutoReconnectEvent.class, e -> autoReconnects.inc()),
            of(ServerRestartingEvent.class, e -> serverRestarts.inc())
        );
    }

    private void onConnect(ClientConnectEvent event) {
        connects.inc();
        instruments.guard("client_info", this::updateClientInfo);
        instruments.guard("traffic_counter", this::countTraffic);
    }

    private void onDisconnect(ClientDisconnectEvent event) {
        disconnects.labelValues(DisconnectReasons.classify(event.reason())).inc();
        if (!event.wasInQueue() && !event.onlineDuration().isZero()) {
            sessionDuration.observe(event.onlineDuration().toMillis() / 1000.0);
        }
    }

    private void updateClientInfo() {
        clientInfo.setLabelValues(CONFIG.authentication.username, CONFIG.client.server.address.toLowerCase(Locale.ROOT));
    }

    private void countTraffic() {
        var pipeline = Proxy.getInstance().getClient().getChannel().pipeline();
        if (pipeline.get(TrafficCounter.NAME) == null) {
            pipeline.addFirst(TrafficCounter.NAME, new TrafficCounter(bytesReceived, bytesSent));
        }
    }
}
