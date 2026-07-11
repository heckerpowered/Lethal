/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network

import java.util.*

class PayloadReceiverRegistry<C> {
    private val registrations = IdentityHashMap<Payload.Type<*>, PayloadHandlerRegistration<*, C>>()

    fun <T : Payload<T>> register(type: Payload.Type<T>, handler: (T, C) -> Unit) {
        require(type !in registrations) { "Payload handler is already registered: ${type.id}" }
        registrations[type] = PayloadHandlerRegistration(type, handler)
    }

    fun handle(payload: Payload<*>, context: C) {
        val registration = registrations[payload.type] ?: error("Payload handler is not registered: ${payload.type.id}")
        registration.handle(payload, context)
    }

    private class PayloadHandlerRegistration<T : Payload<T>, C>(
        private val type: Payload.Type<T>,
        private val handler: (T, C) -> Unit,
    ) {
        fun handle(payload: Payload<*>, context: C) {
            check(payload.type === type) { "Payload type instance does not match its registered handler" }

            @Suppress("UNCHECKED_CAST")
            handler(payload as T, context)
        }
    }
}