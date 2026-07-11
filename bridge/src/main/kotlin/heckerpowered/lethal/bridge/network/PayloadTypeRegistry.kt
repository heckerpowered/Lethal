/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.network

import heckerpowered.lethal.bridge.network.codec.StreamCodec
import heckerpowered.lethal.bridge.resources.Identifier
import heckerpowered.lethal.bridge.security.UnsafeUntrustedAccess
import heckerpowered.lethal.bridge.security.Untrusted

data class PayloadDefinition<T : Payload<T>>(
    val type: Payload.Type<T>,
    val codec: StreamCodec<StreamBuffer, T>,
) {
    fun decode(input: StreamBuffer): T = codec.decode(input)
    fun encode(output: StreamBuffer, payload: T) = codec.encode(output, payload)
}

class PayloadTypeRegistry {
    private val registrations = LinkedHashMap<Identifier, PayloadRegistration<*>>()

    fun <T : Payload<T>> register(type: Payload.Type<T>, codec: StreamCodec<StreamBuffer, T>): PayloadDefinition<T> {
        val typeId = type.id
        require(typeId !in registrations) { "Payload type is already registered: $typeId" }

        val definition = PayloadDefinition(type, codec)
        registrations[typeId] = PayloadRegistration(definition)
        return definition
    }

    fun isRegistered(type: Payload.Type<*>): Boolean {
        return type.id in registrations
    }

    fun decode(typeId: Identifier, input: StreamBuffer): Payload<*> {
        val registration = registrations[typeId] ?: error("Payload type is not registered: $typeId")
        return registration.decode(input)
    }

    @OptIn(UnsafeUntrustedAccess::class)
    fun decode(typeId: Untrusted<Identifier>, input: StreamBuffer): Payload<*> {
        val registration = registrations[typeId.unsafeUnwrap()] ?: error("Payload type is not registered: (untrusted identifier)")
        return registration.decode(input)
    }

    fun encode(output: StreamBuffer, payload: Payload<*>) {
        val typeId = payload.type.id
        val registration = registrations[typeId] ?: error("Payload type is not registered: $typeId")
        registration.encode(output, payload)
    }

    private class PayloadRegistration<T : Payload<T>>(
        private val definition: PayloadDefinition<T>,
    ) {
        fun decode(input: StreamBuffer): Payload<*> {
            val payload = definition.decode(input)
            require(payload.type === definition.type) { "Codec for ${definition.type.id} decoded payload with type ${payload.type.id}" }
            return payload
        }

        fun encode(output: StreamBuffer, payload: Payload<*>) {
            require(payload.type === definition.type) { "Payload declares an unregistered type instance for ${payload.type.id}" }

            @Suppress("UNCHECKED_CAST")
            definition.encode(output, payload as T)
        }
    }
}
