package lucheart.zenithanoying.mixin;

import com.zenith.feature.pathfinder.PrecomputedData;
import com.zenith.mc.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Zenith sizes this array to the highest block state id instead of that id + 1, so the pathfinder
 * throws ArrayIndexOutOfBoundsException on every path that comes near the last registered block
 * (firefly_bush on 26.x).
 */
@Mixin(PrecomputedData.class)
public abstract class PrecomputedDataMixin {
    @Redirect(method = "<init>", at = @At(
        value = "INVOKE",
        target = "Lcom/zenith/mc/block/Block;maxStateId()I"
    ))
    private int dataSize(Block block) {
        return block.maxStateId() + 1;
    }
}
