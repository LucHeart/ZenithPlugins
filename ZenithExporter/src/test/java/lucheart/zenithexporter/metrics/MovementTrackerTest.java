package lucheart.zenithexporter.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementTrackerTest {
    @Test
    void firstPositionIsNotMovement() {
        assertEquals(0, new MovementTracker().update(1000, 64, 1000));
    }

    @Test
    void addsNormalMovement() {
        var tracker = new MovementTracker();
        tracker.update(0, 64, 0);
        assertEquals(5, tracker.update(3, 64, 4), 1e-9);
    }

    @Test
    void teleportDoesNotCountButMovementAfterItDoes() {
        var tracker = new MovementTracker();
        tracker.update(0, 64, 0);
        assertEquals(0, tracker.update(5000, 64, 5000));
        assertEquals(1, tracker.update(5001, 64, 5000), 1e-9);
    }

    @Test
    void resetForgetsThePosition() {
        var tracker = new MovementTracker();
        tracker.update(0, 64, 0);
        tracker.reset();
        assertEquals(0, tracker.update(3, 64, 4));
    }
}
