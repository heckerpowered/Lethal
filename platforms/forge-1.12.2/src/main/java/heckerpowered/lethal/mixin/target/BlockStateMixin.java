/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.block.BlockAccess;
import heckerpowered.bridge.adapter.block.BlockStateAccess;
import heckerpowered.bridge.MixinInterop;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockStateContainer.StateImplementation.class)
@Implements(@Interface(iface = BlockStateAccess.class, prefix = "blockStateAccess$"))
abstract class BlockStateMixin {
    @Shadow
    public abstract Block getBlock();

    @NotNull
    public BlockAccess blockStateAccess$getBlock() {
        return MixinInterop.requireAccess(getBlock(), BlockAccess.class);
    }
}
