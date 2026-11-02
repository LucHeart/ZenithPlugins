package lucheart.zenithexporter.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import lucheart.zenithexporter.module.ZenithExporterModule;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static lucheart.zenithexporter.ZenithExporterPlugin.PLUGIN_CONFIG;

public class ZenithExporterCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("zenithExporter")
            .category(CommandCategory.MODULE)
            .description("Prometheus metrics endpoint")
            .usageLines(
                "on/off",
                "port <port>",
                "bind <address>",
                "auth <username> <password>",
                "auth off",
                "jvm on/off",
                "position on/off",
                "players on/off",
                "statusPing on/off",
                "statusPing interval <seconds>",
                "watch add <player>",
                "watch remove <player>",
                "watch clear"
            )
            .aliases("exporter")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("zenithExporter")
            .executes(c -> {
                c.getSource().getEmbed()
                    .title("ZenithExporter");
            })
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                module().syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("ZenithExporter " + toggleStrCaps(PLUGIN_CONFIG.enabled));
            }))
            .then(literal("port").then(argument("port", integer(1, 65535)).executes(c -> {
                int port = getInteger(c, "port");
                return changeEndpoint(c.getSource(), () -> PLUGIN_CONFIG.port = port);
            })))
            .then(literal("bind").then(argument("address", string()).executes(c -> {
                var address = getString(c, "address");
                return changeEndpoint(c.getSource(), () -> PLUGIN_CONFIG.bindAddress = address);
            })))
            .then(literal("auth")
                .then(literal("off").executes(c -> {
                    return changeEndpoint(c.getSource(), () -> {
                        PLUGIN_CONFIG.authUsername = "";
                        PLUGIN_CONFIG.authPassword = "";
                    });
                }))
                .then(argument("username", string()).then(argument("password", string()).executes(c -> {
                    // keeps the password out of the logged command
                    c.getSource().setSensitiveInput(true);
                    var username = getString(c, "username");
                    var password = getString(c, "password");
                    return changeEndpoint(c.getSource(), () -> {
                        PLUGIN_CONFIG.authUsername = username;
                        PLUGIN_CONFIG.authPassword = password;
                    });
                }))))
            .then(literal("jvm").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.jvmMetrics = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("JVM Metrics " + toggleStrCaps(PLUGIN_CONFIG.jvmMetrics));
            })))
            .then(literal("position").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.exposePosition = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Position Metrics " + toggleStrCaps(PLUGIN_CONFIG.exposePosition));
            })))
            .then(literal("players").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.playerMetrics = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Player Metrics " + toggleStrCaps(PLUGIN_CONFIG.playerMetrics));
            })))
            .then(literal("statusPing")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.statusPing = getToggle(c, "toggle");
                    module().restartStatusPoller();
                    c.getSource().getEmbed()
                        .title("Status Ping " + toggleStrCaps(PLUGIN_CONFIG.statusPing));
                }))
                .then(literal("interval").then(argument("seconds", integer(5)).executes(c -> {
                    PLUGIN_CONFIG.statusPingIntervalSeconds = getInteger(c, "seconds");
                    module().restartStatusPoller();
                    c.getSource().getEmbed()
                        .title("Status Ping Interval Set");
                }))))
            .then(literal("watch")
                .then(literal("add").then(argument("player", string()).executes(c -> {
                    var player = getString(c, "player");
                    if (PLUGIN_CONFIG.watchedPlayers.stream().noneMatch(player::equalsIgnoreCase)) {
                        PLUGIN_CONFIG.watchedPlayers.add(player);
                    }
                    c.getSource().getEmbed()
                        .title("Watching " + player);
                })))
                .then(literal("remove").then(argument("player", string()).executes(c -> {
                    var player = getString(c, "player");
                    PLUGIN_CONFIG.watchedPlayers.removeIf(player::equalsIgnoreCase);
                    c.getSource().getEmbed()
                        .title("Stopped Watching " + player);
                })))
                .then(literal("clear").executes(c -> {
                    PLUGIN_CONFIG.watchedPlayers.clear();
                    c.getSource().getEmbed()
                        .title("Watch List Cleared");
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var module = module();
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("Endpoint", module.endpoint())
            .addField("Serving", String.valueOf(module.isServing()))
            .addField("Auth", toggleStr(!PLUGIN_CONFIG.authUsername.isEmpty() && !PLUGIN_CONFIG.authPassword.isEmpty()))
            .addField("JVM Metrics", toggleStr(PLUGIN_CONFIG.jvmMetrics))
            .addField("Position Metrics", toggleStr(PLUGIN_CONFIG.exposePosition))
            .addField("Player Metrics", toggleStr(PLUGIN_CONFIG.playerMetrics))
            .addField("Status Ping", toggleStr(PLUGIN_CONFIG.statusPing))
            .addField("Status Ping Interval", PLUGIN_CONFIG.statusPingIntervalSeconds + "s")
            .addField("Watched Players", PLUGIN_CONFIG.watchedPlayers.isEmpty() ? "None" : String.join(", ", PLUGIN_CONFIG.watchedPlayers));
    }

    /** Runs a change to the endpoint settings. If the endpoint can't start with them it goes back to the previous ones. */
    private int changeEndpoint(CommandContext source, Runnable change) {
        var module = module();
        var bindAddress = PLUGIN_CONFIG.bindAddress;
        var port = PLUGIN_CONFIG.port;
        var authUsername = PLUGIN_CONFIG.authUsername;
        var authPassword = PLUGIN_CONFIG.authPassword;
        change.run();
        if (!module.isEnabled() || module.restartServer()) {
            source.getEmbed().title("Endpoint Updated");
            return OK;
        }
        var failed = module.endpoint();
        PLUGIN_CONFIG.bindAddress = bindAddress;
        PLUGIN_CONFIG.port = port;
        PLUGIN_CONFIG.authUsername = authUsername;
        PLUGIN_CONFIG.authPassword = authPassword;
        module.restartServer();
        source.getEmbed()
            .title("Error")
            .errorColor()
            .description("Couldn't start the endpoint on " + failed + ", kept the previous settings. Check the logs");
        return ERROR;
    }

    private static ZenithExporterModule module() {
        return MODULE.get(ZenithExporterModule.class);
    }
}
