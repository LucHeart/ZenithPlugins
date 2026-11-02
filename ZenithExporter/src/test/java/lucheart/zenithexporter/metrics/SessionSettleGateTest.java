package lucheart.zenithexporter.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionSettleGateTest {
    @Test
    void countsBeforeAnySession() {
        assertTrue(new SessionSettleGate().counts(1000));
    }

    @Test
    void holdsOffUntilTabListSettled() {
        var gate = new SessionSettleGate();
        gate.sessionStarted(1000);
        assertFalse(gate.counts(1000));
        assertFalse(gate.counts(1000 + SessionSettleGate.SETTLE_MS - 1));
        assertTrue(gate.counts(1000 + SessionSettleGate.SETTLE_MS));
    }

    @Test
    void laterStartExtendsTheWait() {
        var gate = new SessionSettleGate();
        gate.sessionStarted(1000);
        gate.sessionStarted(5000);
        assertFalse(gate.counts(1000 + SessionSettleGate.SETTLE_MS));
        assertTrue(gate.counts(5000 + SessionSettleGate.SETTLE_MS));
    }
}
