/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.block.BlockAccess;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.lethal.mixin.impl.BlockAccessImpl;
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
        return BlockAccessImpl.identifier(lethal$self());
    }

    @Unique
    private Block lethal$self() {
        return (Block) (Object) this;
    }
}
