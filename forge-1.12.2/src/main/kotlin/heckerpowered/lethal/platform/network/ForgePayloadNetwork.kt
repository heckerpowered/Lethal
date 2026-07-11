/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.lethal.Constants
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.relauncher.Side

object ForgePayloadNetwork {
    private val Channel = NetworkRegistry.INSTANCE.newSimpleChannel(Constants.MOD_ID)

    init {
        Channel.registerMessage(ClientboundForgePacketEnvelopeHandler(), ClientboundForgePacketEnvelope::class.java, 0, Side.CLIENT)
        Channel.registerMessage(ServerboundForgePacketEnvelopeHandler(), ServerboundForgePacketEnvelope::class.java, 1, Side.SERVER)
    }
}
