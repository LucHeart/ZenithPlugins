package lucheart.zenithanoying.command;

import lucheart.zenithanoying.module.BlockSpamModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static lucheart.zenithanoying.AnoyingPlugin.PLUGIN_CONFIG;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockArgument.block;
import static com.zenith.command.brigadier.BlockArgument.getBlock;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class BlockSpamCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("blockSpam")
            .category(CommandCategory.MODULE)
            .description("""
                Finds the nearest block of a type, walks to it, and spam right clicks it
                """)
            .usageLines(
                "on/off",
                "block <block>",
                "interval <ticks>",
                "radius <chunks>",
                "pathing on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("blockSpam")
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.blockSpam.enabled = getToggle(c, "toggle");
                MODULE.get(BlockSpamModule.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Block Spam " + toggleStrCaps(PLUGIN_CONFIG.blockSpam.enabled));
            }))
            .then(literal("block").then(argument("block", block()).executes(c -> {
                var block = getBlock(c, "block");
                if (block.isAir()) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .description("Can't click air");
                    return ERROR;
                }
                PLUGIN_CONFIG.blockSpam.block = block.name();
                MODULE.get(BlockSpamModule.class).reset();
                c.getSource().getEmbed()
                    .title("Block Set");
                return OK;
            })))
            .then(literal("interval").then(argument("ticks", integer(1)).executes(c -> {
                PLUGIN_CONFIG.blockSpam.intervalTicks = getInteger(c, "ticks");
                c.getSource().getEmbed()
                    .title("Interval Set");
            })))
            .then(literal("radius").then(argument("chunks", integer(0, 64)).executes(c -> {
                PLUGIN_CONFIG.blockSpam.searchRadiusChunks = getInteger(c, "chunks");
                c.getSource().getEmbed()
                    .title("Radius Set");
            })))
            .then(literal("pathing").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.blockSpam.pathing = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Pathing " + toggleStrCaps(PLUGIN_CONFIG.blockSpam.pathing));
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var target = MODULE.get(BlockSpamModule.class).getTarget();
        embed
            .primaryColor()
            .addField("Block Spam", toggleStr(PLUGIN_CONFIG.blockSpam.enabled))
            .addField("Block", PLUGIN_CONFIG.blockSpam.block)
            .addField("Interval", PLUGIN_CONFIG.blockSpam.intervalTicks + " ticks")
            .addField("Radius", PLUGIN_CONFIG.blockSpam.searchRadiusChunks + " chunks")
            .addField("Pathing", toggleStr(PLUGIN_CONFIG.blockSpam.pathing))
            .addField("Target", target == null ? "None" : "[" + target.x() + ", " + target.y() + ", " + target.z() + "]");
    }
}
