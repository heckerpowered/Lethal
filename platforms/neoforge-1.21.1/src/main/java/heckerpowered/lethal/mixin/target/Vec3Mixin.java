/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.math.VectorView;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Vec3.class)
@Implements(@Interface(iface = VectorView.class, prefix = "vectorView$"))
abstract class Vec3Mixin {
    @Shadow
    @Final
    public double x;

    @Shadow
    @Final
    public double y;

    @Shadow
    @Final
    public double z;

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
