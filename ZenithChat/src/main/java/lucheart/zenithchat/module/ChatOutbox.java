package lucheart.zenithchat.module;

import com.zenith.Proxy;
import com.zenith.util.ChatUtil;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static com.zenith.Globals.CACHE;
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

    /** What {@link #dmOnline} did. With a result other than QUEUED nothing was queued. */
    public record BulkResult(Result result, List<String> queued, List<String> offline) {
        static BulkResult failed(Result result) {
            return new BulkResult(result, List.of(), List.of());
        }
    }

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
        return offer(whisper(player, message), "DM to " + player);
    }

    /**
     * Queues the same DM to every player in the list who is online, skipping the account itself.
     * Either all of them are queued or none, so a half sent notification doesn't happen.
     */
    public synchronized BulkResult dmOnline(Collection<String> players, String message) {
        message = message.strip();
        if (players.isEmpty() || message.isEmpty() || !players.stream().allMatch(ChatUtil::isValidPlayerName)) return BulkResult.failed(Result.INVALID);
        for (String player : players) {
            if (whisper(player, message).length() > MAX_LENGTH) return BulkResult.failed(Result.TOO_LONG);
        }
        if (!canChat()) return BulkResult.failed(Result.OFFLINE);

        var online = onlinePlayers();
        var self = CACHE.getProfileCache().getProfile();
        var selfName = self == null ? null : self.getName();
        List<String> queued = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        for (String player : players.stream().distinct().toList()) {
            if (player.equalsIgnoreCase(selfName)) continue;
            // the tab list has the name as typed in game, a DM works with any casing
            String name = online.get(player.toLowerCase(Locale.ROOT));
            if (name == null) offline.add(player);
            else if (!queued.contains(name)) queued.add(name);
        }
        if (queue.size() + queued.size() > Math.max(1, PLUGIN_CONFIG.maxQueuedMessages)) return BulkResult.failed(Result.QUEUE_FULL);
        for (String name : queued) {
            queue.add(new Message(ChatUtil.sanitizeChatMessage(whisper(name, message)), "DM to " + name));
        }
        return new BulkResult(Result.QUEUED, queued, offline);
    }

    /** Lowercase name to name of everyone in the tab list. */
    private static Map<String, String> onlinePlayers() {
        return CACHE.getTabListCache().getEntries().stream()
            .map(PlayerListEntry::getName)
            .filter(name -> name != null && !name.isEmpty())
            .collect(Collectors.toMap(name -> name.toLowerCase(Locale.ROOT), name -> name, (a, b) -> a));
    }

    // same as ChatUtil.getWhisperChatPacket, built here to check the length instead of cutting it off
    private static String whisper(String player, String message) {
        return "/" + CONFIG.client.extra.whisperCommand + " " + player + " " + message;
    }

    private synchronized Result offer(String raw, String description) {
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
