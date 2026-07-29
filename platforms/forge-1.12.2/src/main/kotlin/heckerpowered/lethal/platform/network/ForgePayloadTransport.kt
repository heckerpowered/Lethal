/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.PayloadTransport
import heckerpowered.bridge.network.ServerboundPayload
import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.interop.asHost
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.relauncher.Side

class ForgePayloadTransport : PayloadTransport {
    private val channel = NetworkRegistry.INSTANCE.newSimpleChannel(Constants.MOD_ID)

    init {
        channel.registerMessage(ClientboundForgePacketEnvelopeHandler(), ClientboundForgePayloadEnvelope::class.java, 0, Side.CLIENT)
        channel.registerMessage(ServerboundForgePacketEnvelopeHandler(), ServerboundForgePayloadEnvelope::class.java, 1, Side.SERVER)
    }

    override fun sendToServer(payload: ServerboundPayload<*>) {
        channel.sendToServer(ServerboundForgePayloadEnvelope(payload))
    }

    override fun sendToPlayer(player: ServerPlayerAccess, payload: ClientboundPayload<*>) {
        val player = player.asHost() as EntityPlayerMP
        channel.sendTo(ClientboundForgePayloadEnvelope(payload), player)
    }

    override fun sendToPlayersTracking(entity: EntityAccess, payload: ClientboundPayload<*>) {
        val nativeEntity = entity.asHost()
        channel.sendToAllTracking(ClientboundForgePayloadEnvelope(payload), nativeEntity)
        if (nativeEntity is EntityPlayerMP) {
            channel.sendTo(ClientboundForgePayloadEnvelope(payload), nativeEntity)
        }
    }
}
