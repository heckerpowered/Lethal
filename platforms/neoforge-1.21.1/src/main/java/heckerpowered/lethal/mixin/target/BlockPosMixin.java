/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.math.BlockPositionView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockPos.class)
@Implements(@Interface(iface = BlockPositionView.class, prefix = "blockPositionView$"))
abstract class BlockPosMixin extends Vec3i {
    private BlockPosMixin(int x, int y, int z) {
        super(x, y, z);
    }

    @Intrinsic
    public int blockPositionView$getX() {
        return super.getX();
    }

    @Intrinsic
    public int blockPositionView$getY() {
        return super.getY();
    }

    @Intrinsic
    public int blockPositionView$getZ() {
        return super.getZ();
    }
}
