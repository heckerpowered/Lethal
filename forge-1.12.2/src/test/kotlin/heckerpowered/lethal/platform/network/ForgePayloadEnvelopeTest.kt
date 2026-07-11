/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.Payload
import heckerpowered.bridge.network.PayloadRegistrar
import heckerpowered.bridge.network.StreamCodecs
import heckerpowered.bridge.network.codec.StreamCodec
import heckerpowered.bridge.resources.IdentifierProvider
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals

class ForgePayloadEnvelopeTest {
    @Test
    fun envelopeRoundTripsARegisteredPacketThroughByteBuf() {
        PayloadRegistrar.registerClientbound(TestClientboundPayload.type, TestClientboundPayload.codec)
        val packet = TestClientboundPayload(42, 120L)
        val buffer = Unpooled.buffer()

        try {
            ClientboundForgePayloadEnvelope(packet).toBytes(buffer)

            val decodedEnvelope = ClientboundForgePayloadEnvelope()
            decodedEnvelope.fromBytes(buffer)

            assertEquals(packet, decodedEnvelope.payload)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }

    private data class TestClientboundPayload(
        val entityId: Int,
        val channelTimeTicks: Long,
    ) : ClientboundPayload<TestClientboundPayload> {
        override val type: Payload.Type<TestClientboundPayload>
            get() = Companion.type

        companion object {
            val type = Payload.Type<TestClientboundPayload>(
                IdentifierProvider.Freestanding.identifier("lethal", "test_clientbound"),
            )

            val codec = StreamCodec.composite(
                StreamCodecs.Int, TestClientboundPayload::entityId,
                StreamCodecs.Long, TestClientboundPayload::channelTimeTicks,
                ::TestClientboundPayload,
            )
        }
    }
}
