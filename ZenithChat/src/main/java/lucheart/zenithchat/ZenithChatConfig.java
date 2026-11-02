package lucheart.zenithchat;

public class ZenithChatConfig {
    public boolean enabled = true;
    /** Time between two messages sent to the server (ms), so a burst doesn't get the account kicked for spam. */
    public int messageDelayMs = 1500;
    /** Messages waiting to be sent before new ones are turned away. */
    public int maxQueuedMessages = 20;

    /** REST endpoint for other programs, off until turned on. */
    public boolean apiEnabled = false;
    /** Address the endpoint listens on. Set to 0.0.0.0 if the caller is on another host or in Docker. */
    public String apiBindAddress = "127.0.0.1";
    public int apiPort = 9431;
    /** Sent by callers as "Authorization: Bearer <token>". Generated when the endpoint is first turned on. */
    public String apiToken = "";
    /** Let messages from the endpoint start with "/", i.e. run any server command as the account. */
    public boolean apiAllowCommands = false;
}
