package lucheart.zenithanoying.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.chunk.Chunk;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.goals.GoalNear;
import com.zenith.feature.player.ClickTarget;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.Rotation;
import com.zenith.feature.player.RotationHelper;
import com.zenith.feature.player.World;
import com.zenith.feature.player.raycast.RaycastHelper;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.block.BlockState;
import com.zenith.mc.block.Direction;
import com.zenith.mc.block.properties.BellAttachType;
import com.zenith.mc.block.properties.api.BlockStateProperties;
import com.zenith.module.api.Module;
import com.zenith.util.math.MathHelper;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.rfresh2.EventConsumer.of;
import static lucheart.zenithanoying.AnoyingPlugin.PLUGIN_CONFIG;
import static com.zenith.Globals.BARITONE;
import static com.zenith.Globals.BOT;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INPUTS;

public class BlockSpamModule extends Module {
    private static final long RESCAN_INTERVAL_TICKS = 100L;
    private static final long PATH_RETRY_INTERVAL_TICKS = 20L;
    private static final int MAX_PATH_ATTEMPTS = 4;
    // offsets from the block center to aim at, closest to the center first
    private static final List<double[]> AIM_OFFSETS = aimOffsets();

    private final Timer scanTimer = Timers.tickTimer();
    private final Timer clickTimer = Timers.tickTimer();
    private final Timer pathTimer = Timers.tickTimer();
    private final Set<BlockPos> unreachable = new HashSet<>();
    private @Nullable BlockPos target = null;
    private @Nullable GoalNear activeGoal = null;
    private int pathAttempts = 0;
    private boolean warnedNoTarget = false;
    private boolean warnedOutOfReach = false;

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.blockSpam.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleBotTick),
            of(ClientBotTick.Starting.class, this::handleBotTickStarting)
        );
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    public @Nullable BlockPos getTarget() {
        return target;
    }

    public void reset() {
        stopPathing();
        unreachable.clear();
        setTarget(null);
        warnedNoTarget = false;
        scanTimer.skip();
    }

    private void handleBotTickStarting(ClientBotTick.Starting event) {
        reset();
    }

    private void handleBotTick(ClientBotTick event) {
        // a path someone else requested takes priority over us
        if (BARITONE.isActive() && !isPathing()) return;
        Block block = BlockRegistry.REGISTRY.get(PLUGIN_CONFIG.blockSpam.block);
        if (block == null) return;

        if (target != null && !isTargetBlock(target, block)) {
            setTarget(null);
            scanTimer.skip();
        }
        if (scanTimer.tick(RESCAN_INTERVAL_TICKS)) {
            scan(block);
        }
        if (target == null) {
            stopPathing();
            return;
        }

        BlockState state = World.getBlockState(target.x(), target.y(), target.z());
        Rotation rotation = findClickRotation(state);
        if (rotation != null) {
            stopPathing();
            pathAttempts = 0;
            click(target, rotation);
        } else if (PLUGIN_CONFIG.blockSpam.pathing) {
            pathToTarget(state);
        } else if (!warnedOutOfReach) {
            warnedOutOfReach = true;
            warn("{} at [{}, {}, {}] is out of reach and pathing is off", block.name(), target.x(), target.y(), target.z());
        }
    }

    private boolean isTargetBlock(BlockPos pos, Block block) {
        return World.isChunkLoadedBlockPos(pos.x(), pos.z()) && World.getBlock(pos.x(), pos.y(), pos.z()) == block;
    }

    private void scan(Block block) {
        BlockPos nearest = findNearest(block);
        if (nearest == null) {
            if (target == null && !warnedNoTarget) {
                warnedNoTarget = true;
                warn("No {} found in loaded chunks, will keep looking", block.name());
            }
            return;
        }
        warnedNoTarget = false;
        // only retarget for a block that's actually closer, so ties don't make us flip between blocks
        if (target == null || distanceSq(nearest) < distanceSq(target)) {
            setTarget(nearest);
            info("Targeting {} at [{}, {}, {}]", block.name(), nearest.x(), nearest.y(), nearest.z());
        }
    }

    private void setTarget(@Nullable BlockPos pos) {
        target = pos;
        pathAttempts = 0;
        warnedOutOfReach = false;
    }

    private @Nullable BlockPos findNearest(Block block) {
        int playerChunkX = MathHelper.floorI(CACHE.getPlayerCache().getX()) >> 4;
        int playerChunkZ = MathHelper.floorI(CACHE.getPlayerCache().getZ()) >> 4;
        int radius = PLUGIN_CONFIG.blockSpam.searchRadiusChunks;
        BlockPos nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (int chunkX = playerChunkX - radius; chunkX <= playerChunkX + radius; chunkX++) {
            for (int chunkZ = playerChunkZ - radius; chunkZ <= playerChunkZ + radius; chunkZ++) {
                Chunk chunk = World.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                var sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    if (section == null || section.isBlockCountEmpty()) continue;
                    if (!paletteContainsBlock(section.getChunkData().getPalette(), block)) continue;
                    int sectionMinY = chunk.minY() + (i << 4);
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                int stateId = section.getBlock(x, y, z);
                                if (stateId < block.minStateId() || stateId > block.maxStateId()) continue;
                                var pos = new BlockPos((chunkX << 4) | x, sectionMinY + y, (chunkZ << 4) | z);
                                if (unreachable.contains(pos)) continue;
                                double distSq = distanceSq(pos);
                                if (distSq < nearestSq) {
                                    nearest = pos;
                                    nearestSq = distSq;
                                }
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean paletteContainsBlock(Palette palette, Block block) {
        for (int id = block.minStateId(); id <= block.maxStateId(); id++) {
            if (palette.contains(id)) return true;
        }
        return false;
    }

    private static double distanceSq(BlockPos pos) {
        double dx = pos.x() + 0.5 - CACHE.getPlayerCache().getX();
        double dy = pos.y() + 0.5 - CACHE.getPlayerCache().getY();
        double dz = pos.z() + 0.5 - CACHE.getPlayerCache().getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Finds a rotation that hits the block on a face that does something when clicked.
     * Null if there is none from where we're standing.
     */
    private @Nullable Rotation findClickRotation(BlockState state) {
        var center = World.blockInteractionCenter(state.x(), state.y(), state.z());
        List<Direction> faces = clickableFaces(state);
        for (double[] offset : AIM_OFFSETS) {
            var rotation = RotationHelper.rotationTo(center.x() + offset[0], center.y() + offset[1], center.z() + offset[2]);
            var ray = RaycastHelper.playerEyeRaycastThroughToBlockTarget(state.x(), state.y(), state.z(), rotation.getX(), rotation.getY());
            if (ray.hit() && (faces == null || faces.contains(ray.direction()))) {
                return new Rotation(rotation.getX(), rotation.getY());
            }
        }
        return null;
    }

    /**
     * A bell only rings when hit on certain sides, depending on how it's attached.
     * Null if any face works.
     */
    private static @Nullable List<Direction> clickableFaces(BlockState state) {
        if (state.block() != BlockRegistry.BELL) return null;
        Direction facing = state.getProperty(BlockStateProperties.HORIZONTAL_FACING);
        BellAttachType attachment = state.getProperty(BlockStateProperties.BELL_ATTACHMENT);
        if (facing == null || attachment == null) return Direction.HORIZONTALS;
        return switch (attachment) {
            case FLOOR -> Direction.HORIZONTALS.stream().filter(d -> d.getAxis() == facing.getAxis()).toList();
            case SINGLE_WALL, DOUBLE_WALL -> Direction.HORIZONTALS.stream().filter(d -> d.getAxis() != facing.getAxis()).toList();
            case CEILING -> Direction.HORIZONTALS;
        };
    }

    private void click(BlockPos pos, Rotation rotation) {
        var in = Input.builder();
        // the timer fires once more than delay ticks have passed
        if (clickTimer.tick(PLUGIN_CONFIG.blockSpam.intervalTicks - 1)) {
            in.hand(Hand.MAIN_HAND)
                .clickRequiresRotation(true)
                .clickTarget(new ClickTarget.BlockPosition(pos.x(), pos.y(), pos.z()))
                .rightClick(true);
        }
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(in.build())
            .yaw(rotation.yaw())
            .pitch(rotation.pitch())
            .priority(Baritone.getPriority() + 1)
            .build());
    }

    private void pathToTarget(BlockState state) {
        if (BARITONE.isActive() || !pathTimer.tick(PATH_RETRY_INTERVAL_TICKS)) return;
        if (pathAttempts >= MAX_PATH_ATTEMPTS) {
            warn("Can't get to {} at [{}, {}, {}], looking for another", state.block().name(), state.x(), state.y(), state.z());
            unreachable.add(target);
            setTarget(null);
            scanTimer.skip();
            return;
        }
        activeGoal = pathGoal(state, pathAttempts++);
        BARITONE.pathTo(activeGoal);
    }

    private static GoalNear pathGoal(BlockState state, int attempt) {
        List<Direction> faces = clickableFaces(state);
        if (faces == null) {
            int rangeSq = Math.max(2, (int) Math.pow(BOT.getBlockReachDistance() - 1, 2));
            return new GoalNear(state.x(), state.y(), state.z(), rangeSq);
        }
        // stand in front of a face we can use, trying the next one if we couldn't get there or it didn't work
        // the range leaves room for the floor being below bells hanging from a ceiling
        Direction face = faces.get(attempt % faces.size());
        return new GoalNear(state.x() + face.x() * 2, state.y(), state.z() + face.z() * 2, 4);
    }

    private boolean isPathing() {
        return activeGoal != null && BARITONE.isGoalActive(activeGoal);
    }

    private void stopPathing() {
        if (isPathing()) BARITONE.stop();
        activeGoal = null;
    }

    private static List<double[]> aimOffsets() {
        List<double[]> offsets = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    offsets.add(new double[]{x * 0.2, y * 0.2, z * 0.2});
                }
            }
        }
        offsets.sort(Comparator.comparingDouble(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2]));
        return offsets;
    }
}
