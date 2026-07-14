/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.network.ClientPlayNetworking.Context
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.Payload
import heckerpowered.bridge.network.StreamCodecs
import heckerpowered.bridge.network.codec.StreamCodec
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.client.render.DivineShotEffect

class DivineParticlePayload(val isMainHand: Boolean) : ClientboundPayload<DivineParticlePayload> {
    companion object {
        val PayloadId = Constants.identifier("divine_particle")

        @JvmField
        val Type = Payload.Type<DivineParticlePayload>(PayloadId)
        val Codec = StreamCodec.composite(StreamCodecs.Boolean, DivineParticlePayload::isMainHand, ::DivineParticlePayload)
    }

    override val type: Payload.Type<DivineParticlePayload>
        get() = Type

    fun handle(context: Context) {
        DivineShotEffect.play(context.player, isMainHand)
    }
}
