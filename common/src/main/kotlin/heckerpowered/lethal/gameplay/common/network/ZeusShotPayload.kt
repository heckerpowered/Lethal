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
import heckerpowered.lethal.gameplay.client.render.ZeusShotEffect

class ZeusShotPayload(val isMainHand: Boolean) : ClientboundPayload<ZeusShotPayload> {
    companion object {
        val PayloadId = Constants.identifier("zeus_shot")

        @JvmField
        val Type = Payload.Type<ZeusShotPayload>(PayloadId)
        val Codec = StreamCodec.composite(StreamCodecs.Boolean, ZeusShotPayload::isMainHand, ::ZeusShotPayload)
    }

    override val type: Payload.Type<ZeusShotPayload>
        get() = Type

    fun handle(context: Context) {
        ZeusShotEffect.play(context.player, isMainHand)
    }
}
