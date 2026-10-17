/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.fabric

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.PayloadTransport
import heckerpowered.bridge.network.ServerboundPayload
import heckerpowered.bridge.requireHost
import heckerpowered.lethal.platform.network.MinecraftPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

class FabricPayloadTransport : PayloadTransport {
    override fun sendToServer(payload: ServerboundPayload<*>) {
        ClientPlayNetworking.send(MinecraftPayload(payload))
    }

    override fun sendToPlayer(player: ServerPlayerAccess, payload: ClientboundPayload<*>) {
        ServerPlayNetworking.send(requireHost<ServerPlayer>(player), MinecraftPayload(payload))
    }

    override fun sendToPlayersTracking(entity: EntityAccess, payload: ClientboundPayload<*>) {
        val nativeEntity = requireHost<Entity>(entity)
        val recipients = PlayerLookup.tracking(nativeEntity).toMutableSet()
        if (nativeEntity is ServerPlayer) recipients.add(nativeEntity)

        val nativePayload = MinecraftPayload(payload)
        recipients.forEach { ServerPlayNetworking.send(it, nativePayload) }
    }
}
