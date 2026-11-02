package lucheart.zenithexporter.metrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * The parts of a server list ping that are metrics. Parsed here instead of with zenith's MCPing, which is made for
 * 2b2t's response and throws on one without a favicon or with a plain text description. Servers can also hide
 * their player count, so every field may be missing.
 * <p>
 * 2b2t counts everyone connected as online, queue included, and spells out the real numbers as fake players in
 * the sample: "In-game: 566", "Queue: 749", "Priority queue: 1". Its limit is a placeholder 1, and its protocol -1.
 *
 * @param online       players actually on the server
 * @param reported     what the server gives as its online count, which on 2b2t includes the queue
 * @param queue        length of the regular queue, if the server says
 * @param priorityQueue length of the priority queue, if the server says
 */
public record ServerStatusResponse(
    OptionalInt online,
    OptionalInt reported,
    OptionalInt max,
    OptionalInt protocol,
    OptionalInt queue,
    OptionalInt priorityQueue
) {
    private static final OptionalInt NONE = OptionalInt.empty();
    private static final ServerStatusResponse EMPTY = new ServerStatusResponse(NONE, NONE, NONE, NONE, NONE, NONE);
    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern IN_GAME = Pattern.compile("^in-game:\\s*(\\d+)$");
    private static final Pattern QUEUE = Pattern.compile("^queue:\\s*(\\d+)$");
    private static final Pattern PRIORITY_QUEUE = Pattern.compile("^priority queue:\\s*(\\d+)$");

    public static ServerStatusResponse parse(@Nullable String json) {
        if (json == null) return EMPTY;
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            var players = root.getAsJsonObject("players");
            var version = root.getAsJsonObject("version");
            var reported = intField(players, "online");
            var max = intField(players, "max");
            // a limit below the count isn't a limit
            if (max.isPresent() && reported.isPresent() && max.getAsInt() < reported.getAsInt()) max = NONE;
            var protocol = intField(version, "protocol");
            if (protocol.isPresent() && protocol.getAsInt() < 0) protocol = NONE;

            var inGame = NONE;
            var queue = NONE;
            var priorityQueue = NONE;
            if (players != null && players.has("sample") && players.get("sample").isJsonArray()) {
                for (JsonElement entry : players.getAsJsonArray("sample")) {
                    if (!entry.isJsonObject() || !entry.getAsJsonObject().has("name")) continue;
                    var name = FORMATTING.matcher(entry.getAsJsonObject().get("name").getAsString()).replaceAll("").trim().toLowerCase(Locale.ROOT);
                    inGame = firstOf(inGame, IN_GAME, name);
                    queue = firstOf(queue, QUEUE, name);
                    priorityQueue = firstOf(priorityQueue, PRIORITY_QUEUE, name);
                }
            }
            return new ServerStatusResponse(inGame.isPresent() ? inGame : reported, reported, max, protocol, queue, priorityQueue);
        } catch (RuntimeException e) {
            return EMPTY;
        }
    }

    private static OptionalInt intField(@Nullable JsonObject object, String field) {
        return object != null && object.has(field) ? OptionalInt.of(object.get(field).getAsInt()) : NONE;
    }

    private static OptionalInt firstOf(OptionalInt current, Pattern pattern, String text) {
        if (current.isPresent()) return current;
        var matcher = pattern.matcher(text);
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : NONE;
    }
}
