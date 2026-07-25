/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.adapter.client.MinecraftAccess
import heckerpowered.bridge.platform.services.ClientPlatform
import net.minecraft.client.Minecraft

class ForgeClientPlatform : ClientPlatform {
    override val minecraft: MinecraftAccess
        get() = Minecraft.getMinecraft() as MinecraftAccess
}
