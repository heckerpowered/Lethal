/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.network.Payload
import heckerpowered.bridge.network.ServerPlayNetworking
import heckerpowered.bridge.network.ServerboundPayload
import heckerpowered.bridge.network.StreamCodecs
import heckerpowered.bridge.network.codec.StreamCodec
import heckerpowered.lethal.Constants

class FireStatePayload(val state: Boolean) : ServerboundPayload<FireStatePayload> {
    companion object {
        val PayloadId = Constants.identifier("fire_state")
        val Type = Payload.Type<FireStatePayload>(PayloadId)
        val Codec = StreamCodec.composite(StreamCodecs.Boolean, FireStatePayload::state, ::FireStatePayload)
    }

    override val type: Payload.Type<FireStatePayload>
        get() = Type

    fun handle(context: ServerPlayNetworking.Context) {

    }
}