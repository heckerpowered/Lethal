/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.network.StreamCodecs
import heckerpowered.lethal.gameplay.common.network.ZeusChainPayload
import heckerpowered.lethal.gameplay.common.network.ZeusChainSegment
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZeusChainPayloadTest {
    @Test
    fun codecPreservesBranchingChainSegments() {
        val rootPosition = Geometry.vector(1.25, -2.5, 3.75)
        val payload = ZeusChainPayload(
            listOf(
                ZeusChainSegment(rootPosition, Geometry.vector(-4.5, 5.25, 6.0)),
                ZeusChainSegment(rootPosition, Geometry.vector(7.5, 8.25, -9.0)),
            ),
        )
        val nativeBuffer = Unpooled.buffer()

        try {
            val streamBuffer = ByteBufStreamBuffer(nativeBuffer)
            ZeusChainPayload.Codec.encode(streamBuffer, payload)
            val decodedPayload = ZeusChainPayload.Codec.decode(streamBuffer)

            assertEquals(expected = payload.chainSegments.size, actual = decodedPayload.chainSegments.size)
            payload.chainSegments.zip(decodedPayload.chainSegments).forEach { (expected, actual) ->
                assertVectorEquals(expected.startPosition, actual.startPosition)
                assertVectorEquals(expected.endPosition, actual.endPosition)
            }
            assertEquals(expected = 0, actual = nativeBuffer.readableBytes())
        } finally {
            nativeBuffer.release()
        }
    }

    @Test
    fun payloadRequiresAtLeastOneChainSegment() {
        assertFailsWith<IllegalArgumentException> {
            ZeusChainPayload(emptyList())
        }
    }

    @Test
    fun codecRejectsSegmentCountBeyondReadableData() {
        val nativeBuffer = Unpooled.buffer()

        try {
            val streamBuffer = ByteBufStreamBuffer(nativeBuffer)
            StreamCodecs.VarInt.encode(streamBuffer, 2)

            assertFailsWith<IllegalArgumentException> {
                ZeusChainPayload.Codec.decode(streamBuffer)
            }
        } finally {
            nativeBuffer.release()
        }
    }

    private fun assertVectorEquals(expected: VectorView, actual: VectorView) {
        assertEquals(expected = expected.x, actual = actual.x)
        assertEquals(expected = expected.y, actual = actual.y)
        assertEquals(expected = expected.z, actual = actual.z)
    }
}
