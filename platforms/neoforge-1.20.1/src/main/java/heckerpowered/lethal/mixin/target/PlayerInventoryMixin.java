/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.PlayerInventoryAccess;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.lethal.mixin.impl.PlayerInventoryAccessImpl;
import kotlin.sequences.Sequence;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
@Implements(@Interface(iface = PlayerInventoryAccess.class, prefix = "playerInventory$"))
abstract class PlayerInventoryMixin {
    public @NotNull Sequence<ItemStackAccess> playerInventory$getCarriedStacks() {
        return PlayerInventoryAccessImpl.carriedStacks((Player) (Object) this);
    }
}
