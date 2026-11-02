package lucheart.zenithchat.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.module.api.Module;
import lucheart.zenithchat.api.ChatApiServer;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.saveConfigAsync;
import static lucheart.zenithchat.ZenithChatPlugin.PLUGIN_CONFIG;

/** Owns the outbox that sends messages, and the REST endpoint while it's turned on. */
public class ZenithChatModule extends Module {
    private final ChatOutbox outbox = new ChatOutbox();
    private final ChatApiServer api = new ChatApiServer(outbox);

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            // messages meant for this session shouldn't go out after a reconnect
            of(ClientDisconnectEvent.class, e -> outbox.clear())
        );
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
