/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.client.MinecraftAccess
import net.minecraft.client.Minecraft

fun Minecraft.asView(): MinecraftAccess = MinecraftAccess::class.java.cast(this)
