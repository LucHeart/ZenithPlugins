package lucheart.zenithchat.module;

import com.zenith.Proxy;
import com.zenith.util.ChatUtil;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.concurrent.LinkedBlockingQueue;

import static com.zenith.Globals.CONFIG;
import static lucheart.zenithchat.ZenithChatPlugin.LOG;
import static lucheart.zenithchat.ZenithChatPlugin.PLUGIN_CONFIG;

/**
 * Sends queued chat messages and DMs one at a time, {@code messageDelayMs} apart.
 * Messages only queue while the account is in game, and are dropped when it leaves.
 */
public final class ChatOutbox {
    // vanilla chat limit, the server cuts off anything longer
    public static final int MAX_LENGTH = 256;

    private final LinkedBlockingQueue<Message> queue = new LinkedBlockingQueue<>();
    private Thread sender;

    public enum Result {
        QUEUED,
        /** Disconnected or still in queue. */
        OFFLINE,
        QUEUE_FULL,
        /** Longer than {@link #MAX_LENGTH}, with the whisper command for DMs. */
        TOO_LONG,
        /** Empty message or an invalid player name. */
        INVALID,
        /** Starts with "/" where commands aren't allowed. */
        COMMAND_NOT_ALLOWED,
    }

    private record Message(String chat, String description) {}

    /** Queues a public chat message. With allowCommands a leading "/" runs it as a server command. */
    public Result say(String message, boolean allowCommands) {
        message = message.strip();
        if (!allowCommands && message.startsWith("/")) return Result.COMMAND_NOT_ALLOWED;
        return offer(message, "chat");
    }

    /** Queues a DM, sent with zenith's whisperCommand setting (/msg by default). */
    public Result dm(String player, String message) {
        if (!ChatUtil.isValidPlayerName(player)) return Result.INVALID;
        message = message.strip();
        if (message.isEmpty()) return Result.INVALID;
        // same as ChatUtil.getWhisperChatPacket, built here to check the length instead of cutting it off
        return offer("/" + CONFIG.client.extra.whisperCommand + " " + player + " " + message, "DM to " + player);
    }

    private Result offer(String raw, String description) {
        if (raw.length() > MAX_LENGTH) return Result.TOO_LONG;
        String chat = ChatUtil.sanitizeChatMessage(raw);
        if (chat.isBlank() || chat.equals("/")) return Result.INVALID;
        if (!canChat()) return Result.OFFLINE;
        if (queue.size() >= Math.max(1, PLUGIN_CONFIG.maxQueuedMessages)) return Result.QUEUE_FULL;
        queue.add(new Message(chat, description));
        return Result.QUEUED;
    }

    public int queued() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    public static boolean canChat() {
        var proxy = Proxy.getInstance();
        return proxy.isConnected() && !proxy.isInQueue();
    }

    public synchronized void start() {
        if (sender != null) return;
        sender = Thread.ofVirtual().name("zenith-chat-outbox").start(this::run);
    }

    public synchronized void stop() {
        if (sender != null) sender.interrupt();
        sender = null;
        queue.clear();
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Message message = queue.take();
                if (!canChat()) {
                    LOG.info("Dropped {} queued message(s), no longer in game", queue.size() + 1);
                    queue.clear();
                    continue;
                }
                // zenith sends a chat packet starting with "/" as a command, signed if the server wants that
                Proxy.getInstance().getClient().sendAsync(new ServerboundChatPacket(message.chat()));
                LOG.info("Sent {}: {}", message.description(), message.chat());
                Thread.sleep(Math.max(0, PLUGIN_CONFIG.messageDelayMs));
            }
        } catch (InterruptedException e) {
            // stopped
        }
    }
}
