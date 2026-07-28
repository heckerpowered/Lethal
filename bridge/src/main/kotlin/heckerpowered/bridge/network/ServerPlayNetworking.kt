/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network

import heckerpowered.bridge.adapter.entity.PlayerAccess

object ServerPlayNetworking {
    private val Receivers = PayloadReceiverRegistry<Context>()

    fun <T : ServerboundPayload<T>> registerReceiver(type: Payload.Type<T>, handler: (T, Context) -> Unit) {
        Receivers.register(type, handler)
    }

    fun handle(payload: Payload<*>, context: Context) {
        Receivers.handle(payload, context)
    }

    data class Context(
        val player: PlayerAccess,
    )
}
