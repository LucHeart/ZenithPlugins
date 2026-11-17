package lucheart.zenithchat;

public class ZenithChatConfig {
    public boolean enabled = true;
    /** Time between two messages sent to the server (ms), so a burst doesn't get the account kicked for spam. */
    public int messageDelayMs = 1500;
    /** Messages waiting to be sent before new ones are turned away. */
    public int maxQueuedMessages = 20;

    /** Save every chat message the account receives, one file per day. */
    public boolean chatLogEnabled = true;
    /** Where the chat log files go, relative to ZenithProxy's folder unless absolute. */
    public String chatLogDirectory = "chatlogs";
    /** Also save messages received while in queue (mostly queue position updates). */
    public boolean chatLogInQueue = false;

    /** REST endpoint for other programs, off until turned on. */
    public boolean apiEnabled = false;
    /** Address the endpoint listens on. Set to 0.0.0.0 if the caller is on another host or in Docker. */
    public String apiBindAddress = "127.0.0.1";
    public int apiPort = 9431;
    /** Sent by callers as "Authorization: Bearer <token>". Generated when the endpoint is first turned on. */
    public String apiToken = "";
    /** Let messages from the endpoint start with "/", i.e. run any server command as the account. */
    public boolean apiAllowCommands = false;

    /** BlueMapAlert's address, such as http://192.168.1.10:9432, which gets the players around the account. Empty sends nothing. */
    public String reportUrl = "";
    /** BlueMapAlert's botReportToken. */
    public String reportToken = "";
    /** How often to look for changes (ms). Even without changes a report goes out every 5 seconds. */
    public int reportIntervalMs = 1000;
}
