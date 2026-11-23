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
import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.interop.asHost
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Native wrapper for a bridge play payload. Separate serverbound and clientbound variants select
 * the matching payload registry and keep the direction visible to the host transport.
 */
sealed class MinecraftPayload<P : Payload<*>>(val payload: P) : CustomPacketPayload {
    /**
     * Carries a bridge play payload from a client to the server. Its direction selects the
     * serverbound codec and receiving handler.
     */
    class Serverbound(payload: ServerboundPayload<*>) : MinecraftPayload<ServerboundPayload<*>>(payload) {
        override fun type(): CustomPacketPayload.Type<Serverbound> = ServerboundType
    }

    /**
     * Carries a bridge play payload from the server to a client. Its direction selects the
     * clientbound codec and receiving handler.
     */
    class Clientbound(payload: ClientboundPayload<*>) : MinecraftPayload<ClientboundPayload<*>>(payload) {
        override fun type(): CustomPacketPayload.Type<Clientbound> = ClientboundType
    }

    /**
     * Provides the host codecs or payload identifiers for the two play directions. A clientbound
     * message and a serverbound message are not interchangeable on the wire.
     */
    companion object {
        val ServerboundType = CustomPacketPayload.Type<Serverbound>(Constants.identifier("serverbound_play").asHost())
        val ClientboundType = CustomPacketPayload.Type<Clientbound>(Constants.identifier("clientbound_play").asHost())
        val ServerboundCodec: StreamCodec<RegistryFriendlyByteBuf, Serverbound> = codec(PayloadTypeRegistry.ServerboundPlay, ::Serverbound)
        val ClientboundCodec: StreamCodec<RegistryFriendlyByteBuf, Clientbound> = codec(PayloadTypeRegistry.ClientboundPlay, ::Clientbound)

        private fun <P : Payload<*>, T : MinecraftPayload<P>> codec(registry: PayloadTypeRegistry<P>, wrap: (P) -> T): StreamCodec<RegistryFriendlyByteBuf, T> = object : StreamCodec<RegistryFriendlyByteBuf, T> {
            override fun encode(output: RegistryFriendlyByteBuf, value: T) {
                output.writeUtf(value.payload.type.id.asString(), Identifier.MAXIMUM_LENGTH)
                registry.encode(MinecraftStreamBuffer(output), value.payload)
            }

            override fun decode(input: RegistryFriendlyByteBuf): T {
                val identifier = Identifier.parse(input.readUtf(Identifier.MAXIMUM_LENGTH))
                val payload = registry.decode(identifier, MinecraftStreamBuffer(input))
                require(!input.isReadable) { "Payload contains trailing bytes" }
                return wrap(payload)
            }
        }
    }
}
