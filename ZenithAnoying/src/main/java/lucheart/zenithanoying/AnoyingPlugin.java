package lucheart.zenithanoying;

import lucheart.zenithanoying.command.BlockSpamCommand;
import lucheart.zenithanoying.module.BlockSpamModule;
import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Finds the nearest bell (or any other block) and spam clicks it",
    authors = {"LucHeart"},
    mcVersions = {BuildConstants.MC_VERSION},
    mixins = {BuildConstants.MAVEN_GROUP + ".mixin.**"}
)
public class AnoyingPlugin implements ZenithProxyPlugin {
    public static AnoyingConfig PLUGIN_CONFIG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, AnoyingConfig.class);
        pluginAPI.registerModule(new BlockSpamModule());
        pluginAPI.registerCommand(new BlockSpamCommand());
    }
}
