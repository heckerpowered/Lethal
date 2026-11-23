/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.lethal.platform.network

import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.Payload
import heckerpowered.bridge.network.PayloadTypeRegistry
import heckerpowered.bridge.network.ServerboundPayload
import heckerpowered.bridge.resources.Identifier
import net.minecraft.network.FriendlyByteBuf

/**
 * Native wrapper for a bridge play payload. Separate serverbound and clientbound variants select
 * the matching payload registry and keep the direction visible to the host transport.
 */
sealed class MinecraftPayload<P : Payload<*>>(val payload: P) {
    /**
     * Carries a bridge play payload from a client to the server. Its direction selects the
     * serverbound codec and receiving handler.
     */
    class Serverbound(payload: ServerboundPayload<*>) : MinecraftPayload<ServerboundPayload<*>>(payload)

    /**
     * Carries a bridge play payload from the server to a client. Its direction selects the
     * clientbound codec and receiving handler.
     */
    class Clientbound(payload: ClientboundPayload<*>) : MinecraftPayload<ClientboundPayload<*>>(payload)

    /**
     * Provides the host codecs or payload identifiers for the two play directions. A clientbound
     * message and a serverbound message are not interchangeable on the wire.
     */
    companion object {
        fun encode(message: Serverbound, output: FriendlyByteBuf) = encode(message.payload, output, PayloadTypeRegistry.ServerboundPlay)
        fun encode(message: Clientbound, output: FriendlyByteBuf) = encode(message.payload, output, PayloadTypeRegistry.ClientboundPlay)

        private fun <P : Payload<*>> encode(payload: P, output: FriendlyByteBuf, registry: PayloadTypeRegistry<P>) {
            output.writeUtf(payload.type.id.asString(), Identifier.MAXIMUM_LENGTH)
            registry.encode(MinecraftStreamBuffer(output), payload)
        }

        fun decodeServerbound(input: FriendlyByteBuf): Serverbound = Serverbound(decode(input, PayloadTypeRegistry.ServerboundPlay))
        fun decodeClientbound(input: FriendlyByteBuf): Clientbound = Clientbound(decode(input, PayloadTypeRegistry.ClientboundPlay))

        private fun <P : Payload<*>> decode(input: FriendlyByteBuf, registry: PayloadTypeRegistry<P>): P {
            val identifier = Identifier.parse(input.readUtf(Identifier.MAXIMUM_LENGTH))
            val payload = registry.decode(identifier, MinecraftStreamBuffer(input))
            require(!input.isReadable) { "Payload contains trailing bytes" }
            return payload
        }
    }
}
