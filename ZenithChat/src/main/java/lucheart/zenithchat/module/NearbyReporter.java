package lucheart.zenithchat.module;

import lucheart.zenithchat.module.NearbyPlayers.Nearby;
import lucheart.zenithchat.module.NearbyPlayers.Snapshot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.OBJECT_MAPPER;
import static lucheart.zenithchat.ZenithChatPlugin.LOG;
import static lucheart.zenithchat.ZenithChatPlugin.PLUGIN_CONFIG;

/**
 * Sends the players around the account to BlueMapAlert (POST {reportUrl}/sightings) whenever they change, and at
 * least every few seconds, so the receiver can tell a quiet account from one that stopped watching.
 */
public final class NearbyReporter {
    private static final long HEARTBEAT_MS = 5000;
    // smaller moves aren't worth a report, the receiver only cares which area someone is in
    private static final double MOVED_BLOCKS = 1;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private ScheduledExecutorService executor;
    private Snapshot lastSent;
    private long lastSentAt;
    private String lastProblem;

    public synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "ZenithChat-reporter");
            thread.setDaemon(true);
            return thread;
        });
        // fixed delay, so a slow receiver never gets two reports from here at once
        executor.scheduleWithFixedDelay(this::tick, 0, Math.max(200, PLUGIN_CONFIG.reportIntervalMs), TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (executor != null) executor.shutdownNow();
        executor = null;
        lastSent = null;
        lastProblem = null;
    }

    /** Applies a changed interval or address. */
    public synchronized void restart() {
        stop();
        start();
    }

    /** What went wrong with the last report, or null when it went through. */
    public String problem() {
        return lastProblem;
    }

    private void tick() {
        try {
            String url = PLUGIN_CONFIG.reportUrl.strip();
            if (url.isEmpty()) return;

            var snapshot = NearbyPlayers.snapshot();
            long now = System.currentTimeMillis();
            if (!changed(lastSent, snapshot) && now - lastSentAt < HEARTBEAT_MS) return;

            send(url, snapshot);
            lastSent = snapshot;
            lastSentAt = now;
        } catch (Throwable e) {
            // a failed report is tried again on the next tick, which sends whatever is current then
            problem(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void send(String url, Snapshot snapshot) throws Exception {
        var profile = CACHE.getProfileCache().getProfile();
        var body = new LinkedHashMap<String, Object>();
        body.put("bot", profile == null ? "" : profile.getName());
        body.put("inGame", snapshot.inGame());
        body.put("dimension", snapshot.dimension());
        body.put("players", snapshot.players());

        var request = HttpRequest.newBuilder(URI.create(url.replaceAll("/+$", "") + "/sightings"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + PLUGIN_CONFIG.reportToken)
            .POST(HttpRequest.BodyPublishers.ofByteArray(OBJECT_MAPPER.writeValueAsBytes(body)))
            .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("answered " + response.statusCode() + " " + response.body());
        }
        if (lastProblem != null) LOG.info("Reporting nearby players to {} again", url);
        lastProblem = null;
    }

    // logged once until it changes, a receiver that's down would otherwise fill the log every second
    private void problem(String problem) {
        if (!problem.equals(lastProblem)) LOG.warn("Could not report nearby players: {}", problem);
        lastProblem = problem;
    }

    static boolean changed(Snapshot before, Snapshot now) {
        if (before == null || before.inGame() != now.inGame() || !before.dimension().equals(now.dimension())) return true;
        if (before.players().size() != now.players().size()) return true;
        // both are sorted by UUID
        for (int i = 0; i < now.players().size(); i++) {
            Nearby a = before.players().get(i), b = now.players().get(i);
            if (!a.uuid().equals(b.uuid()) || a.invisible() != b.invisible()) return true;
            double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
            if (dx * dx + dy * dy + dz * dz >= MOVED_BLOCKS * MOVED_BLOCKS) return true;
        }
        return false;
    }
}
