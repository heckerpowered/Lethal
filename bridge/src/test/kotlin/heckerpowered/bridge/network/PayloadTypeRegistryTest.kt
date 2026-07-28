/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network

import heckerpowered.bridge.network.codec.StreamCodec
import heckerpowered.bridge.resources.IdentifierProvider
import java.nio.ByteBuffer
import java.util.*
import kotlin.test.*

class PayloadTypeRegistryTest {
    @Test
    fun registeredPayloadRoundTrips() {
        val registry = PayloadTypeRegistry()
        val payload = TestPayload(UUID(12L, 34L), 27)
        val buffer = ByteBufferStreamBuffer()

        assertFalse(registry.isRegistered(TestPayload.type))

        registry.register(TestPayload.type, TestPayload.codec)
        registry.encode(buffer, payload)
        buffer.prepareForReading()

        assertEquals(expected = payload, actual = registry.decode(TestPayload.type.id, buffer))
        assertTrue(registry.isRegistered(TestPayload.type))
        assertEquals(expected = 0, actual = buffer.readableByteCount)
    }

    @Test
    fun duplicatePayloadTypeIdsAreRejected() {
        val registry = PayloadTypeRegistry()
        val duplicateType = Payload.Type<TestPayload>(TestPayload.type.id)

        registry.register(TestPayload.type, TestPayload.codec)

        assertFailsWith<IllegalArgumentException> { registry.register(duplicateType, TestPayload.codec) }
    }

    private data class TestPayload(
        val magicUuid: UUID,
        val entityId: Int,
    ) : ClientboundPayload<TestPayload> {
        override val type
            get() = Companion.type

        companion object {
            val type = Payload.Type<TestPayload>(
                IdentifierProvider.Freestanding.identifier("test", "payload"),
            )

            val codec = StreamCodec.composite(
                StreamCodecs.Uuid, TestPayload::magicUuid,
                StreamCodecs.Int, TestPayload::entityId,
                ::TestPayload,
            )
        }
    }
}

class StreamCodecsTest {
    @Test
    fun varIntAndVarLongRoundTripSignedValues() {
        val integerValues = listOf(0, 1, 127, 128, Int.MAX_VALUE, -1, Int.MIN_VALUE)
        val longValues = listOf(0L, 1L, 127L, 128L, Long.MAX_VALUE, -1L, Long.MIN_VALUE)
        val buffer = ByteBufferStreamBuffer()

        for (value in integerValues) {
            StreamCodecs.VarInt.encode(buffer, value)
        }
        for (value in longValues) {
            StreamCodecs.VarLong.encode(buffer, value)
        }

        buffer.prepareForReading()

        val decodedIntegers = List(integerValues.size) { StreamCodecs.VarInt.decode(buffer) }
        val decodedLongs = List(longValues.size) { StreamCodecs.VarLong.decode(buffer) }

        assertEquals(expected = integerValues, actual = decodedIntegers)
        assertEquals(expected = longValues, actual = decodedLongs)
        assertEquals(expected = 0, actual = buffer.readableByteCount)
    }

    @Test
    fun boundedCodecsRoundTripUtf8AndByteArrays() {
        val stringCodec = StreamCodecs.stringUtf8(32)
        val byteArrayCodec = StreamCodecs.byteArray(8)
        val string = "沼气炸弹"
        val bytes = byteArrayOf(1, 1, 4, 5, 1, 4)
        val buffer = ByteBufferStreamBuffer()

        stringCodec.encode(buffer, string)
        byteArrayCodec.encode(buffer, bytes)
        buffer.prepareForReading()

        assertEquals(expected = string, actual = stringCodec.decode(buffer))
        assertContentEquals(bytes, byteArrayCodec.decode(buffer))
        assertEquals(expected = 0, actual = buffer.readableByteCount)
    }

    @Test
    fun boundedCodecsRejectOversizedValues() {
        val stringCodec = StreamCodecs.stringUtf8(4)
        val byteArrayCodec = StreamCodecs.byteArray(4)

        assertFailsWith<IllegalArgumentException> { stringCodec.encode(ByteBufferStreamBuffer(), "oversized") }
        assertFailsWith<IllegalArgumentException> { byteArrayCodec.encode(ByteBufferStreamBuffer(), ByteArray(5)) }
    }
}

private class ByteBufferStreamBuffer(capacity: Int = 4096) : StreamBuffer {
    private val buffer = ByteBuffer.allocate(capacity)

    override val readableByteCount: Int
        get() = buffer.remaining()

    fun prepareForReading() {
        buffer.flip()
    }

    override fun writeBoolean(value: Boolean) {
        writeByte((if (value) 1 else 0).toByte())
    }

    override fun writeByte(value: Byte) {
        buffer.put(value)
    }

    override fun writeShort(value: Short) {
        buffer.putShort(value)
    }

    override fun writeInt(value: Int) {
        buffer.putInt(value)
    }

    override fun writeLong(value: Long) {
        buffer.putLong(value)
    }

    override fun writeFloat(value: Float) {
        buffer.putFloat(value)
    }

    override fun writeDouble(value: Double) {
        buffer.putDouble(value)
    }

    override fun writeBytes(value: ByteArray) {
        buffer.put(value)
    }

    override fun readBoolean(): Boolean {
        return readByte().toInt() != 0
    }

    override fun readByte(): Byte {
        return buffer.get()
    }

    override fun readShort(): Short {
        return buffer.short
    }

    override fun readInt(): Int {
        return buffer.int
    }

    override fun readLong(): Long {
        return buffer.long
    }

    override fun readFloat(): Float {
        return buffer.float
    }

    override fun readDouble(): Double {
        return buffer.double
    }

    override fun readBytes(byteCount: Int): ByteArray {
        val bytes = ByteArray(byteCount)
        buffer.get(bytes)
        return bytes
    }
}
