/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.block.BlockAccess;
import heckerpowered.bridge.adapter.block.BlockCategory;
import heckerpowered.bridge.adapter.block.BlockClassificationAccess;
import heckerpowered.bridge.adapter.block.BlockStateAccess;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockState.class)
@Implements({
        @Interface(iface = BlockStateAccess.class, prefix = "blockStateAccess$"),
        @Interface(iface = BlockClassificationAccess.class, prefix = "blockClassificationAccess$")
})
abstract class BlockStateMixin {
    @NotNull
    public BlockAccess blockStateAccess$getBlock() {
        return MixinInterop.requireAccess(lethal$self().getBlock(), BlockAccess.class);
    }

    public boolean blockClassificationAccess$isReplaceable() {
        return lethal$self().canBeReplaced();
    }

    public boolean blockClassificationAccess$isIn(@NotNull BlockCategory category) {
        return switch (category) {
            case SugarCane -> lethal$self().getBlock() == Blocks.SUGAR_CANE;
            case Logs -> lethal$self().is(BlockTags.LOGS);
            case Planks -> lethal$self().is(BlockTags.PLANKS);
            case Snow -> lethal$self().is(BlockTags.SNOW);
            case Sand -> lethal$self().is(BlockTags.SAND);
        };
    }

    @Unique
    private BlockState lethal$self() {
        return (BlockState) (Object) this;
    }
}
