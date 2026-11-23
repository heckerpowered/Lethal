/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.bridge.network.ClientPlayNetworking
import heckerpowered.bridge.network.ServerPlayNetworking
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * Dispatches decoded play payloads in the correct direction. Serverbound dispatch supplies the
 * sending server player; clientbound dispatch supplies the receiving client player.
 */
object MinecraftNetwork {
    fun handleServerbound(payload: MinecraftPayload.Serverbound, player: ServerPlayer) {
        ServerPlayNetworking.handle(payload.payload, ServerPlayNetworking.Context(player.asView()))
    }

    fun handleClientbound(payload: MinecraftPayload.Clientbound, player: Player) {
        ClientPlayNetworking.handle(payload.payload, ClientPlayNetworking.Context(player.asView()))
    }
}
