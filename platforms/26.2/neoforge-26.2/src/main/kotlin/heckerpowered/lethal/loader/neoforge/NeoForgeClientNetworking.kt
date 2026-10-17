/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.neoforge

import heckerpowered.lethal.platform.network.MinecraftPayload
import net.neoforged.neoforge.client.network.ClientPacketDistributor

internal object NeoForgeClientNetworking {
    fun sendToServer(payload: MinecraftPayload) {
        ClientPacketDistributor.sendToServer(payload)
    }
}
