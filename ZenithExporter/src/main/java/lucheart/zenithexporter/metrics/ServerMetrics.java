package lucheart.zenithexporter.metrics;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientConnectEvent;
import com.zenith.event.client.ClientOnlineEvent;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.event.queue.QueueCompleteEvent;
import com.zenith.event.server.ServerPlayerConnectedEvent;
import com.zenith.event.server.ServerPlayerDisconnectedEvent;
import com.zenith.util.ComponentSerializer;
import io.prometheus.metrics.core.metrics.Counter;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.TPS;
import static lucheart.zenithexporter.ZenithExporterPlugin.PLUGIN_CONFIG;
import static lucheart.zenithexporter.metrics.ZenithState.inGame;

/** The destination server: who is on it and how it's doing. */
public final class ServerMetrics implements MetricGroup {
    private final SessionSettleGate settleGate = new SessionSettleGate();
    private final Counter joins;
    private final Counter leaves;

    public ServerMetrics(Instruments instruments, ServerStatusPoller poller) {
        instruments.gauge("zenith_server_players_online", "Players online on the server, per way of knowing that",
            cb -> {
                var status = poller.status();
                if (status != null && status.response().online().isPresent()) cb.call(status.response().online().getAsInt(), "status");
                if (!inGame()) return;
                cb.call(listedPlayers(), "tablist");
                var players = footer().players();
                if (players.isPresent()) cb.call(players.getAsInt(), "footer");
            }, "source");
        instruments.gauge("zenith_server_players_max", "Player limit of the server",
            cb -> {
                var status = poller.status();
                if (status != null && status.response().max().isPresent()) cb.call(status.response().max().getAsInt());
                else if (inGame() && CACHE.getPlayerCache().getMaxPlayers() > 0) cb.call(CACHE.getPlayerCache().getMaxPlayers());
            });
        instruments.gauge("zenith_server_status_players_reported", "Online count as the server states it, on 2b2t that includes the queue",
            cb -> {
                var status = poller.status();
                if (status != null && status.response().reported().isPresent()) cb.call(status.response().reported().getAsInt());
            });
        instruments.gauge("zenith_server_tablist_entries", "Tab list entries, including ones hidden from the tab list",
            cb -> { if (inGame()) cb.call(CACHE.getTabListCache().getEntries().size()); });
        instruments.gauge("zenith_server_players_latency_avg_seconds", "Average latency of players on the tab list",
            cb -> {
                if (!inGame()) return;
                long total = 0;
                int count = 0;
                for (var entry : CACHE.getTabListCache().getEntries()) {
                    total += entry.getLatency();
                    count++;
                }
                if (count > 0) cb.call(total / (double) count / 1000.0);
            });
        instruments.gauge("zenith_server_player_online", "1 for each player on the tab list. No series means offline, or the proxy not being in game",
            cb -> {
                if (!inGame() || !PLUGIN_CONFIG.playerMetrics) return;
                for (var entry : onlinePlayers()) {
                    var gameMode = entry.getGameMode();
                    cb.call(1, entry.getName(), entry.getProfileId().toString(), gameMode != null ? lower(gameMode.name()) : "unknown");
                }
            }, "player", "uuid", "gamemode");
        instruments.gauge("zenith_server_player_latency_seconds", "Latency of each player on the tab list",
            cb -> {
                if (!inGame() || !PLUGIN_CONFIG.playerMetrics) return;
                for (var entry : onlinePlayers()) cb.call(entry.getLatency() / 1000.0, entry.getName(), entry.getProfileId().toString());
            }, "player", "uuid");
        instruments.gauge("zenith_server_watched_player_online", "Whether a player from the watch list is online",
            cb -> {
                if (!inGame()) return;
                var online = watchedPlayersOnline();
                for (var name : watchedPlayers().values()) cb.call(online.containsKey(lower(name)) ? 1 : 0, name);
            }, "player");
        instruments.gauge("zenith_server_watched_player_latency_seconds", "Latency of an online player from the watch list",
            cb -> {
                if (!inGame()) return;
                var online = watchedPlayersOnline();
                for (var name : watchedPlayers().values()) {
                    var entry = online.get(lower(name));
                    if (entry != null) cb.call(entry.getLatency() / 1000.0, name);
                }
            }, "player");

        instruments.gauge("zenith_server_tps", "Server TPS calculated from time updates",
            cb -> { if (inGame()) cb.call(TPS.getTPSValue()); });
        instruments.gauge("zenith_server_reported_tps", "TPS the server shows in its tab list footer",
            cb -> {
                if (!inGame()) return;
                var tps = footer().tps();
                if (tps.isPresent()) cb.call(tps.getAsDouble());
            });
        instruments.gauge("zenith_server_reported_ping_seconds", "Ping the server shows in its tab list footer",
            cb -> {
                if (!inGame()) return;
                var ping = footer().pingMs();
                if (ping.isPresent()) cb.call(ping.getAsInt() / 1000.0);
            });

        instruments.gauge("zenith_server_status_up", "Whether the server answered the last status ping",
            cb -> {
                var status = poller.status();
                if (status != null) cb.call(status.up() ? 1 : 0);
            });
        instruments.gauge("zenith_server_status_ping_duration_seconds", "How long the last status ping took",
            cb -> {
                var status = poller.status();
                if (status != null && status.up()) cb.call(status.durationSeconds());
            });
        instruments.gauge("zenith_server_status_protocol_version", "Protocol version the server reports in its status",
            cb -> {
                var status = poller.status();
                if (status != null && status.response().protocol().isPresent()) cb.call(status.response().protocol().getAsInt());
            });

        joins = instruments.counter("zenith_server_player_joins_total", "Players joining the server, seen from the tab list");
        leaves = instruments.counter("zenith_server_player_leaves_total", "Players leaving the server, seen from the tab list");
    }

    @Override
    public List<EventConsumer<?>> events() {
        return List.of(
            of(ClientConnectEvent.class, e -> settleGate.sessionStarted(System.currentTimeMillis())),
            of(ClientOnlineEvent.class, e -> settleGate.sessionStarted(System.currentTimeMillis())),
            of(QueueCompleteEvent.class, e -> settleGate.sessionStarted(System.currentTimeMillis())),
            of(ClientTickEvent.Starting.class, e -> settleGate.sessionStarted(System.currentTimeMillis())),
            of(ServerPlayerConnectedEvent.class, e -> { if (countsAsChurn()) joins.inc(); }),
            of(ServerPlayerDisconnectedEvent.class, e -> { if (countsAsChurn()) leaves.inc(); })
        );
    }

    private boolean countsAsChurn() {
        return inGame() && settleGate.counts(System.currentTimeMillis());
    }

    private static long listedPlayers() {
        return CACHE.getTabListCache().getEntries().stream().filter(PlayerListEntry::isListed).count();
    }

    // the tab list is keyed by uuid, and the uuid is a label, so these can't produce a duplicate series
    private static List<PlayerListEntry> onlinePlayers() {
        return CACHE.getTabListCache().getEntries().stream()
            .filter(PlayerListEntry::isListed)
            .filter(entry -> entry.getName() != null)
            .toList();
    }

    private static TabFooter footer() {
        return TabFooter.parse(ComponentSerializer.serializePlain(CACHE.getTabListCache().getFooter()));
    }

    // keyed by lowercase so the same name twice can't produce a duplicate series
    private static HashMap<String, String> watchedPlayers() {
        var watched = new HashMap<String, String>();
        for (var name : PLUGIN_CONFIG.watchedPlayers) watched.putIfAbsent(lower(name), name);
        return watched;
    }

    private static HashMap<String, PlayerListEntry> watchedPlayersOnline() {
        var watched = watchedPlayers();
        var online = new HashMap<String, PlayerListEntry>();
        if (watched.isEmpty()) return online;
        for (var entry : CACHE.getTabListCache().getEntries()) {
            var name = lower(entry.getName());
            if (watched.containsKey(name)) online.put(name, entry);
        }
        return online;
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
