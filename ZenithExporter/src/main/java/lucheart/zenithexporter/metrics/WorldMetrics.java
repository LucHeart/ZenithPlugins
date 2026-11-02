package lucheart.zenithexporter.metrics;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.event.module.VisualRangeEnterEvent;
import com.zenith.event.module.VisualRangeLeaveEvent;
import com.zenith.event.module.VisualRangeLogoutEvent;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.network.client.ClientSession;
import io.prometheus.metrics.core.metrics.Counter;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.ENTITY_DATA;
import static lucheart.zenithexporter.metrics.ZenithState.inGame;

/** The world around the account: loaded chunks, entities, dropped items, nearby players. */
public final class WorldMetrics implements MetricGroup {
    private static final int MAX_PENDING_ITEMS = 8192;

    private final Instruments instruments;
    private final Counter chunkLoads;
    private final Counter entitiesSpawned;
    private final Counter itemsSpawned;
    private final Counter visualRange;
    // item entities that spawned but haven't said what item they are yet. Only the first metadata counts,
    // later updates are stacks merging or splitting
    private final Set<Integer> pendingItems = ConcurrentHashMap.newKeySet();
    private volatile int latestEntityId = 0;

    public WorldMetrics(Instruments instruments) {
        this.instruments = instruments;
        instruments.gauge("zenith_world_dimension", "Dimension the account is in",
            cb -> {
                var world = CACHE.getChunkCache().getWorldName();
                if (inGame() && world != null) cb.call(1, world.asString());
            }, "dimension");
        instruments.gauge("zenith_world_chunks_loaded", "Chunks currently loaded",
            cb -> { if (inGame()) cb.call(CACHE.getChunkCache().getCache().size()); });
        instruments.gauge("zenith_world_view_distance_chunks", "View distance the server sent",
            cb -> { if (inGame()) cb.call(CACHE.getChunkCache().getServerViewDistance()); });
        instruments.gauge("zenith_world_simulation_distance_chunks", "Simulation distance the server sent",
            cb -> { if (inGame()) cb.call(CACHE.getChunkCache().getServerSimulationDistance()); });
        instruments.gauge("zenith_world_border_size_blocks", "Diameter of the world border",
            cb -> { if (inGame()) cb.call(CACHE.getChunkCache().getWorldBorderData().getSize()); });
        instruments.gauge("zenith_world_age_ticks", "Ticks the world has existed for",
            cb -> {
                var time = CACHE.getChunkCache().getWorldTimeData();
                if (inGame() && time != null) cb.call(time.getGameTime());
            });
        instruments.gauge("zenith_world_raining", "Whether it's raining",
            cb -> { if (inGame()) cb.call(CACHE.getChunkCache().isRaining() ? 1 : 0); });
        instruments.gauge("zenith_world_rain_strength", "Rain strength, 0 to 1",
            cb -> { if (inGame()) cb.call(CACHE.getChunkCache().getRainStrength()); });
        instruments.gauge("zenith_world_thunder_strength", "Thunderstorm strength, 0 to 1",
            cb -> { if (inGame()) cb.call(CACHE.getChunkCache().getThunderStrength()); });

        instruments.gauge("zenith_world_entities", "Entities loaded around the account",
            cb -> {
                if (!inGame()) return;
                var counts = new HashMap<String, Integer>();
                for (var entity : CACHE.getEntityCache().getEntities().values()) {
                    counts.merge(entityName(entity.getEntityType()), 1, Integer::sum);
                }
                counts.forEach((type, count) -> cb.call(count, type));
            }, "type");
        instruments.gauge("zenith_world_dropped_items", "Dropped items around the account, counting whole stacks",
            cb -> {
                if (!inGame()) return;
                var counts = new HashMap<String, Integer>();
                for (var entity : CACHE.getEntityCache().getEntities().values()) {
                    if (entity.getEntityType() != EntityType.ITEM) continue;
                    for (var metadata : entity.getMetadata().values()) {
                        if (metadata.getValue() instanceof ItemStack stack) {
                            var item = ItemRegistry.REGISTRY.get(stack.getId());
                            if (item != null) counts.merge(item.name(), stack.getAmount(), Integer::sum);
                        }
                    }
                }
                counts.forEach((item, amount) -> cb.call(amount, item));
            }, "item");
        instruments.gauge("zenith_world_players_nearby", "Other players loaded around the account",
            cb -> {
                if (!inGame()) return;
                cb.call(CACHE.getEntityCache().getEntities().values().stream()
                    .filter(entity -> entity.getEntityType() == EntityType.PLAYER)
                    .filter(entity -> entity.getEntityId() != CACHE.getPlayerCache().getEntityId())
                    .count());
            });
        instruments.gauge("zenith_world_latest_entity_id", "Id of the newest entity seen, servers count these up so its rate shows activity",
            cb -> { if (inGame() && latestEntityId > 0) cb.call(latestEntityId); });

        chunkLoads = instruments.counter("zenith_world_chunk_loads_total", "Chunks received from the server");
        entitiesSpawned = instruments.counter("zenith_world_entities_spawned_total", "Entities that came into range", "type");
        itemsSpawned = instruments.counter("zenith_world_items_spawned_total", "Dropped items that came into range, counting whole stacks", "item");
        visualRange = instruments.counter("zenith_visual_range_events_total", "Players entering, leaving or logging out in visual range", "event", "friend");
        for (var event : List.of("enter", "leave", "logout")) {
            for (var friend : List.of("true", "false")) visualRange.initLabelValues(event, friend);
        }
    }

    @Override
    public List<EventConsumer<?>> events() {
        return List.of(
            of(ClientDisconnectEvent.class, e -> {
                // ids are per server connection
                pendingItems.clear();
                latestEntityId = 0;
            }),
            of(VisualRangeEnterEvent.class, e -> visualRange.labelValues("enter", String.valueOf(e.isFriend())).inc()),
            of(VisualRangeLeaveEvent.class, e -> visualRange.labelValues("leave", String.valueOf(e.isFriend())).inc()),
            of(VisualRangeLogoutEvent.class, e -> visualRange.labelValues("logout", String.valueOf(e.isFriend())).inc())
        );
    }

    public ClientboundLevelChunkWithLightPacket onChunk(ClientboundLevelChunkWithLightPacket packet, ClientSession session) {
        chunkLoads.inc();
        return packet;
    }

    public void onEntitySpawn(int entityId, EntityType type) {
        instruments.guard("entity_spawn", () -> {
            latestEntityId = entityId;
            entitiesSpawned.labelValues(entityName(type)).inc();
            if (type == EntityType.ITEM) {
                if (pendingItems.size() >= MAX_PENDING_ITEMS) pendingItems.clear();
                pendingItems.add(entityId);
            }
        });
    }

    public ClientboundSetEntityDataPacket onEntityData(ClientboundSetEntityDataPacket packet, ClientSession session) {
        if (pendingItems.isEmpty()) return packet;
        instruments.guard("item_spawn", () -> {
            if (!pendingItems.contains(packet.getEntityId())) return;
            for (var metadata : packet.getMetadata()) {
                if (metadata.getValue() instanceof ItemStack stack) {
                    pendingItems.remove(packet.getEntityId());
                    var item = ItemRegistry.REGISTRY.get(stack.getId());
                    if (item != null) itemsSpawned.labelValues(item.name()).inc(stack.getAmount());
                    return;
                }
            }
        });
        return packet;
    }

    private static String entityName(EntityType type) {
        var data = ENTITY_DATA.getEntityData(type);
        return data != null ? data.name() : "unknown";
    }
}
