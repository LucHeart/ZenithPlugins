package lucheart.zenithchatbot;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import lucheart.zenithchatbot.command.ChatBotCommand;
import lucheart.zenithchatbot.module.ChatBotModule;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Answers players in chat with Claude when they mention the account, with the recent chat as context",
    authors = {"LucHeart"},
    mcVersions = {BuildConstants.MC_VERSION}
)
public class ZenithChatBotPlugin implements ZenithProxyPlugin {
    public static ZenithChatBotConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, ZenithChatBotConfig.class);
        pluginAPI.registerModule(new ChatBotModule());
        pluginAPI.registerCommand(new ChatBotCommand());
        LOG.info("ZenithChatBot loaded");
    }
}
