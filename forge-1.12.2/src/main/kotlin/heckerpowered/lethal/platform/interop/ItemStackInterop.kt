/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.lethal.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.platform.adapter.item.HostedItemStackAccess
import net.minecraft.item.ItemStack

object ItemStackInterop {
    @JvmStatic
    fun stack(access: ItemStackAccess): ItemStack {
        if (access is HostedItemStackAccess) return access.stack

        @Suppress("CAST_NEVER_SUCCEEDS")
        return access as? ItemStack ?: error("Unsupported ItemStackAccess implementation: ${access::class.java.name}")
    }

    @JvmStatic
    fun stack(stack: ItemStack): ItemStackAccess {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return stack as? ItemStackAccess ?: HostedItemStackAccess(stack)
    }
}

fun ItemStackAccess.stack(): ItemStack {
    return ItemStackInterop.stack(this)
}

fun ItemStack.stack(): ItemStackAccess {
    return ItemStackInterop.stack(this)
}