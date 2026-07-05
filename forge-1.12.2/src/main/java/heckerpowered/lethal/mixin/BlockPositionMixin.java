package heckerpowered.lethal.mixin;

import heckerpowered.lethal.bridge.math.BlockPositionView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockPos.class)
@Implements(@Interface(iface = BlockPositionView.class, prefix = "blockPositionView$"))
class BlockPositionMixin extends Vec3i {
    private BlockPositionMixin(int x, int y, int z) {
        super(x, y, z);
    }

    public int blockPositionView$getX() {
        return super.getX();
    }

    public int blockPositionView$getY() {
        return super.getY();
    }

    public int blockPositionView$getZ() {
        return super.getZ();
    }
}
