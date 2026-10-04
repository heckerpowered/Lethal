/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.entity.player.EntityPlayer

object PlayerInventoryAccessImpl {
    @JvmStatic
    fun carriedStacks(player: EntityPlayer): Sequence<ItemStackAccess> = sequence {
        for (stack in player.inventory.mainInventory) yield(stack.asView())
        for (stack in player.inventory.offHandInventory) yield(stack.asView())
    }
}
