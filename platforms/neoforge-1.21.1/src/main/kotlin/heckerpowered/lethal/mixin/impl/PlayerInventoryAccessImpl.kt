/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.world.entity.player.Player

object PlayerInventoryAccessImpl {
    @JvmStatic
    fun carriedStacks(player: Player): Sequence<ItemStackAccess> = sequence {
        for (stack in player.inventory.items) yield(stack.asView())
        for (stack in player.inventory.offhand) yield(stack.asView())
    }
}
