package lucheart.zenithexporter.metrics;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.client.ClientConnectEvent;
import com.zenith.event.client.ClientDeathEvent;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.event.client.ClientOnlineEvent;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.event.module.PlayerHealthChangedEvent;
import com.zenith.event.module.ServerPlayerAttackedUsEvent;
import com.zenith.event.module.TotemPopEvent;
import com.zenith.mc.item.ItemRegistry;
import io.prometheus.metrics.core.metrics.Counter;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.BARITONE;
import static com.zenith.Globals.CACHE;
import static lucheart.zenithexporter.ZenithExporterPlugin.PLUGIN_CONFIG;
import static lucheart.zenithexporter.metrics.ZenithState.inGame;

/** The account the proxy is playing on. */
public final class PlayerMetrics implements MetricGroup {
    private final Instruments instruments;
    private final MovementTracker movement = new MovementTracker();
    private final SessionSettleGate settleGate = new SessionSettleGate();
    private final Counter distanceMoved;
    private final Counter deaths;
    private final Counter totemPops;
    private final Counter damageTaken;
    private final Counter attackedByPlayers;

    public PlayerMetrics(Instruments instruments) {
        this.instruments = instruments;
        inGameGauge("zenith_player_health", "Health points", () -> {
            var health = self().getHealth();
            return health != null ? health : 0;
        });
        inGameGauge("zenith_player_food", "Food level", () -> self().getFood());
        inGameGauge("zenith_player_saturation", "Food saturation", () -> self().getSaturation());
        inGameGauge("zenith_player_alive", "Whether the account is alive", () -> self().isAlive() ? 1 : 0);
        inGameGauge("zenith_player_experience_level", "Experience level", () -> self().getLevel());
        inGameGauge("zenith_player_experience_progress_ratio", "Progress to the next experience level, 0 to 1", () -> self().getExperience());
        inGameGauge("zenith_player_experience_points", "Total experience points", () -> self().getTotalExperience());
        inGameGauge("zenith_player_being_controlled", "Whether a player is controlling the account through the proxy",
            () -> Proxy.getInstance().hasActivePlayer() ? 1 : 0);
        inGameGauge("zenith_player_pathing", "Whether the proxy's pathfinder is moving the account", () -> BARITONE.isActive() ? 1 : 0);

        instruments.gauge("zenith_player_position_blocks", "Position of the account. x and z only with exposePosition on",
            cb -> {
                if (!inGame()) return;
                var cache = CACHE.getPlayerCache();
                cb.call(cache.getY(), "y");
                if (PLUGIN_CONFIG.exposePosition) {
                    cb.call(cache.getX(), "x");
                    cb.call(cache.getZ(), "z");
                }
            }, "axis");
        instruments.gauge("zenith_player_gamemode", "Current game mode",
            cb -> {
                var gameMode = CACHE.getPlayerCache().getGameMode();
                if (inGame() && gameMode != null) cb.call(1, lower(gameMode.name()));
            }, "gamemode");
        instruments.gauge("zenith_player_effect_amplifier", "Level of each active potion effect, 0 is level 1",
            cb -> {
                if (!inGame()) return;
                for (var effect : self().getPotionEffectMap().values()) cb.call(effect.getAmplifier(), lower(effect.getEffect().name()));
            }, "effect");
        instruments.gauge("zenith_player_inventory_items", "Items in the inventory, including equipment",
            cb -> {
                if (!inGame()) return;
                var counts = new HashMap<String, Integer>();
                for (var stack : CACHE.getPlayerCache().getPlayerInventory()) {
                    if (stack == null) continue;
                    var item = ItemRegistry.REGISTRY.get(stack.getId());
                    if (item != null) counts.merge(item.name(), stack.getAmount(), Integer::sum);
                }
                counts.forEach((item, amount) -> cb.call(amount, item));
            }, "item");
        instruments.gauge("zenith_player_equipment_durability_ratio", "Durability left on worn and held items, 0 to 1",
            cb -> {
                if (!inGame()) return;
                for (var equipped : CACHE.getPlayerCache().getEquipment().entrySet()) {
                    var stack = equipped.getValue();
                    if (stack == null) continue;
                    var item = ItemRegistry.REGISTRY.get(stack.getId());
                    if (item == null) continue;
                    var maxDamage = item.components().get(DataComponentTypes.MAX_DAMAGE);
                    if (maxDamage == null || maxDamage <= 0) continue;
                    var damage = stack.getDataComponentsOrEmpty().get(DataComponentTypes.DAMAGE);
                    cb.call(1 - (damage != null ? damage : 0) / (double) maxDamage, lower(equipped.getKey().name()), item.name());
                }
            }, "slot", "item");

        distanceMoved = instruments.counter("zenith_player_moved_blocks_total", "Distance moved, teleports not counted");
        deaths = instruments.counter("zenith_player_deaths_total", "Deaths of the account");
        totemPops = instruments.counter("zenith_player_totem_pops_total", "Totems of undying the account used");
        damageTaken = instruments.counter("zenith_player_damage_taken_total", "Health points lost");
        attackedByPlayers = instruments.counter("zenith_player_attacked_by_players_total", "Attacks on the account by other players");
    }

    @Override
    public List<EventConsumer<?>> events() {
        return List.of(
            of(ClientTickEvent.class, e -> instruments.guard("player_movement", this::trackMovement)),
            of(ClientTickEvent.Starting.class, e -> {
                movement.reset();
                settleGate.sessionStarted(System.currentTimeMillis());
            }),
            of(ClientConnectEvent.class, e -> settleGate.sessionStarted(System.currentTimeMillis())),
            of(ClientOnlineEvent.class, e -> settleGate.sessionStarted(System.currentTimeMillis())),
            of(ClientDisconnectEvent.class, e -> movement.reset()),
            of(ClientDeathEvent.class, e -> deaths.inc()),
            of(TotemPopEvent.class, this::onTotemPop),
            of(PlayerHealthChangedEvent.class, this::onHealthChanged),
            of(ServerPlayerAttackedUsEvent.class, e -> attackedByPlayers.inc())
        );
    }

    private void trackMovement() {
        if (!inGame()) {
            movement.reset();
            return;
        }
        var cache = CACHE.getPlayerCache();
        var moved = movement.update(cache.getX(), cache.getY(), cache.getZ());
        if (moved > 0) distanceMoved.inc(moved);
    }

    private void onTotemPop(TotemPopEvent event) {
        // the event fires for every entity in range
        if (event.entityId() == CACHE.getPlayerCache().getEntityId()) totemPops.inc();
    }

    private void onHealthChanged(PlayerHealthChangedEvent event) {
        if (!settleGate.counts(System.currentTimeMillis())) return;
        if (event.newHealth() < event.previousHealth()) damageTaken.inc(event.previousHealth() - event.newHealth());
    }

    private void inGameGauge(String name, String help, DoubleSupplier value) {
        instruments.gauge(name, help, cb -> { if (inGame()) cb.call(value.getAsDouble()); });
    }

    private static EntityPlayer self() {
        return CACHE.getPlayerCache().getThePlayer();
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
