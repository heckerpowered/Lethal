/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.platform.interop.item
import net.minecraft.item.ItemStack

/**
 * Wrapper used when a native stack does not directly expose [ItemStackAccess].
 */
class HostedItemStackAccess(
    val stack: ItemStack,
) : ItemStackAccess {
    override val item: ItemAccess
        get() = stack.item.item()

    override var count: Int
        get() = stack.count
        set(value) {
            stack.setCount(value)
        }

    override var damagePoints: Int
        get() = stack.getItemDamage()
        set(value) {
            stack.setItemDamage(value)
        }

    override val maxStackCount: Int
        get() = stack.maxStackSize

    override val maxDamagePoints: Int
        get() = stack.maxDamage

    override val isEmpty: Boolean
        get() = stack.isEmpty

    override val isDamageable: Boolean
        get() = stack.isItemStackDamageable

    override val isDamaged: Boolean
        get() = stack.isItemDamaged
}
