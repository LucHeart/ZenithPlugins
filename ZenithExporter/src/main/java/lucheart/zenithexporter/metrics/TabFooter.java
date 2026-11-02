package lucheart.zenithexporter.metrics;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Stats some servers print in the tab list footer, 2b2t's is "19.51 tps — 679 players online — 127 ping".
 * Each value is matched on its own so a server showing only some of them still gets those.
 */
public record TabFooter(OptionalInt players, OptionalDouble tps, OptionalInt pingMs) {
    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final String NUMBER = "(\\d[\\d,]*)";
    // servers format these with their own locale, so 19,51 tps is as likely as 19.51 tps
    private static final String DECIMAL = "(\\d+(?:[.,]\\d+)?)";
    // spaces only: footers are often one stat per line, a number must not pair up with the next line's word
    private static final String SPACE = "[ \\t]";
    private static final String SEPARATOR = SPACE + "*[:=-]" + SPACE + "*";
    private static final Pattern PLAYERS = Pattern.compile(
        "\\b" + NUMBER + SPACE + "+players?" + SPACE + "+online\\b|\\bonline(?:" + SPACE + "+players)?" + SEPARATOR + NUMBER);
    private static final Pattern TPS = Pattern.compile(
        "\\b" + DECIMAL + SPACE + "*tps\\b|\\btps" + SEPARATOR + DECIMAL);
    private static final Pattern PING = Pattern.compile(
        "\\b" + NUMBER + SPACE + "*(?:ms" + SPACE + "+)?ping\\b|\\bping" + SEPARATOR + NUMBER);

    public static TabFooter parse(String footer) {
        var text = FORMATTING.matcher(footer).replaceAll("").toLowerCase(Locale.ROOT);
        var players = firstGroup(PLAYERS, text);
        var tps = firstGroup(TPS, text);
        var ping = firstGroup(PING, text);
        return new TabFooter(
            players == null ? OptionalInt.empty() : parseInt(players),
            tps == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(tps.replace(',', '.'))),
            ping == null ? OptionalInt.empty() : parseInt(ping));
    }

    private static String firstGroup(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        if (!matcher.find()) return null;
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static OptionalInt parseInt(String number) {
        try {
            return OptionalInt.of(Integer.parseInt(number.replace(",", "")));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
