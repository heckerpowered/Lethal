package heckerpowered.lethal.mixin;

import heckerpowered.lethal.bridge.math.VectorView;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;

@Mixin(Vec3d.class)
@Implements(@Interface(iface = VectorView.class, prefix = "vectorView$"))
class Vec3dMixin {
    @Shadow
    @Final
    public double x;

    @Shadow
    @Final
    public double y;

    @Shadow
    @Final
    public double z;

    private Vec3dMixin() {
    }

    public double vectorView$getX() {
        return x;
    }

    public double vectorView$getY() {
        return y;
    }

    public double vectorView$getZ() {
        return z;
    }
}