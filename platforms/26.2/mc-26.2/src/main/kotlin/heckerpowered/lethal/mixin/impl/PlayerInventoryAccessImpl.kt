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
    fun getCarriedStacks(player: Player): Sequence<ItemStackAccess> = sequence {
        for (stack in player.inventory.nonEquipmentItems) yield(stack.asView())
        yield(player.offhandItem.asView())
    }
}
