package lucheart.zenithexporter.metrics;

/**
 * When the proxy joins, the server sends state it already had as if it just changed: everyone online as a new
 * tab list entry, and the account's health compared to a default of 20. Counters watching those wait until that settled.
 */
public final class SessionSettleGate {
    static final long SETTLE_MS = 15_000;

    private volatile long settledAt = 0;

    public void sessionStarted(long nowMs) {
        settledAt = nowMs + SETTLE_MS;
    }

    public boolean counts(long nowMs) {
        return nowMs >= settledAt;
    }
}
