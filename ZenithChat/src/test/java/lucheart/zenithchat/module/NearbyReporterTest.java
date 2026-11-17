package lucheart.zenithchat.module;

import lucheart.zenithchat.module.NearbyPlayers.Nearby;
import lucheart.zenithchat.module.NearbyPlayers.Snapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearbyReporterTest {
    private static Snapshot seeing(Nearby... players) {
        return new Snapshot(true, "overworld", List.of(players));
    }

    private static Nearby steve(double x, boolean invisible) {
        return new Nearby("a", "Steve", x, 64, 0, invisible);
    }

    @Test
    void firstReportCounts() {
        assertTrue(NearbyReporter.changed(null, seeing()));
    }

    @Test
    void smallMovesDoNot() {
        assertFalse(NearbyReporter.changed(seeing(steve(0, false)), seeing(steve(0.4, false))));
        assertTrue(NearbyReporter.changed(seeing(steve(0, false)), seeing(steve(1.5, false))));
    }

    @Test
    void comingGoingAndPotionsDo() {
        assertTrue(NearbyReporter.changed(seeing(), seeing(steve(0, false))));
        assertTrue(NearbyReporter.changed(seeing(steve(0, false)), seeing()));
        assertTrue(NearbyReporter.changed(seeing(steve(0, false)), seeing(steve(0, true))));
    }

    @Test
    void leavingTheGameOrDimensionDoes() {
        assertTrue(NearbyReporter.changed(seeing(), Snapshot.OUT_OF_GAME));
        assertTrue(NearbyReporter.changed(seeing(), new Snapshot(true, "the_nether", List.of())));
    }
}
