package lucheart.zenithchat.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import lucheart.zenithchat.module.ChatOutbox;
import lucheart.zenithchat.module.ChatOutbox.Result;
import lucheart.zenithchat.module.ZenithChatModule;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.discord.DiscordBot.escape;
import static lucheart.zenithchat.ZenithChatPlugin.PLUGIN_CONFIG;

public class ZenithChatCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("zenithChat")
            .category(CommandCategory.MODULE)
            .description("""
                Sends chat messages and DMs, one every messageDelayMs so the server doesn't kick for spam.
                say can run server commands with a leading "/".

                api: REST endpoint for other programs, see the plugin's README.
                Callers send "Authorization: Bearer <token>", the token is generated when the api is first turned on.
                """)
            .usageLines(
                "on/off",
                "say <message>",
                "dm <player> <message>",
                "delay <ms>",
                "api on/off",
                "api port <port>",
                "api bind <address>",
                "api token",
                "api token new",
                "api commands on/off"
            )
            .aliases("zc")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("zenithChat")
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                module().syncEnabledFromConfig();
                c.getSource().getEmbed().title("ZenithChat " + toggleStrCaps(PLUGIN_CONFIG.enabled));
            }))
            .then(literal("say").then(argument("message", greedyString()).executes(c -> {
                var message = getString(c, "message");
                return queued(c.getSource(), outbox().say(message, true), "Message", message);
            })))
            .then(literal("dm").then(argument("player", string()).then(argument("message", greedyString()).executes(c -> {
                var player = getString(c, "player");
                var message = getString(c, "message");
                return queued(c.getSource(), outbox().dm(player, message), "DM to " + player, message);
            }))))
            .then(literal("delay").then(argument("ms", integer(0, 60000)).executes(c -> {
                PLUGIN_CONFIG.messageDelayMs = getInteger(c, "ms");
                c.getSource().getEmbed().title("Message Delay Set");
            })))
            .then(literal("api")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.apiEnabled = getToggle(c, "toggle");
                    return changeApi(c.getSource(), "Chat API " + toggleStrCaps(PLUGIN_CONFIG.apiEnabled));
                }))
                .then(literal("port").then(argument("port", integer(1, 65535)).executes(c -> {
                    PLUGIN_CONFIG.apiPort = getInteger(c, "port");
                    return changeApi(c.getSource(), "Chat API Port Set");
                })))
                .then(literal("bind").then(argument("address", string()).executes(c -> {
                    PLUGIN_CONFIG.apiBindAddress = getString(c, "address");
                    return changeApi(c.getSource(), "Chat API Address Set");
                })))
                .then(literal("token")
                    .executes(c -> {
                        c.getSource().getEmbed()
                            .title("Chat API Token")
                            .description(PLUGIN_CONFIG.apiToken.isEmpty()
                                ? "Not generated yet, turn the api on or run `api token new`"
                                : "`" + PLUGIN_CONFIG.apiToken + "`");
                    })
                    .then(literal("new").executes(c -> {
                        var token = module().newApiToken();
                        c.getSource().getEmbed()
                            .title("Chat API Token Replaced")
                            .description("`" + token + "`\nThe old token stops working right away");
                    })))
                .then(literal("commands").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.apiAllowCommands = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Chat API Commands " + toggleStrCaps(PLUGIN_CONFIG.apiAllowCommands));
                }))));
    }

    private int queued(CommandContext source, Result result, String what, String message) {
        var embed = source.getEmbed();
        switch (result) {
            case QUEUED -> {
                embed.title(what + " Queued").description(escape(message));
                return OK;
            }
            case OFFLINE -> embed.title("Not In Game");
            case QUEUE_FULL -> embed.title("Too Many Messages Waiting");
            case TOO_LONG -> embed.title("Message Too Long").description("The limit is " + ChatOutbox.MAX_LENGTH + " characters, for DMs including the whisper command");
            case INVALID -> embed.title("Empty Message Or Invalid Player Name");
            case COMMAND_NOT_ALLOWED -> embed.title("Commands Not Allowed");
        }
        return ERROR;
    }

    private int changeApi(CommandContext source, String title) {
        boolean running = module().restartApi();
        var embed = source.getEmbed().title(title);
        if (PLUGIN_CONFIG.apiEnabled && module().isEnabled() && !running) {
            embed.description("Failed to start, see the log");
            return ERROR;
        }
        return OK;
    }

    private static ZenithChatModule module() {
        return MODULE.get(ZenithChatModule.class);
    }

    private static ChatOutbox outbox() {
        return module().outbox();
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var module = module();
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("In Game", toggleStr(ChatOutbox.canChat()))
            .addField("Message Delay", PLUGIN_CONFIG.messageDelayMs + " ms")
            .addField("Queued", module.outbox().queued())
            .addField("API", !PLUGIN_CONFIG.apiEnabled ? "off"
                : module.isApiRunning() ? module.apiEndpoint() : "not running")
            .addField("API Commands", toggleStr(PLUGIN_CONFIG.apiAllowCommands));
    }
}
