package lucheart.zenithexporter;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import lucheart.zenithexporter.command.ZenithExporterCommand;
import lucheart.zenithexporter.module.ZenithExporterModule;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Prometheus metrics for ZenithProxy: players online, queue, connection, player, world and JVM stats",
    authors = {"LucHeart"},
    mcVersions = {BuildConstants.MC_VERSION}
)
public class ZenithExporterPlugin implements ZenithProxyPlugin {
    public static ZenithExporterConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, ZenithExporterConfig.class);
        pluginAPI.registerModule(new ZenithExporterModule());
        pluginAPI.registerCommand(new ZenithExporterCommand());
        LOG.info("ZenithExporter loaded");
    }
}
