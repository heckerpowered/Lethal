/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.network.ClientPlayNetworking.Context
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.Payload
import heckerpowered.bridge.network.StreamBuffer
import heckerpowered.bridge.network.codec.StreamCodec
import heckerpowered.lethal.Constants

object DivineParticlePayload : ClientboundPayload<DivineParticlePayload> {
    val PayloadId = Constants.identifier("divine_particle")

    @JvmField
    val Type = Payload.Type<DivineParticlePayload>(PayloadId)
    val Codec = StreamCodec.unit<StreamBuffer, DivineParticlePayload>(DivineParticlePayload)

    override val type: Payload.Type<DivineParticlePayload>
        get() = Type

    fun handle(payload: DivineParticlePayload, context: Context) {
        
    }
}