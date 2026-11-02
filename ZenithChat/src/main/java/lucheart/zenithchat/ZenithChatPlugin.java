package lucheart.zenithchat;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import lucheart.zenithchat.command.ZenithChatCommand;
import lucheart.zenithchat.module.ZenithChatModule;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Sends chat messages and DMs from a command or a REST endpoint, spaced out so the server doesn't kick for spam",
    authors = {"LucHeart"},
    mcVersions = {BuildConstants.MC_VERSION}
)
public class ZenithChatPlugin implements ZenithProxyPlugin {
    public static ZenithChatConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, ZenithChatConfig.class);
        pluginAPI.registerModule(new ZenithChatModule());
        pluginAPI.registerCommand(new ZenithChatCommand());
        LOG.info("ZenithChat loaded");
    }
}
