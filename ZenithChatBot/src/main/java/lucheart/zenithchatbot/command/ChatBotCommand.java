package lucheart.zenithchatbot.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import lucheart.zenithchatbot.ZenithChatBotConfig;
import lucheart.zenithchatbot.module.ChatBotModule;
import lucheart.zenithchatbot.module.Claude;
import lucheart.zenithchatbot.module.Claude.ClaudeException;

import java.util.List;
import java.util.Locale;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.discord.DiscordBot.escape;
import static lucheart.zenithchatbot.ZenithChatBotPlugin.PLUGIN_CONFIG;

public class ChatBotCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("chatBot")
            .category(CommandCategory.MODULE)
            .description("""
                Answers players with Claude when they write the account's name (or one of the extra names) in chat.
                The recent chat is sent along, so the bot knows what was said before. DMs are answered by DM.

                apiKey: an Anthropic API key, or leave it empty to use the ANTHROPIC_API_KEY environment variable.
                persona: who the bot is, e.g. "You're grumpy and only talk about fishing".
                channel: a group chat like faction chat. Lines starting with the tag (e.g. "[Fraktion]") are answered
                with the command (e.g. /fc) instead of in public chat.
                test: shows what the bot would answer to a message, without sending anything.
                """)
            .usageLines(
                "on/off",
                "apiKey <key>",
                "model <model>",
                "effort <low/medium/high/xhigh/max>",
                "persona <text>",
                "persona reset",
                "names add/del <name>",
                "accountName on/off",
                "ignore add/del <player>",
                "prefixes add/del <prefix>",
                "players on/off",
                "customFormat on/off",
                "whispers on/off",
                "channel add <name> \"<tag>\" <command>",
                "channel del <name>",
                "address on/off",
                "whilePlaying on/off",
                "cooldown player <seconds>",
                "cooldown global <seconds>",
                "maxPerMinute <count>",
                "maxLength <characters>",
                "maxMessages <count>",
                "context <messages> <minutes>",
                "fallback on/off",
                "test <message>",
                "clear"
            )
            .aliases("cb")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("chatBot")
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                module().syncEnabledFromConfig();
                c.getSource().getEmbed().title("ChatBot " + toggleStrCaps(PLUGIN_CONFIG.enabled));
            }))
            .then(literal("apiKey")
                .executes(c -> {
                    PLUGIN_CONFIG.apiKey = "";
                    c.getSource().getEmbed().title("API Key Cleared").description("Using the ANTHROPIC_API_KEY environment variable");
                })
                .then(argument("key", string()).executes(c -> {
                    PLUGIN_CONFIG.apiKey = getString(c, "key").strip();
                    // never echo the key back, the embed may end up in a Discord channel
                    c.getSource().getEmbed().title("API Key Set");
                })))
            .then(literal("model").then(argument("model", string()).executes(c -> {
                PLUGIN_CONFIG.model = getString(c, "model").strip();
                c.getSource().getEmbed().title("Model Set");
            })))
            .then(literal("effort").then(argument("effort", string()).executes(c -> {
                var effort = getString(c, "effort").toLowerCase(Locale.ROOT);
                if (!Claude.EFFORTS.contains(effort)) {
                    c.getSource().getEmbed().title("Invalid Effort").description("One of low, medium, high, xhigh, max");
                    return ERROR;
                }
                PLUGIN_CONFIG.effort = effort;
                c.getSource().getEmbed().title("Effort Set");
                return OK;
            })))
            .then(literal("persona")
                .then(literal("reset").executes(c -> {
                    PLUGIN_CONFIG.persona = ZenithChatBotConfig.DEFAULT_PERSONA;
                    c.getSource().getEmbed().title("Persona Reset").description(escape(PLUGIN_CONFIG.persona));
                }))
                .then(argument("text", greedyString()).executes(c -> {
                    PLUGIN_CONFIG.persona = getString(c, "text").strip();
                    c.getSource().getEmbed().title("Persona Set").description(escape(PLUGIN_CONFIG.persona));
                })))
            .then(literal("names")
                .then(literal("add").then(argument("name", string()).executes(c -> {
                    var name = getString(c, "name").strip();
                    if (PLUGIN_CONFIG.names.stream().noneMatch(name::equalsIgnoreCase)) PLUGIN_CONFIG.names.add(name);
                    c.getSource().getEmbed().title("Name Added").description(names());
                })))
                .then(literal("del").then(argument("name", string()).executes(c -> {
                    var name = getString(c, "name");
                    PLUGIN_CONFIG.names.removeIf(name::equalsIgnoreCase);
                    c.getSource().getEmbed().title("Name Removed").description(names());
                }))))
            .then(literal("accountName").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.respondToAccountName = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Answer To Account Name " + toggleStrCaps(PLUGIN_CONFIG.respondToAccountName));
            })))
            .then(literal("ignore")
                .then(literal("add").then(argument("player", string()).executes(c -> {
                    var player = getString(c, "player").strip();
                    if (PLUGIN_CONFIG.ignoredPlayers.stream().noneMatch(player::equalsIgnoreCase)) PLUGIN_CONFIG.ignoredPlayers.add(player);
                    c.getSource().getEmbed().title("Player Ignored").description(list(PLUGIN_CONFIG.ignoredPlayers));
                })))
                .then(literal("del").then(argument("player", string()).executes(c -> {
                    var player = getString(c, "player");
                    PLUGIN_CONFIG.ignoredPlayers.removeIf(player::equalsIgnoreCase);
                    c.getSource().getEmbed().title("Player Unignored").description(list(PLUGIN_CONFIG.ignoredPlayers));
                }))))
            .then(literal("prefixes")
                .then(literal("add").then(argument("prefix", string()).executes(c -> {
                    var prefix = getString(c, "prefix").strip();
                    if (!prefix.isEmpty() && !PLUGIN_CONFIG.commandPrefixes.contains(prefix)) PLUGIN_CONFIG.commandPrefixes.add(prefix);
                    c.getSource().getEmbed().title("Command Prefix Added").description(list(PLUGIN_CONFIG.commandPrefixes));
                })))
                .then(literal("del").then(argument("prefix", string()).executes(c -> {
                    PLUGIN_CONFIG.commandPrefixes.remove(getString(c, "prefix").strip());
                    c.getSource().getEmbed().title("Command Prefix Removed").description(list(PLUGIN_CONFIG.commandPrefixes));
                }))))
            .then(literal("players").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.includeOnlinePlayers = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Online Players In Context " + toggleStrCaps(PLUGIN_CONFIG.includeOnlinePlayers));
            })))
            .then(literal("customFormat").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.customChatFormat = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Custom Chat Format " + toggleStrCaps(PLUGIN_CONFIG.customChatFormat));
            })))
            .then(literal("whispers").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.replyToWhispers = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Answer DMs " + toggleStrCaps(PLUGIN_CONFIG.replyToWhispers));
            })))
            .then(literal("channel")
                .then(literal("add").then(argument("name", string()).then(argument("tag", string()).then(argument("command", greedyString()).executes(c -> {
                    var name = getString(c, "name").strip();
                    PLUGIN_CONFIG.channels.removeIf(ch -> ch.name.equalsIgnoreCase(name));
                    PLUGIN_CONFIG.channels.add(new ZenithChatBotConfig.ChatChannel(name, getString(c, "tag").strip(), getString(c, "command").strip()));
                    c.getSource().getEmbed().title("Channel Set").description(channels());
                })))))
                .then(literal("del").then(argument("name", string()).executes(c -> {
                    var name = getString(c, "name");
                    PLUGIN_CONFIG.channels.removeIf(ch -> ch.name.equalsIgnoreCase(name));
                    c.getSource().getEmbed().title("Channel Removed").description(channels());
                }))))
            .then(literal("address").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.addressSender = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Address Sender " + toggleStrCaps(PLUGIN_CONFIG.addressSender));
            })))
            .then(literal("whilePlaying").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.replyWhilePlaying = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Answer While Playing " + toggleStrCaps(PLUGIN_CONFIG.replyWhilePlaying));
            })))
            .then(literal("cooldown")
                .then(literal("player").then(argument("seconds", integer(0, 3600)).executes(c -> {
                    PLUGIN_CONFIG.playerCooldownSeconds = getInteger(c, "seconds");
                    c.getSource().getEmbed().title("Player Cooldown Set");
                })))
                .then(literal("global").then(argument("seconds", integer(0, 3600)).executes(c -> {
                    PLUGIN_CONFIG.globalCooldownSeconds = getInteger(c, "seconds");
                    c.getSource().getEmbed().title("Global Cooldown Set");
                }))))
            .then(literal("maxPerMinute").then(argument("count", integer(1, 60)).executes(c -> {
                PLUGIN_CONFIG.maxRepliesPerMinute = getInteger(c, "count");
                c.getSource().getEmbed().title("Max Answers Per Minute Set");
            })))
            .then(literal("maxLength").then(argument("characters", integer(20, 250)).executes(c -> {
                PLUGIN_CONFIG.maxReplyLength = getInteger(c, "characters");
                c.getSource().getEmbed().title("Max Reply Length Set");
            })))
            .then(literal("maxMessages").then(argument("count", integer(1, 5)).executes(c -> {
                PLUGIN_CONFIG.maxMessages = getInteger(c, "count");
                c.getSource().getEmbed().title("Max Messages Per Reply Set");
            })))
            .then(literal("context").then(argument("messages", integer(0, 200)).then(argument("minutes", integer(1, 1440)).executes(c -> {
                PLUGIN_CONFIG.contextMessages = getInteger(c, "messages");
                PLUGIN_CONFIG.contextMinutes = getInteger(c, "minutes");
                c.getSource().getEmbed().title("Context Set");
            }))))
            .then(literal("fallback").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.refusalFallback = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Refusal Fallback " + toggleStrCaps(PLUGIN_CONFIG.refusalFallback));
            })))
            .then(literal("test").then(argument("message", greedyString()).executes(c -> {
                var message = getString(c, "message");
                var embed = c.getSource().getEmbed();
                try {
                    var parts = module().testReply(message);
                    embed.title("Test Reply").description(parts.isEmpty() ? "*(would not answer)*" : escape(String.join("\n", parts)));
                    return OK;
                } catch (ClaudeException e) {
                    embed.title("Test Failed").description(escape(e.getMessage()));
                    return ERROR;
                }
            })))
            .then(literal("clear").executes(c -> {
                module().clearHistory();
                c.getSource().getEmbed().title("Chat History Cleared");
            }));
    }

    private static ChatBotModule module() {
        return MODULE.get(ChatBotModule.class);
    }

    private static String names() {
        return list(module().botNames());
    }

    private static String channels() {
        return list(PLUGIN_CONFIG.channels.stream().map(ch -> ch.name + ": " + ch.tag + " -> " + ch.command).toList());
    }

    private static String list(List<String> items) {
        return items.isEmpty() ? "none" : escape(String.join(", ", items));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var module = module();
        String persona = PLUGIN_CONFIG.persona.strip();
        if (persona.length() > 300) persona = persona.substring(0, 300) + "...";
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("API Key", !PLUGIN_CONFIG.apiKey.isBlank() ? "set" : Claude.hasApiKey() ? "from environment" : "missing")
            .addField("Model", escape(PLUGIN_CONFIG.model) + " (effort " + PLUGIN_CONFIG.effort + ")")
            .addField("Answers To", names())
            .addField("Ignored", list(PLUGIN_CONFIG.ignoredPlayers))
            .addField("Ignored Prefixes", list(PLUGIN_CONFIG.commandPrefixes))
            .addField("Custom Chat Format", toggleStr(PLUGIN_CONFIG.customChatFormat))
            .addField("DMs", toggleStr(PLUGIN_CONFIG.replyToWhispers))
            .addField("Channels", channels())
            .addField("Address Sender", toggleStr(PLUGIN_CONFIG.addressSender))
            .addField("While Playing", toggleStr(PLUGIN_CONFIG.replyWhilePlaying))
            .addField("Cooldowns", PLUGIN_CONFIG.playerCooldownSeconds + "s player, " + PLUGIN_CONFIG.globalCooldownSeconds
                + "s global, max " + PLUGIN_CONFIG.maxRepliesPerMinute + "/min")
            .addField("Context", PLUGIN_CONFIG.contextMessages + " messages / " + PLUGIN_CONFIG.contextMinutes + " min"
                + (PLUGIN_CONFIG.includeOnlinePlayers ? ", can look up online players" : ""))
            .addField("Max Length", PLUGIN_CONFIG.maxReplyLength + " characters x " + PLUGIN_CONFIG.maxMessages + " messages")
            .addField("Persona", escape(persona));
        if (module.lastError() != null) embed.addField("Last Error", escape(module.lastError()));
    }
}
