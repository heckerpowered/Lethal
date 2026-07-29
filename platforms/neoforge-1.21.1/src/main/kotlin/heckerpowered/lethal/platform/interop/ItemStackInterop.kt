/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.requireAccess
import heckerpowered.bridge.requireHost
import net.minecraft.world.item.ItemStack

fun ItemStackAccess.asHost() = requireHost<ItemStack>(this)

fun ItemStack.asView() = requireAccess<ItemStackAccess>(this)
