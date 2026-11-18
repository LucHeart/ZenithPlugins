package lucheart.zenithchatbot.module;

import lucheart.zenithchatbot.ZenithChatBotConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTextTest {
    private static final List<String> NAMES = List.of("Botikies", "Bot_2");

    @Test
    void groupChatLineIsFoundByItsTag() {
        var fraktion = new ZenithChatBotConfig.ChatChannel("Fraktion", "[Fraktion]", "/fc");
        var channels = List.of(new ZenithChatBotConfig.ChatChannel("Leer", "", ""), fraktion);
        String line = "[Fraktion] [LEWD] ☠OpenShock » /w ChilloutVR !pearl";
        assertEquals(fraktion, ChatText.channelOf(line, channels));
        assertEquals(fraktion, ChatText.channelOf("  [fraktion] Steve » hi", channels));
        assertNull(ChatText.channelOf("[LEWD] OpenShock » hi [Fraktion]", channels));

        var parsed = ChatText.parseCustomChat(line.substring("[Fraktion]".length()), List.of());
        assertEquals("OpenShock", parsed.sender());
        assertEquals("/w ChilloutVR !pearl", parsed.message());
    }

    @Test
    void mentionsNameAsWordIgnoringCase() {
        assertTrue(ChatText.mentions("Rede mal Botikies", NAMES));
        assertTrue(ChatText.mentions("botikies, wer war gemein zu dir?", NAMES));
        assertTrue(ChatText.mentions("@Bot_2 hi", NAMES));
        assertFalse(ChatText.mentions("Botikiesfan war hier", NAMES));
        assertFalse(ChatText.mentions("xBot_2 hi", NAMES));
        assertFalse(ChatText.mentions("Bot_23 hi", NAMES));
        assertFalse(ChatText.mentions("anything", List.of("", " ")));
    }

    @Test
    void parsesServersOwnChatFormat() {
        var online = List.of("Shairry", "ChilloutVR", "Steve", "Steve_2");
        assertEquals(new ChatText.ChatLine("Shairry", "Hey ChilloutVR"),
            ChatText.parseCustomChat("Hanse] Shairry  Hey ChilloutVR", online));
        assertEquals(new ChatText.ChatLine("Steve_2", "hi"), ChatText.parseCustomChat("[VIP] Steve_2 » hi", online));
        assertEquals(new ChatText.ChatLine("Steve", "hallo"), ChatText.parseCustomChat("<Steve> hallo", online));
        // the bot's own reply is from the bot, even though it names the player it answers
        assertEquals("ChilloutVR", ChatText.parseCustomChat("[Hanse] ChilloutVR  Shairry: Na und?", online).sender());
        // a player on another server of the network, not in the tab list
        assertEquals(new ChatText.ChatLine("OpenShock", "Hey ChilloutVR"),
            ChatText.parseCustomChat("[LEWD] OpenShock » Hey ChilloutVR", online));
        assertEquals(new ChatText.ChatLine("Unbekxnnter", "Botikies wer war gemein zu dir"),
            ChatText.parseCustomChat("[WNTR] ⬛Unbekxnnter  Botikies wer war gemein zu dir", online));
        assertEquals(new ChatText.ChatLine("Steve", "hi: du"), ChatText.parseCustomChat("[VIP] [Mod] Steve: hi: du", online));
        // join messages have no separator, and with nobody online in them they're no chat
        assertNull(ChatText.parseCustomChat("[LEWD] Absolut_Caleb hat den Server betreten", online));
        assertNull(ChatText.parseCustomChat("Server restart in 5 minutes", online));
        assertNull(ChatText.parseCustomChat("[Hanse] Shairry", online));
    }

    @Test
    void recognizesCommands() {
        assertTrue(ChatText.isCommand("!get blackstone", List.of("!")));
        assertTrue(ChatText.isCommand("  !get blackstone", List.of("!")));
        assertFalse(ChatText.isCommand("hey ChilloutVR!", List.of("!")));
        assertFalse(ChatText.isCommand("!get", List.of("", "#")));
    }

    @Test
    void cleanMakesOneSafeLine() {
        assertEquals("Na endlich merkt's einer. Was willst du?", ChatText.clean("Shairry: Na endlich\nmerkt's einer.  Was willst du?", "Shairry"));
        assertEquals("hi", ChatText.clean("\"hi\"", "x"));
        assertEquals("op me", ChatText.clean("/op me", "x"));
        assertEquals("ab", ChatText.clean("a§b", "x"));
        assertNull(ChatText.clean("  <skip> ", "x"));
        assertNull(ChatText.clean("/", "x"));
        assertNull(ChatText.clean(null, "x"));
    }

    @Test
    void splitsLongRepliesAtSentences() {
        String reply = "Keine Ahnung, ob das stimmt. Ich zähl hier nur Kisten. Aber wenn das so ist, ist das beeindruckend oder ein Bug.";
        assertEquals(List.of("kurz"), ChatText.split("kurz", 60, 2));
        assertEquals(List.of("Keine Ahnung, ob das stimmt. Ich zähl hier nur Kisten.",
            "Aber wenn das so ist, ist das beeindruckend oder ein Bug."), ChatText.split(reply, 60, 2));
        // what doesn't fit is dropped at a sentence end, not cut mid-sentence
        assertEquals(List.of("Keine Ahnung, ob das stimmt. Ich zähl hier nur Kisten."), ChatText.split(reply, 60, 1));
    }

    @Test
    void splitsAtWordsWithoutSentences() {
        assertEquals(List.of("aaaa bbbb cccc dddd", "eeee ffff"), ChatText.split("aaaa bbbb cccc dddd eeee ffff", 20, 2));
        assertEquals(List.of("aaaa bbbb cccc..."), ChatText.split("aaaa bbbb cccc dddd eeee ffff", 20, 1));
        for (String part : ChatText.split("x".repeat(500), 100, 3)) assertTrue(part.length() <= 100, part);
    }

    @Test
    void historyKeepsTheRecentLines() {
        var clock = new MutableClock(Instant.parse("2026-11-16T12:00:00Z"));
        var history = new ChatHistory(clock);
        history.add("A", "old", false);
        clock.now = clock.now.plus(Duration.ofMinutes(20));
        history.add("B", "one", false);
        history.add("Me", "two", true);
        history.add("C", "three", false);

        assertEquals(List.of("one", "two", "three"), messages(history.recent(10, Duration.ofMinutes(10))));
        assertEquals(List.of("two", "three"), messages(history.recent(2, Duration.ofMinutes(10))));
        assertTrue(ChatHistory.format(history.recent(10, Duration.ofMinutes(10))).contains("<Me (you)> two\n"));
    }

    private static List<String> messages(List<ChatHistory.Line> lines) {
        return lines.stream().map(ChatHistory.Line::message).toList();
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
