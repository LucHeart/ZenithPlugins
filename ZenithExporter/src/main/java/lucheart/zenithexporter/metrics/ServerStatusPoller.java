package lucheart.zenithexporter.metrics;

import com.zenith.Proxy;
import com.zenith.feature.queue.Queue;
import com.zenith.feature.queue.QueueStatus;
import com.zenith.feature.queue.mcping.MCPing;

import java.util.concurrent.TimeUnit;

import static com.zenith.Globals.CONFIG;
import static lucheart.zenithexporter.ZenithExporterPlugin.PLUGIN_CONFIG;

/**
 * Asks the server for its status in the background so a scrape never waits on the network, and so the
 * player count is known without being connected.
 */
public final class ServerStatusPoller {
    private static final int TIMEOUT_MS = 3000;

    public record Status(boolean up, ServerStatusResponse response, double durationSeconds, long atMs) {}

    private volatile Thread thread;
    private volatile Status status;
    private volatile QueueStatus queueStatus;

    public synchronized void start() {
        stop();
        thread = Thread.ofVirtual().name("ZenithExporter Status").start(this::run);
    }

    public synchronized void stop() {
        if (thread != null) thread.interrupt();
        thread = null;
        status = null;
    }

    /** Latest status, or null if there is none recent enough to trust. */
    public Status status() {
        var current = status;
        if (current == null) return null;
        long maxAgeMs = TimeUnit.SECONDS.toMillis(PLUGIN_CONFIG.statusPingIntervalSeconds * 3L);
        return System.currentTimeMillis() - current.atMs() <= maxAgeMs ? current : null;
    }

    public QueueStatus queueStatus() {
        return queueStatus;
    }

    private void run() {
        var self = Thread.currentThread();
        // start() holds the lock until it has assigned the field
        synchronized (this) {
            if (thread != self) return;
        }
        while (thread == self) {
            var polled = PLUGIN_CONFIG.statusPing && !CONFIG.client.connectionProxy.enabled ? ping() : null;
            // a ping can outlive stop(), its result is no longer wanted then
            if (thread != self) return;
            status = polled;
            queueStatus = Proxy.getInstance().isOn2b2t() ? Queue.getQueueStatus() : null;
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(Math.max(5, PLUGIN_CONFIG.statusPingIntervalSeconds)));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // doesn't go through the connection proxy, so it's not used with one: it would show the server this host's real address
    private Status ping() {
        long start = System.nanoTime();
        try {
            var hostname = CONFIG.client.server.address;
            var address = MCPing.INSTANCE.resolveAddress(hostname, CONFIG.client.server.port);
            var response = ServerStatusResponse.parse(ServerListPing.fetch(address, hostname, TIMEOUT_MS));
            double duration = (System.nanoTime() - start) / 1e9;
            return new Status(true, response, duration, System.currentTimeMillis());
        } catch (Exception e) {
            return new Status(false, ServerStatusResponse.parse(null), 0, System.currentTimeMillis());
        }
    }
}
