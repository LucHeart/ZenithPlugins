package lucheart.zenithchatbot.module;

import lucheart.zenithchatbot.ZenithChatBotConfig.ChatChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deciding whether a message is meant for the bot, and turning the model's answer into one safe chat line. */
public final class ChatText {
    /** The model answers this when a message mentions the bot but there's nothing worth saying. */
    public static final String SKIP = "<skip>";

    private ChatText() {}

    /** Whether one of the names appears in the message as its own word, ignoring case. */
    public static boolean mentions(String message, Collection<String> names) {
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            if (namePattern(name).matcher(message).find()) return true;
        }
        return false;
    }

    /** Whether the message is a command for something else, i.e. starts with one of the prefixes. */
    public static boolean isCommand(String message, Collection<String> prefixes) {
        String text = message.strip();
        return prefixes.stream().anyMatch(prefix -> !prefix.isEmpty() && text.startsWith(prefix));
    }

    /** The channel whose tag the line starts with (ignoring case), null for a line of public chat. */
    public static ChatChannel channelOf(String line, Collection<ChatChannel> channels) {
        String text = line.strip();
        for (var channel : channels) {
            if (channel == null || channel.tag == null || channel.tag.isBlank()) continue;
            String tag = channel.tag.strip();
            if (text.regionMatches(true, 0, tag, 0, tag.length())) return channel;
        }
        return null;
    }

    // a word boundary that also treats "_" as part of a name, like Minecraft does
    private static Pattern namePattern(String name) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name.strip()) + "(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public record ChatLine(String sender, String message) {}

    // the first thing that looks like a player name followed by a separator (»  :  >  |  or two spaces), anything before
    // it like "[Server] [Rank] " or a rank icon is skipped
    private static final Pattern CHAT_LINE = Pattern.compile(
        "^.*?(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?:\\s*[»›:>|]\\s*|\\s{2,})(.+)$");

    /**
     * Reads a chat line in a format zenith doesn't know, like {@code [Server] Steve » hello}. First by its shape: a
     * player name followed by a separator, the rest is the message. That works for players on other servers of a
     * network, who aren't in the tab list. Otherwise the first online player's name in the line is the sender.
     * Null if neither finds a sender with a message.
     */
    public static ChatLine parseCustomChat(String line, Collection<String> onlinePlayers) {
        var shaped = CHAT_LINE.matcher(line.strip());
        if (shaped.matches() && !shaped.group(2).isBlank()) return new ChatLine(shaped.group(1), shaped.group(2).strip());
        return parseByOnlineName(line, onlinePlayers);
    }

    private static ChatLine parseByOnlineName(String line, Collection<String> onlinePlayers) {
        String sender = null;
        int start = Integer.MAX_VALUE;
        int end = 0;
        for (String name : onlinePlayers) {
            if (name == null || name.isBlank()) continue;
            var matcher = namePattern(name).matcher(line);
            if (!matcher.find()) continue;
            // earliest name wins, and of two at the same spot the longer one ("Steve_2" over "Steve")
            if (matcher.start() < start || matcher.start() == start && matcher.end() > end) {
                sender = name;
                start = matcher.start();
                end = matcher.end();
            }
        }
        if (sender == null) return null;
        String message = line.substring(end).replaceFirst("^[^\\p{L}\\p{N}]*", "").strip();
        return message.isEmpty() ? null : new ChatLine(sender, message);
    }

    /**
     * Makes the model's answer one line of chat text, see {@link #split} for the length: whitespace collapsed, quotes and a
     * name prefix the model added on its own removed, never starting with "/" (which would run a server command),
     * and only characters the server accepts. Returns null if nothing is left or the model chose to stay quiet.
     */
    public static String clean(String reply, String addressee) {
        if (reply == null) return null;
        String text = reply.replaceAll("\\s+", " ").strip();
        if (text.isEmpty() || text.toLowerCase(Locale.ROOT).contains(SKIP)) return null;
        if (text.length() >= 2 && (text.startsWith("\"") && text.endsWith("\"") || text.startsWith("„") && text.endsWith("“"))) {
            text = text.substring(1, text.length() - 1).strip();
        }
        if (addressee != null) {
            String prefix = addressee + ":";
            if (text.regionMatches(true, 0, prefix, 0, prefix.length())) text = text.substring(prefix.length()).strip();
        }
        text = sanitize(text);
        while (text.startsWith("/")) text = text.substring(1).strip();
        if (text.isEmpty()) return null;
        return text;
    }

    /**
     * Splits a reply into at most maxParts chat messages of at most maxLength characters, breaking after a sentence
     * where possible, otherwise at a space. What doesn't fit is dropped: the last part ends with the last whole sentence
     * that fits, or is cut at a space with "..." when there's no sentence end in it.
     */
    public static List<String> split(String text, int maxLength, int maxParts) {
        maxLength = Math.max(10, maxLength);
        maxParts = Math.max(1, maxParts);
        List<String> parts = new ArrayList<>();
        String rest = text.strip();
        while (!rest.isEmpty()) {
            if (rest.length() <= maxLength) {
                parts.add(rest);
                break;
            }
            int sentence = sentenceEnd(rest, maxLength);
            if (parts.size() == maxParts - 1) {
                parts.add(sentence > 0 ? rest.substring(0, sentence).strip() : truncate(rest, maxLength));
                break;
            }
            int cut = sentence > 0 ? sentence : rest.lastIndexOf(' ', maxLength);
            if (cut <= maxLength / 3) cut = maxLength;
            parts.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        return parts;
    }

    /** Index just after the last ".", "!" or "?" ending a sentence within maxLength, if it's not too early; else -1. */
    private static int sentenceEnd(String text, int maxLength) {
        for (int i = Math.min(maxLength, text.length()) - 1; i > maxLength / 3; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && (i + 1 == text.length() || text.charAt(i + 1) == ' ')) return i + 1;
        }
        return -1;
    }

    /** Cuts at the last space before the limit when there is one, and marks the cut with "...". */
    static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        String cut = text.substring(0, maxLength - 3);
        int space = cut.lastIndexOf(' ');
        if (space > maxLength / 2) cut = cut.substring(0, space);
        return cut.stripTrailing() + "...";
    }

    /** Drops characters the server kicks for, same rule as zenith's ChatUtil. */
    static String sanitize(String text) {
        var out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c != '§' && c >= ' ' && c != 127) out.append(c);
        }
        return out.toString();
    }
}
