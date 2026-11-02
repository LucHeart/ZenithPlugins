package lucheart.zenithexporter.metrics;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.chat.DeathMessageChatEvent;
import com.zenith.event.chat.PublicChatEvent;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.chat.WhisperChatEvent;
import com.zenith.event.module.AutoEatOutOfFoodEvent;
import com.zenith.event.module.HealthAutoDisconnectEvent;
import com.zenith.event.module.NoTotemsEvent;
import com.zenith.event.module.QueueWarningEvent;
import com.zenith.event.player.BlacklistedPlayerConnectedEvent;
import com.zenith.event.player.NonWhitelistedPlayerConnectedEvent;
import com.zenith.event.player.PlayerConnectedEvent;
import com.zenith.event.player.PlayerDisconnectedEvent;
import com.zenith.event.player.SpectatorConnectedEvent;
import com.zenith.event.player.SpectatorDisconnectedEvent;
import com.zenith.network.server.ServerSession;
import io.prometheus.metrics.core.metrics.Counter;

import java.util.HashMap;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/** The proxy itself: who is connected to it, its modules, and what's going through it. */
public final class ProxyMetrics implements MetricGroup {
    private static final String CONTROLLER = "controller";
    private static final String SPECTATOR = "spectator";

    private final Counter connections;
    private final Counter disconnections;
    private final Counter rejected;
    private final Counter chat;
    private final Counter moduleEvents;

    public ProxyMetrics(Instruments instruments) {
        instruments.gauge("zenith_proxy_players_connected", "Players connected to the proxy itself",
            cb -> {
                int controllers = 0;
                int spectators = 0;
                for (var session : sessions()) {
                    if (!session.isLoggedIn()) continue;
                    if (session.isSpectator()) spectators++;
                    else controllers++;
                }
                cb.call(controllers, CONTROLLER);
                cb.call(spectators, SPECTATOR);
            }, "role");
        instruments.gauge("zenith_proxy_player_ping_seconds", "Latency of each player connected to the proxy",
            cb -> {
                // two sessions can have the same name, which would be a duplicate series
                var pings = new HashMap<List<String>, Double>();
                for (var session : sessions()) {
                    if (session.isLoggedIn()) pings.put(List.of(session.getName(), role(session)), session.getPing() / 1000.0);
                }
                pings.forEach((labels, ping) -> cb.call(ping, labels.get(0), labels.get(1)));
            }, "player", "role");
        instruments.gauge("zenith_proxy_start_timestamp_seconds", "When the proxy started",
            cb -> cb.call(Proxy.getInstance().getStartTime() / 1000.0));
        instruments.gauge("zenith_proxy_module_enabled", "Whether each module is enabled",
            cb -> {
                var enabled = new HashMap<String, Boolean>();
                for (var module : MODULE.getModules()) enabled.put(module.getClass().getSimpleName(), module.isEnabled());
                enabled.forEach((module, on) -> cb.call(on ? 1 : 0, module));
            }, "module");
        instruments.gauge("zenith_proxy_plugins_loaded", "Plugins loaded in the proxy",
            cb -> cb.call(PLUGIN_MANAGER.getPluginInfos().size()));
        instruments.gauge("zenith_proxy_discord_connected", "Whether the discord bot is running",
            cb -> cb.call(DISCORD.isRunning() ? 1 : 0));
        instruments.gauge("zenith_proxy_update_available", "Whether a ZenithProxy update is available",
            cb -> cb.call(Proxy.getInstance().getAutoUpdater().getUpdateAvailable() ? 1 : 0));

        connections = instruments.counter("zenith_proxy_player_connections_total", "Players connecting to the proxy", "role");
        disconnections = instruments.counter("zenith_proxy_player_disconnections_total", "Players disconnecting from the proxy", "role");
        for (var role : List.of(CONTROLLER, SPECTATOR)) {
            connections.initLabelValues(role);
            disconnections.initLabelValues(role);
        }
        rejected = instruments.counter("zenith_proxy_rejected_connections_total", "Players kept out of the proxy", "reason");
        rejected.initLabelValues("not_whitelisted");
        rejected.initLabelValues("blacklisted");
        chat = instruments.counter("zenith_chat_messages_total", "Chat messages the account received or sent", "type");
        for (var type : List.of("public", "whisper_in", "whisper_out", "system", "death")) chat.initLabelValues(type);
        moduleEvents = instruments.counter("zenith_module_events_total", "Notable things modules reported", "event");
        for (var event : List.of("auto_eat_out_of_food", "no_totems", "health_auto_disconnect", "queue_warning")) moduleEvents.initLabelValues(event);
    }

    @Override
    public List<EventConsumer<?>> events() {
        return List.of(
            of(PlayerConnectedEvent.class, e -> connections.labelValues(CONTROLLER).inc()),
            of(PlayerDisconnectedEvent.class, e -> disconnections.labelValues(CONTROLLER).inc()),
            of(SpectatorConnectedEvent.class, e -> connections.labelValues(SPECTATOR).inc()),
            of(SpectatorDisconnectedEvent.class, e -> disconnections.labelValues(SPECTATOR).inc()),
            of(NonWhitelistedPlayerConnectedEvent.class, e -> rejected.labelValues("not_whitelisted").inc()),
            of(BlacklistedPlayerConnectedEvent.class, e -> rejected.labelValues("blacklisted").inc()),
            of(PublicChatEvent.class, e -> chat.labelValues("public").inc()),
            of(WhisperChatEvent.class, e -> chat.labelValues(e.outgoing() ? "whisper_out" : "whisper_in").inc()),
            of(SystemChatEvent.class, e -> chat.labelValues("system").inc()),
            of(DeathMessageChatEvent.class, e -> chat.labelValues("death").inc()),
            of(AutoEatOutOfFoodEvent.class, e -> moduleEvents.labelValues("auto_eat_out_of_food").inc()),
            of(NoTotemsEvent.class, e -> moduleEvents.labelValues("no_totems").inc()),
            of(HealthAutoDisconnectEvent.class, e -> moduleEvents.labelValues("health_auto_disconnect").inc()),
            of(QueueWarningEvent.class, e -> moduleEvents.labelValues("queue_warning").inc())
        );
    }

    private static ServerSession[] sessions() {
        return Proxy.getInstance().getActiveConnections().getArray();
    }

    private static String role(ServerSession session) {
        return session.isSpectator() ? SPECTATOR : CONTROLLER;
    }
}
