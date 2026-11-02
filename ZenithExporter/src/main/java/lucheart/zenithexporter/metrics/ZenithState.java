package lucheart.zenithexporter.metrics;

import com.zenith.Proxy;

import java.util.List;

final class ZenithState {
    static final String DISCONNECTED = "disconnected";
    static final String CONNECTING = "connecting";
    static final String QUEUE = "queue";
    static final String ONLINE = "online";
    static final List<String> ALL = List.of(DISCONNECTED, CONNECTING, QUEUE, ONLINE);

    private ZenithState() {}

    /** Past the queue and actually playing. Most game state is only meaningful here, otherwise it's stale or defaults. */
    static boolean inGame() {
        var proxy = Proxy.getInstance();
        var client = proxy.getClient();
        return client != null && client.isConnected() && client.isOnline() && !proxy.isInQueue();
    }

    static String current() {
        var proxy = Proxy.getInstance();
        if (!proxy.isConnected()) return proxy.getLoggingIn().get() ? CONNECTING : DISCONNECTED;
        if (proxy.isInQueue()) return QUEUE;
        return inGame() ? ONLINE : CONNECTING;
    }
}
