/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network

import heckerpowered.bridge.network.codec.StreamCodec
import java.io.*
import java.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamCodecsTest {
    @Test
    fun varIntEncodingMatchesFixedWireSamples() {
        for ((value, wire) in varIntSamples) {
            assertContentEquals(hexBytes(wire), encode(StreamCodecs.VarInt, value), "Value: $value")
        }
    }

    @Test
    fun varIntDecodingMatchesFixedWireSamples() {
        for ((value, wire) in varIntSamples) {
            val input = ByteArrayStreamBuffer(hexBytes(wire))
            assertEquals(value, StreamCodecs.VarInt.decode(input), "Wire: $wire")
            assertEquals(0, input.readableByteCount)
        }
    }

    @Test
    fun varLongEncodingMatchesFixedWireSamples() {
        for ((value, wire) in varLongSamples) {
            assertContentEquals(hexBytes(wire), encode(StreamCodecs.VarLong, value), "Value: $value")
        }
    }

    @Test
    fun varLongDecodingMatchesFixedWireSamples() {
        for ((value, wire) in varLongSamples) {
            val input = ByteArrayStreamBuffer(hexBytes(wire))
            assertEquals(value, StreamCodecs.VarLong.decode(input), "Wire: $wire")
            assertEquals(0, input.readableByteCount)
        }
    }

    @Test
    fun variableLengthIntegersRoundTripSeededSignedValues() {
        val random = Random(0x5EED)
        repeat(2_048) {
            val integer = random.nextInt()
            val long = random.nextLong()
            assertEquals(integer, StreamCodecs.VarInt.decode(ByteArrayStreamBuffer(encode(StreamCodecs.VarInt, integer))))
            assertEquals(long, StreamCodecs.VarLong.decode(ByteArrayStreamBuffer(encode(StreamCodecs.VarLong, long))))
        }
    }

    @Test
    fun varIntRejectsEveryTruncatedContinuationPrefix() {
        for (byteCount in 0 until 5) {
            val input = ByteArrayStreamBuffer(ByteArray(byteCount) { 0x80.toByte() })
            assertFailsWith<EOFException>("Prefix length: $byteCount") {
                StreamCodecs.VarInt.decode(input)
            }
        }
    }

    @Test
    fun varLongRejectsEveryTruncatedContinuationPrefix() {
        for (byteCount in 0 until 10) {
            val input = ByteArrayStreamBuffer(ByteArray(byteCount) { 0x80.toByte() })
            assertFailsWith<EOFException>("Prefix length: $byteCount") {
                StreamCodecs.VarLong.decode(input)
            }
        }
    }

    @Test
    fun varIntRejectsAnUnterminatedFifthByteWithoutReadingTheNextByte() {
        val input = ByteArrayStreamBuffer(hexBytes("80 80 80 80 80 42"))

        assertFailsWith<IllegalArgumentException> { StreamCodecs.VarInt.decode(input) }

        assertEquals(1, input.readableByteCount)
        assertEquals(0x42.toByte(), input.readByte())
    }

    @Test
    fun varLongRejectsAnUnterminatedTenthByteWithoutReadingTheNextByte() {
        val input = ByteArrayStreamBuffer(hexBytes("80 80 80 80 80 80 80 80 80 80 42"))

        assertFailsWith<IllegalArgumentException> { StreamCodecs.VarLong.decode(input) }

        assertEquals(1, input.readableByteCount)
        assertEquals(0x42.toByte(), input.readByte())
    }

    @Test
    fun adjacentVariableLengthIntegersKeepTheirFieldBoundaries() {
        val input = ByteArrayStreamBuffer(hexBytes("ac 02 ff ff ff ff ff ff ff ff ff 01 42"))

        assertEquals(300, StreamCodecs.VarInt.decode(input))
        assertEquals(-1L, StreamCodecs.VarLong.decode(input))
        assertEquals(0x42.toByte(), input.readByte())
        assertEquals(0, input.readableByteCount)
    }

    @Test
    fun uuidPreservesTheOrderAndBitsOfBothHalves() {
        val value = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
        val wire = hexBytes("00 11 22 33 44 55 66 77 88 99 aa bb cc dd ee ff")

        assertContentEquals(wire, encode(StreamCodecs.Uuid, value))

        val input = ByteArrayStreamBuffer(wire + byteArrayOf(0x42))
        assertEquals(value, StreamCodecs.Uuid.decode(input))
        assertEquals(0x42.toByte(), input.readByte())
        assertEquals(0, input.readableByteCount)
    }

    @Test
    fun byteArraysUseAVariableLengthPrefixAndPreserveEveryByteValue() {
        val value = ByteArray(256) { it.toByte() }
        val wire = hexBytes("80 02") + value
        val codec = StreamCodecs.byteArray(256)

        assertContentEquals(wire, encode(codec, value))

        val input = ByteArrayStreamBuffer(wire + byteArrayOf(0x42))
        assertContentEquals(value, codec.decode(input))
        assertEquals(0x42.toByte(), input.readByte())
        assertEquals(0, input.readableByteCount)
    }

    @Test
    fun zeroByteLimitAcceptsOnlyEmptyArrays() {
        val codec = StreamCodecs.byteArray(0)

        assertContentEquals(byteArrayOf(0), encode(codec, byteArrayOf()))
        assertContentEquals(byteArrayOf(), codec.decode(ByteArrayStreamBuffer(byteArrayOf(0))))
        assertFailsWith<IllegalArgumentException> { encode(codec, byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { codec.decode(ByteArrayStreamBuffer(hexBytes("01 42"))) }
    }

    @Test
    fun oversizedByteArraysAreRejectedBeforeAnythingIsWritten() {
        val output = ByteArrayStreamBuffer()
        output.writeByte(0x42)
        val codec = StreamCodecs.byteArray(2)

        assertFailsWith<IllegalArgumentException> {
            codec.encode(output, byteArrayOf(1, 2, 3))
        }

        assertContentEquals(byteArrayOf(0x42), output.encodedBytes)
    }

    @Test
    fun byteArraysRejectNegativeAndOversizedLengthsBeforeReadingTheirContents() {
        val codec = StreamCodecs.byteArray(2)
        for (prefix in listOf("ff ff ff ff 0f", "ff ff ff ff 07", "03")) {
            val contents = byteArrayOf(1, 2, 3)
            val input = ByteArrayStreamBuffer(hexBytes(prefix) + contents)

            assertFailsWith<IllegalArgumentException>("Prefix: $prefix") { codec.decode(input) }

            assertEquals(contents.size, input.readableByteCount)
        }
    }

    @Test
    fun byteArraysRejectLengthsLargerThanTheRemainingInput() {
        val input = ByteArrayStreamBuffer(hexBytes("03 01 02"))

        assertFailsWith<IllegalArgumentException> { StreamCodecs.byteArray(3).decode(input) }

        assertEquals(2, input.readableByteCount)
    }

    @Test
    fun byteArrayCodecsRejectNegativeLimits() {
        assertFailsWith<IllegalArgumentException> { StreamCodecs.byteArray(-1) }
    }

    @Test
    fun defaultByteArrayLimitAcceptsOneMebibyteAndRejectsAnythingLarger() {
        val value = ByteArray(1_048_576) { it.toByte() }
        val wire = hexBytes("80 80 40") + value

        assertLargeByteArrayEquals(wire, encode(StreamCodecs.ByteArray, value))
        assertLargeByteArrayEquals(value, StreamCodecs.ByteArray.decode(ByteArrayStreamBuffer(wire)))

        assertFailsWith<IllegalArgumentException> {
            encode(StreamCodecs.ByteArray, value + byteArrayOf(0))
        }

        val input = ByteArrayStreamBuffer(hexBytes("81 80 40") + value + byteArrayOf(0))
        assertFailsWith<IllegalArgumentException> { StreamCodecs.ByteArray.decode(input) }
        assertEquals(1_048_577, input.readableByteCount)
    }

    @Test
    fun utf8EncodingPrefixesTheByteCountRatherThanTheStringLength() {
        for ((value, wire) in utf8Samples) {
            assertContentEquals(hexBytes(wire), encode(StreamCodecs.StringUTF8, value), "Value: $value")
        }
    }

    @Test
    fun utf8DecodingMatchesFixedWireSamplesWithoutConsumingTheNextField() {
        for ((value, wire) in utf8Samples) {
            val input = ByteArrayStreamBuffer(hexBytes(wire) + byteArrayOf(0x42))
            assertEquals(value, StreamCodecs.StringUTF8.decode(input), "Wire: $wire")
            assertEquals(1, input.readableByteCount)
            assertEquals(0x42.toByte(), input.readByte())
        }
    }

    @Test
    fun zeroCharacterLimitAcceptsOnlyEmptyStrings() {
        val codec = StreamCodecs.stringUtf8(0)

        assertContentEquals(byteArrayOf(0), encode(codec, ""))
        assertEquals("", codec.decode(ByteArrayStreamBuffer(byteArrayOf(0))))
        assertFailsWith<IllegalArgumentException> { encode(codec, "a") }
        assertFailsWith<IllegalArgumentException> { codec.decode(ByteArrayStreamBuffer(hexBytes("01 61"))) }
    }

    @Test
    fun utf8CharacterLimitsCountUtf16CodeUnitsNotEncodedBytes() {
        val codec = StreamCodecs.stringUtf8(2)

        for ((value, wire) in listOf("é中" to "05 c3 a9 e4 b8 ad", "😀" to "04 f0 9f 98 80")) {
            assertContentEquals(hexBytes(wire), encode(codec, value))
            assertEquals(value, codec.decode(ByteArrayStreamBuffer(hexBytes(wire))))
        }

        val oneCharacterCodec = StreamCodecs.stringUtf8(1)
        assertFailsWith<IllegalArgumentException> { encode(oneCharacterCodec, "😀") }
        assertFailsWith<IllegalArgumentException> {
            oneCharacterCodec.decode(ByteArrayStreamBuffer(hexBytes("04 f0 9f 98 80")))
        }
    }

    @Test
    fun oversizedStringsAreRejectedBeforeAnythingIsWritten() {
        val output = ByteArrayStreamBuffer()
        output.writeByte(0x42)

        assertFailsWith<IllegalArgumentException> {
            StreamCodecs.stringUtf8(2).encode(output, "abc")
        }

        assertContentEquals(byteArrayOf(0x42), output.encodedBytes)
    }

    @Test
    fun utf8DecodingEnforcesTheCharacterLimitEvenWhenTheByteCountFits() {
        val codec = StreamCodecs.stringUtf8(2)
        for (wire in listOf("03 61 62 63", "05 f0 9f 98 80 61")) {
            assertFailsWith<IllegalArgumentException>("Wire: $wire") {
                codec.decode(ByteArrayStreamBuffer(hexBytes(wire)))
            }
        }
    }

    @Test
    fun utf8RejectsNegativeAndOversizedByteCountsBeforeReadingTheirContents() {
        val codec = StreamCodecs.stringUtf8(1)
        for (prefix in listOf("ff ff ff ff 0f", "ff ff ff ff 07", "05")) {
            val contents = hexBytes("61 62 63 64 65")
            val input = ByteArrayStreamBuffer(hexBytes(prefix) + contents)

            assertFailsWith<IllegalArgumentException>("Prefix: $prefix") { codec.decode(input) }

            assertEquals(contents.size, input.readableByteCount)
        }
    }

    @Test
    fun utf8RejectsByteCountsLargerThanTheRemainingInput() {
        val input = ByteArrayStreamBuffer(hexBytes("03 c3 a9"))

        assertFailsWith<IllegalArgumentException> { StreamCodecs.stringUtf8(3).decode(input) }

        assertEquals(2, input.readableByteCount)
    }

    @Test
    fun lengthDelimitedCodecsRejectTruncatedLengthPrefixes() {
        for (codec in listOf(StreamCodecs.ByteArray, StreamCodecs.StringUTF8)) {
            for (prefix in listOf(byteArrayOf(), hexBytes("80"), hexBytes("80 80"))) {
                assertFailsWith<EOFException> { codec.decode(ByteArrayStreamBuffer(prefix)) }
            }
        }
    }

    @Test
    fun utf8RejectsMalformedSequencesInsteadOfReplacingThem() {
        val malformedSequences = listOf(
            "80",
            "c2",
            "c0 af",
            "e2 28 a1",
            "e2 82",
            "ed a0 80",
            "f0 9f 98",
            "f4 90 80 80",
            "f5 80 80 80",
            "ff",
        )

        for (wire in malformedSequences) {
            val bytes = hexBytes(wire)
            val input = ByteArrayStreamBuffer(byteArrayOf(bytes.size.toByte()) + bytes)
            assertFailsWith<IllegalArgumentException>("Malformed UTF-8: $wire") {
                StreamCodecs.StringUTF8.decode(input)
            }
        }
    }

    @Test
    fun utf8CodecsRejectLimitsWhoseByteBudgetWouldOverflow() {
        assertFailsWith<IllegalArgumentException> { StreamCodecs.stringUtf8(-1) }
        assertFailsWith<IllegalArgumentException> { StreamCodecs.stringUtf8(Int.MAX_VALUE / 4 + 1) }
        assertFailsWith<IllegalArgumentException> { StreamCodecs.stringUtf8(Int.MAX_VALUE) }

        val largestLimit = StreamCodecs.stringUtf8(Int.MAX_VALUE / 4)
        assertEquals("", largestLimit.decode(ByteArrayStreamBuffer(byteArrayOf(0))))
    }

    @Test
    fun defaultUtf8LimitAccepts32767CodeUnitsAndRejectsTheNextOne() {
        val value = "a".repeat(32_767)
        val wire = hexBytes("ff ff 01") + value.toByteArray(Charsets.UTF_8)

        assertLargeByteArrayEquals(wire, encode(StreamCodecs.StringUTF8, value))
        assertEquals(value, StreamCodecs.StringUTF8.decode(ByteArrayStreamBuffer(wire)))
        assertFailsWith<IllegalArgumentException> { encode(StreamCodecs.StringUTF8, value + "a") }
        assertFailsWith<IllegalArgumentException> {
            StreamCodecs.StringUTF8.decode(ByteArrayStreamBuffer(hexBytes("80 80 02") + (value + "a").toByteArray(Charsets.UTF_8)))
        }
    }

    private fun <Value> encode(codec: StreamCodec<StreamBuffer, Value>, value: Value): ByteArray {
        val output = ByteArrayStreamBuffer()
        codec.encode(output, value)
        return output.encodedBytes
    }

    private fun hexBytes(text: String): ByteArray {
        return text.split(' ').map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun assertLargeByteArrayEquals(expected: ByteArray, actual: ByteArray) {
        // Keep boundary-test failures from printing entire megabyte-sized payloads.
        assertEquals(expected.size, actual.size, "Byte count")
        val differingIndex = expected.indices.firstOrNull { expected[it] != actual[it] } ?: return
        assertEquals(expected[differingIndex], actual[differingIndex], "Byte at index $differingIndex")
    }

    private val varIntSamples = listOf(
        0 to "00",
        1 to "01",
        127 to "7f",
        128 to "80 01",
        255 to "ff 01",
        16_383 to "ff 7f",
        16_384 to "80 80 01",
        2_097_151 to "ff ff 7f",
        2_097_152 to "80 80 80 01",
        268_435_455 to "ff ff ff 7f",
        268_435_456 to "80 80 80 80 01",
        Int.MAX_VALUE to "ff ff ff ff 07",
        Int.MIN_VALUE to "80 80 80 80 08",
        -1 to "ff ff ff ff 0f",
    )

    private val varLongSamples = listOf(
        0L to "00",
        1L to "01",
        127L to "7f",
        128L to "80 01",
        (1L shl 14) - 1 to "ff 7f",
        (1L shl 14) to "80 80 01",
        (1L shl 21) - 1 to "ff ff 7f",
        (1L shl 21) to "80 80 80 01",
        (1L shl 28) - 1 to "ff ff ff 7f",
        (1L shl 28) to "80 80 80 80 01",
        (1L shl 35) - 1 to "ff ff ff ff 7f",
        (1L shl 35) to "80 80 80 80 80 01",
        (1L shl 42) - 1 to "ff ff ff ff ff 7f",
        (1L shl 42) to "80 80 80 80 80 80 01",
        (1L shl 49) - 1 to "ff ff ff ff ff ff 7f",
        (1L shl 49) to "80 80 80 80 80 80 80 01",
        (1L shl 56) - 1 to "ff ff ff ff ff ff ff 7f",
        (1L shl 56) to "80 80 80 80 80 80 80 80 01",
        Long.MAX_VALUE to "ff ff ff ff ff ff ff ff 7f",
        Long.MIN_VALUE to "80 80 80 80 80 80 80 80 80 01",
        -1L to "ff ff ff ff ff ff ff ff ff 01",
    )

    private val utf8Samples = listOf(
        "" to "00",
        "A" to "01 41",
        "é" to "02 c3 a9",
        "中" to "03 e4 b8 ad",
        "😀" to "04 f0 9f 98 80",
        "A\u0000é中😀" to "0b 41 00 c3 a9 e4 b8 ad f0 9f 98 80",
    )
}

private class ByteArrayStreamBuffer(bytes: ByteArray = byteArrayOf()) : StreamBuffer {
    private val input = DataInputStream(ByteArrayInputStream(bytes))
    private val outputBytes = ByteArrayOutputStream()
    private val output = DataOutputStream(outputBytes)

    val encodedBytes: ByteArray
        get() = outputBytes.toByteArray()

    override val readableByteCount: Int
        get() = input.available()

    override fun writeBoolean(value: Boolean) = output.writeBoolean(value)
    override fun writeByte(value: Byte) = output.writeByte(value.toInt())
    override fun writeShort(value: Short) = output.writeShort(value.toInt())
    override fun writeInt(value: Int) = output.writeInt(value)
    override fun writeLong(value: Long) = output.writeLong(value)
    override fun writeFloat(value: Float) = output.writeFloat(value)
    override fun writeDouble(value: Double) = output.writeDouble(value)
    override fun writeBytes(value: ByteArray) = output.write(value)

    override fun readBoolean(): Boolean = input.readBoolean()
    override fun readByte(): Byte = input.readByte()
    override fun readShort(): Short = input.readShort()
    override fun readInt(): Int = input.readInt()
    override fun readLong(): Long = input.readLong()
    override fun readFloat(): Float = input.readFloat()
    override fun readDouble(): Double = input.readDouble()
    override fun readBytes(byteCount: Int): ByteArray = ByteArray(byteCount).also { input.readFully(it) }
}
