/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.bridge.math.BoxView;
import heckerpowered.lethal.bridge.math.Geometry;
import heckerpowered.lethal.bridge.math.VectorView;
import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(AxisAlignedBB.class)
@Implements(@Interface(iface = BoxView.class, prefix = "boxView$"))
class AxisAlignedBBMixin {
    @Shadow
    @Final
    public double minX;

    @Shadow
    @Final
    public double minY;

    @Shadow
    @Final
    public double minZ;

    @Shadow
    @Final
    public double maxX;

    @Shadow
    @Final
    public double maxY;

    @Shadow
    @Final
    public double maxZ;

    @Intrinsic
    public double boxView$getMinX() {
        return minX;
    }

    @Intrinsic
    public double boxView$getMinY() {
        return minY;
    }

    @Intrinsic
    public double boxView$getMinZ() {
        return minZ;
    }

    @Intrinsic
    public double boxView$getMaxX() {
        return maxX;
    }

    @Intrinsic
    public double boxView$getMaxY() {
        return maxY;
    }

    @Intrinsic
    public double boxView$getMaxZ() {
        return maxZ;
    }

    @NotNull
    public VectorView boxView$getMin() {
        return Geometry.vector(minX, minY, minZ);
    }

    @NotNull
    public VectorView boxView$getMax() {
        return Geometry.vector(maxX, maxY, maxZ);
    }
}
