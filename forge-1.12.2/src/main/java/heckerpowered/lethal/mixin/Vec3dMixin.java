package heckerpowered.lethal.mixin;

import heckerpowered.lethal.bridge.math.VectorView;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;

@Mixin(Vec3d.class)
@Implements(@Interface(iface = VectorView.class, prefix = "vector3dView$"))
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

    public double vector3dView$getX() {
        return x;
    }

    public double vector3dView$getY() {
        return y;
    }

    public double vector3dView$getZ() {
        return z;
    }
}