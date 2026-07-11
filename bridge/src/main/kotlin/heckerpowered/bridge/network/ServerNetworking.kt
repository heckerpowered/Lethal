/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network

import heckerpowered.bridge.adapter.entity.PlayerAccess

object ServerNetworking {
    private val receivers = PayloadReceiverRegistry<Context>()

    fun <T : ClientboundPayload<T>> registerReceiver(type: Payload.Type<T>, handler: (T, Context) -> Unit) {
        receivers.register(type, handler)
    }

    fun handle(payload: Payload<*>, context: Context) {
        receivers.handle(payload, context)
    }

    data class Context(
        val player: PlayerAccess,
    )
}