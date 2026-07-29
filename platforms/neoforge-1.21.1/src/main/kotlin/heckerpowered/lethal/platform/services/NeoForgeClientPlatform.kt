/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.adapter.client.MinecraftAccess
import heckerpowered.bridge.platform.services.ClientPlatform
import heckerpowered.bridge.requireAccess
import net.minecraft.client.Minecraft

class NeoForgeClientPlatform : ClientPlatform {
    override val minecraft: MinecraftAccess
        get() = requireAccess<MinecraftAccess>(Minecraft.getInstance())
}
