/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.math.VectorView;
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

    @Intrinsic
    public double vectorView$getX() {
        return x;
    }

    @Intrinsic
    public double vectorView$getY() {
        return y;
    }

    @Intrinsic
    public double vectorView$getZ() {
        return z;
    }
}
