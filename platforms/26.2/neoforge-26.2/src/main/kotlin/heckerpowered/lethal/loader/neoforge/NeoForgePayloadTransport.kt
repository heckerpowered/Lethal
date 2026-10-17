/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.neoforge

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.PayloadTransport
import heckerpowered.bridge.network.ServerboundPayload
import heckerpowered.bridge.requireHost
import heckerpowered.lethal.platform.network.MinecraftPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor

class NeoForgePayloadTransport : PayloadTransport {
    override fun sendToServer(payload: ServerboundPayload<*>) {
        NeoForgeClientNetworking.sendToServer(MinecraftPayload(payload))
    }

    override fun sendToPlayer(player: ServerPlayerAccess, payload: ClientboundPayload<*>) {
        PacketDistributor.sendToPlayer(requireHost<ServerPlayer>(player), MinecraftPayload(payload))
    }

    override fun sendToPlayersTracking(entity: EntityAccess, payload: ClientboundPayload<*>) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(requireHost<Entity>(entity), MinecraftPayload(payload))
    }
}
