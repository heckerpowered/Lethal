/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.math.BoxView;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AABB.class)
@Implements(@Interface(iface = BoxView.class, prefix = "boxView$"))
abstract class AABBMixin {
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
}
