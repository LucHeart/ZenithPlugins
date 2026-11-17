package lucheart.zenithchat.module;

import lucheart.zenithchat.ZenithChatPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatLogTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void logger() {
        ZenithChatPlugin.LOG = ComponentLogger.logger("test");
    }

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void appendsToOneFilePerDay() throws Exception {
        var clock = new MutableClock(at("2026-11-11T23:59:58Z"));
        var log = new ChatLog(() -> dir.resolve("chatlogs"), clock);
        log.write("CHAT", "<Steve> hello");
        log.write("DM IN", "Alex whispers: hi\nsecond line");
        clock.clock = at("2026-11-12T00:00:01Z");
        log.write("SYSTEM", "  Server restarting  ");
        log.close();

        assertEquals(List.of("[23:59:58] [CHAT] <Steve> hello", "[23:59:58] [DM IN] Alex whispers: hi", "\tsecond line"),
            Files.readAllLines(dir.resolve("chatlogs/2026-11-11.log")));
        assertEquals(List.of("[00:00:01] [SYSTEM] Server restarting"),
            Files.readAllLines(dir.resolve("chatlogs/2026-11-12.log")));
    }

    @Test
    void appendsAfterReopening() throws Exception {
        var clock = at("2026-11-11T10:00:00Z");
        var first = new ChatLog(() -> dir, clock);
        first.write("CHAT", "one");
        first.close();
        var second = new ChatLog(() -> dir, clock);
        second.write("CHAT", "two");
        second.close();

        assertEquals(List.of("[10:00:00] [CHAT] one", "[10:00:00] [CHAT] two"), Files.readAllLines(dir.resolve("2026-11-11.log")));
    }

    private static final class MutableClock extends Clock {
        Clock clock;

        MutableClock(Clock clock) {
            this.clock = clock;
        }

        @Override
        public ZoneId getZone() {
            return clock.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return clock.withZone(zone);
        }

        @Override
        public Instant instant() {
            return clock.instant();
        }
    }
}
