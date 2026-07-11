/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.PayloadTransport
import heckerpowered.bridge.network.ServerboundPayload
import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.interop.ObjectInterop
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
        val player = ObjectInterop.entity<EntityPlayerMP>(player)
        channel.sendTo(ClientboundForgePayloadEnvelope(payload), player)
    }
}
