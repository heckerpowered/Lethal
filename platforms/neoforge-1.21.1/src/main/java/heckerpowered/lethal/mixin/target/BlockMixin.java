/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.block.BlockAccess;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.bridge.MixinInterop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Block.class)
@Implements(@Interface(iface = BlockAccess.class, prefix = "blockAccess$"))
abstract class BlockMixin {
    @NotNull
    public Identifier blockAccess$getIdentifier() {
        return MixinInterop.requireAccess(BuiltInRegistries.BLOCK.getKey(lethal$self()), Identifier.class);
    }

    @Unique
    private Block lethal$self() {
        return (Block) (Object) this;
    }
}
