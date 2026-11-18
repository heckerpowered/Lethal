/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.client.MinecraftAccess
import heckerpowered.bridge.adapter.entity.ClientPlayerAccess
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

fun Minecraft.asView(): MinecraftAccess = MinecraftAccess::class.java.cast(this)

fun LocalPlayer.asView(): ClientPlayerAccess = ClientPlayerAccess::class.java.cast(this)
