/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.requireAccess
import heckerpowered.bridge.requireHost
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import net.minecraft.item.ItemStack

object ItemStackInterop {
    @JvmStatic
    fun asHost(access: ItemStackAccess): ItemStack = access.asHost()

    @JvmStatic
    fun asView(stack: ItemStack): ItemStackAccess = stack.asView()
}

fun ItemStackAccess.asHost(): ItemStack = requireHost(this)

fun ItemStack.asView(): ItemStackAccess = requireAccess(this)
