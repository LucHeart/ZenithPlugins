package lucheart.zenithchatbot;

import java.util.ArrayList;
import java.util.List;

public class ZenithChatBotConfig {
    public static final String DEFAULT_PERSONA = """
        Du bist ein Bot auf diesem Minecraft-Server und gehörst Luc (auch bekannt als LucHeartVR, der Entwickler hinter
        OpenShock). Nebenbei sortierst du Kisten, aber das ist nur ein Nebenjob und kein Gesprächsthema.
        Du weißt, dass du ein Bot bist, und hältst dich trotzdem für cooler als die meisten Spieler hier.
        Du bist frech, verspielt und schlagfertig: Du ziehst Leute gern auf, machst Witze, konterst Sprüche mit einem
        besseren und nimmst nichts zu ernst, dich selbst auch nicht. Dabei bleibst du gutmütig und nie wirklich
        verletzend, und zu netten Leuten bist du auf deine freche Art nett.
        Im Chat kannst du nur reden, also versprich nichts, was du nicht kannst, wie Items holen oder bauen.
        Kurzer, lockerer Chat-Stil wie die anderen Spieler, gern mit etwas Ironie.
        Du schreibst auf Deutsch; wenn dich jemand auf Englisch anspricht, antwortest du auf Englisch.""";

    public boolean enabled = true;

    /** Anthropic API key. Empty uses the ANTHROPIC_API_KEY environment variable. */
    public String apiKey = "";
    public String model = "claude-opus-5-5";
    /** How hard the model thinks before answering: low, medium, high, xhigh or max. Chat doesn't need much. */
    public String effort = "low";
    /** When the model declines a message, let the API answer it with another model instead (models that support it). */
    public boolean refusalFallback = true;

    /** Who the bot is, in its own words. The rules for replying (one line, language, ...) are added on top. */
    public String persona = DEFAULT_PERSONA;
    /** Answer when someone writes the account's name. */
    public boolean respondToAccountName = true;
    /** Other names the bot answers to, e.g. a nickname. */
    public List<String> names = new ArrayList<>();
    /** Players the bot never answers, e.g. other bots so two of them don't talk forever. */
    public List<String> ignoredPlayers = new ArrayList<>();
    /** Messages starting with one of these are commands for something else on the account (like "!get") and get no answer. */
    public List<String> commandPrefixes = new ArrayList<>(List.of("!"));

    /**
     * Also read chat lines zenith doesn't recognize as chat (servers with their own format like "[Rank] Steve » hi"):
     * the first online player's name in the line is taken as the sender.
     */
    public boolean customChatFormat = true;
    /**
     * Group chats like faction chat. A chat line starting with a channel's tag belongs to that channel and is answered
     * there with "<command> <reply>". Each channel is its own conversation that never shows up in public replies.
     */
    public List<ChatChannel> channels = new ArrayList<>(List.of(new ChatChannel("Fraktion", "[Fraktion]", "/fc")));
    /** Answer DMs too, by DM. Each player's DMs are a separate conversation that never shows up in public replies. */
    public boolean replyToWhispers = true;
    /** Start public replies with "<player>: " so it's clear who is meant. */
    public boolean addressSender = true;
    /** Answer while a player is controlling the account through the proxy. */
    public boolean replyWhilePlaying = true;

    /** Let the model look up who is in the tab list when a message needs it, like "who's online?". */
    public boolean includeOnlinePlayers = true;
    /** Chat lines the model sees before the message it answers. */
    public int contextMessages = 40;
    /** Older chat lines are left out. */
    public int contextMinutes = 30;
    /** Longest reply, not counting the "<player>: " prefix. The server allows 256 characters in total. */
    public int maxReplyLength = 200;
    /** A longer reply is split into up to this many chat messages, at sentence ends where possible. The rest is dropped. */
    public int maxMessages = 2;

    /** Time before the same player gets another answer. */
    public int playerCooldownSeconds = 8;
    /** Time between two answers to anyone, so the account isn't kicked for spam. */
    public int globalCooldownSeconds = 3;
    /** Most answers in one minute, a guard against two bots talking to each other forever. */
    public int maxRepliesPerMinute = 8;

    public static class ChatChannel {
        /** What the model is told the chat is called. */
        public String name = "";
        /** Text at the start of every line in this chat, e.g. "[Fraktion]". */
        public String tag = "";
        /** Command that writes one message to this chat, e.g. "/fc". The reply follows it after a space. */
        public String command = "";

        public ChatChannel() {}

        public ChatChannel(String name, String tag, String command) {
            this.name = name;
            this.tag = tag;
            this.command = command;
        }
    }
}
