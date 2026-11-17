package lucheart.zenithchat.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.chat.DeathMessageChatEvent;
import com.zenith.event.chat.PublicChatEvent;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.chat.WhisperChatEvent;
import com.zenith.event.client.ClientConnectEvent;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.module.api.Module;
import lucheart.zenithchat.api.ChatApiServer;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.saveConfigAsync;
import static com.zenith.util.ComponentSerializer.serializePlain;
import static lucheart.zenithchat.ZenithChatPlugin.PLUGIN_CONFIG;

/** Owns the outbox that sends messages, the chat log, and the REST endpoint while it's turned on. */
public class ZenithChatModule extends Module {
    private final ChatOutbox outbox = new ChatOutbox();
    private final ChatApiServer api = new ChatApiServer(outbox);
    private final ChatLog chatLog = new ChatLog(() -> Path.of(PLUGIN_CONFIG.chatLogDirectory), Clock.systemDefaultZone());

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientDisconnectEvent.class, e -> {
                // messages meant for this session shouldn't go out after a reconnect
                outbox.clear();
                logLine("PROXY", "Disconnected: " + e.reason());
            }),
            of(ClientConnectEvent.class, e -> logLine("PROXY", "Connected")),
            // the server's chat packets end up as exactly one of these
            of(PublicChatEvent.class, e -> logChat("CHAT", e.component())),
            of(WhisperChatEvent.class, e -> logChat(e.outgoing() ? "DM OUT" : "DM IN", e.component())),
            of(DeathMessageChatEvent.class, e -> logChat("DEATH", e.component())),
            of(SystemChatEvent.class, e -> logChat("SYSTEM", e.component()))
        );
    }

    private void logChat(String type, Component component) {
        if (!PLUGIN_CONFIG.chatLogInQueue && Proxy.getInstance().isInQueue()) return;
        logLine(type, serializePlain(component));
    }

    private void logLine(String type, String text) {
        if (PLUGIN_CONFIG.chatLogEnabled) chatLog.write(type, text);
    }

    @Override
    public void onEnable() {
        outbox.start();
        if (PLUGIN_CONFIG.apiEnabled) startApi();
    }

    @Override
    public void onDisable() {
        api.stop();
        outbox.stop();
        chatLog.close();
    }

    public ChatLog chatLog() {
        return chatLog;
    }

    public ChatOutbox outbox() {
        return outbox;
    }

    /** Applies changed endpoint settings. Returns whether the endpoint is up, if it should be at all. */
    public synchronized boolean restartApi() {
        api.stop();
        if (!isEnabled() || !PLUGIN_CONFIG.apiEnabled) return false;
        return startApi();
    }

    public boolean isApiRunning() {
        return api.isRunning();
    }

    public String apiEndpoint() {
        return api.endpoint();
    }

    /** Replaces the token callers need, returns the new one. */
    public String newApiToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        PLUGIN_CONFIG.apiToken = HexFormat.of().formatHex(bytes);
        saveConfigAsync();
        return PLUGIN_CONFIG.apiToken;
    }

    private boolean startApi() {
        if (PLUGIN_CONFIG.apiToken.isEmpty()) newApiToken();
        return api.start();
    }
}
