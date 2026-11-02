package lucheart.zenithexporter;

import java.util.ArrayList;
import java.util.List;

public class ZenithExporterConfig {
    public boolean enabled = false;
    /** Address the metrics endpoint listens on. Set to 0.0.0.0 if Prometheus is on another host or in Docker. */
    public String bindAddress = "127.0.0.1";
    public int port = 9430;
    /** Basic auth for the endpoint, off while either is empty. */
    public String authUsername = "";
    public String authPassword = "";
    /** Export JVM and process metrics next to the zenith ones. */
    public boolean jvmMetrics = true;
    /** Export the account's x/z coordinates. Off by default, anyone who can scrape the endpoint would see them. */
    public boolean exposePosition = false;
    /** Ping the configured server so its player count is known while the proxy is disconnected or in queue. */
    public boolean statusPing = true;
    public int statusPingIntervalSeconds = 60;
    /** Export a series per player on the tab list, with name and uuid. Grows Prometheus on servers with many different players. */
    public boolean playerMetrics = true;
    /** Player names to export an explicit online/offline series for, also while they're offline. */
    public List<String> watchedPlayers = new ArrayList<>();
}
