package lucheart.zenithchatbot.module;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The last few chat lines of one conversation (public chat, or the DMs with one player), so a reply can refer to
 * what was said before, like who insulted the bot a minute ago.
 */
public final class ChatHistory {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public record Line(Instant time, String sender, String message, boolean self) {}

    private final Deque<Line> lines = new ArrayDeque<>();
    private final Clock clock;

    public ChatHistory(Clock clock) {
        this.clock = clock;
    }

    public synchronized void add(String sender, String message, boolean self) {
        lines.addLast(new Line(clock.instant(), sender, message, self));
        // hard cap so a busy chat can't grow it without bound, the real limits are applied when reading
        while (lines.size() > 500) lines.removeFirst();
    }

    /** At most maxLines lines from the last maxAge, oldest first. */
    public synchronized List<Line> recent(int maxLines, Duration maxAge) {
        Instant oldest = clock.instant().minus(maxAge);
        lines.removeIf(line -> line.time().isBefore(oldest));
        return lines.stream().skip(Math.max(0, lines.size() - Math.max(0, maxLines))).toList();
    }

    public synchronized void clear() {
        lines.clear();
    }

    /** One line per message, {@code [14:03] <Steve> hi}, with the bot's own lines marked. */
    public static String format(List<Line> lines) {
        var out = new StringBuilder();
        for (Line line : lines) {
            out.append('[').append(TIME.format(line.time())).append("] <").append(line.sender());
            if (line.self()) out.append(" (you)");
            out.append("> ").append(line.message()).append('\n');
        }
        return out.toString();
    }
}
