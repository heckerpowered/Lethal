/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.forge

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.PayloadTransport
import heckerpowered.bridge.network.ServerboundPayload
import heckerpowered.bridge.requireHost
import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.interop.asHost
import heckerpowered.lethal.platform.network.MinecraftNetwork
import heckerpowered.lethal.platform.network.MinecraftPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraftforge.network.Channel
import net.minecraftforge.network.ChannelBuilder
import net.minecraftforge.network.PacketDistributor

class ForgePayloadTransport : PayloadTransport {
    override fun sendToServer(payload: ServerboundPayload<*>) {
        ForgeClient.sendToServer(MinecraftPayload(payload), channel)
    }

    override fun sendToPlayer(player: ServerPlayerAccess, payload: ClientboundPayload<*>) {
        channel.send(MinecraftPayload(payload), PacketDistributor.PLAYER.with(requireHost<ServerPlayer>(player)))
    }

    override fun sendToPlayersTracking(entity: EntityAccess, payload: ClientboundPayload<*>) {
        channel.send(MinecraftPayload(payload), PacketDistributor.TRACKING_ENTITY_AND_SELF.with(requireHost<Entity>(entity)))
    }

    companion object {
        private lateinit var channel: Channel<CustomPacketPayload>

        fun initialize() {
            check(!::channel.isInitialized) { "Lethal's Forge payload channel is already initialized" }
            channel = ChannelBuilder.named(Constants.identifier("play").asHost())
                .networkProtocolVersion(1)
                .payloadChannel()
                .play()
                .serverbound()
                .addMain(MinecraftPayload.ServerboundType, MinecraftPayload.ServerboundCodec) { payload, context ->
                    val player = context.sender ?: return@addMain
                    MinecraftNetwork.handleServerbound(payload, player)
                }
                .clientbound()
                .addMain(MinecraftPayload.ClientboundType, MinecraftPayload.ClientboundCodec) { payload, _ ->
                    ForgeClient.handlePayload(payload)
                }
                .build()
        }
    }
}
