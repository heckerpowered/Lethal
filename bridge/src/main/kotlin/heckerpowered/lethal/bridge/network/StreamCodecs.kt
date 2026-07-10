/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.network

import heckerpowered.lethal.bridge.network.codec.StreamCodec
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.*

object StreamCodecs {
    val Boolean = StreamCodec.of(PacketBuffer::writeBoolean, PacketBuffer::readBoolean)
    val Byte = StreamCodec.of(PacketBuffer::writeByte, PacketBuffer::readByte)
    val Short = StreamCodec.of(PacketBuffer::writeShort, PacketBuffer::readShort)
    val Int = StreamCodec.of(PacketBuffer::writeInt, PacketBuffer::readInt)
    val Long = StreamCodec.of(PacketBuffer::writeLong, PacketBuffer::readLong)
    val Float = StreamCodec.of(PacketBuffer::writeFloat, PacketBuffer::readFloat)
    val Double = StreamCodec.of(PacketBuffer::writeDouble, PacketBuffer::readDouble)

    val VarInt = StreamCodec.of(::writeVarInt, ::readVarInt)
    val VarLong = StreamCodec.of(::writeVarLong, ::readVarLong)

    val Uuid = StreamCodec.of<PacketBuffer, UUID>(
        encoder = { output, value ->
            output.writeLong(value.mostSignificantBits)
            output.writeLong(value.leastSignificantBits)
        },
        decoder = { input ->
            UUID(input.readLong(), input.readLong())
        },
    )

    val ByteArray = byteArray(1_048_576)
    val StringUTF8 = stringUtf8(32_767)

    fun byteArray(maximumByteCount: Int): StreamCodec<PacketBuffer, ByteArray> {
        require(maximumByteCount >= 0) { "Maximum byte count must not be negative" }

        return StreamCodec.of(
            encoder = { output, value ->
                require(value.size <= maximumByteCount) { "Byte array size ${value.size} exceeds maximum $maximumByteCount" }

                VarInt.encode(output, value.size)
                output.writeBytes(value)
            },
            decoder = { input ->
                val byteCount = VarInt.decode(input)
                require(byteCount >= 0) { "Byte array size must not be negative: $byteCount" }
                require(byteCount <= maximumByteCount) { "Byte array size $byteCount exceeds maximum $maximumByteCount" }
                require(byteCount <= input.readableByteCount) { "Byte array size $byteCount exceeds readable bytes ${input.readableByteCount}"}

                input.readBytes(byteCount)
            },
        )
    }

    fun stringUtf8(maximumCharacterCount: Int): StreamCodec<PacketBuffer, String> {
        require(maximumCharacterCount >= 0) { "Maximum character count must not be negative" }
        require(maximumCharacterCount <= Integer.MAX_VALUE / 4) { "Maximum character count is too large: $maximumCharacterCount" }

        val maximumByteCount = maximumCharacterCount * 4
        return StreamCodec.of(
            encoder = { output, value ->
                require(value.length <= maximumCharacterCount) { "String length ${value.length} exceeds maximum $maximumCharacterCount" }

                val bytes = value.toByteArray(Charsets.UTF_8)
                require(bytes.size <= maximumByteCount) { "UTF-8 byte count ${bytes.size} exceeds maximum $maximumByteCount" }

                VarInt.encode(output, bytes.size)
                output.writeBytes(bytes)
            },
            decoder = { input ->
                val byteCount = VarInt.decode(input)
                require(byteCount >= 0) { "UTF-8 byte count must not be negative: $byteCount" }
                require(byteCount <= maximumByteCount) { "UTF-8 byte count $byteCount exceeds maximum $maximumByteCount" }
                require(byteCount <= input.readableByteCount) { "UTF-8 byte count $byteCount exceeds readable bytes ${input.readableByteCount}" }

                val value = decodeUtf8(input.readBytes(byteCount))
                require(value.length <= maximumCharacterCount) { "String length ${value.length} exceeds maximum $maximumCharacterCount" }
                value
            },
        )
    }

    private fun readVarInt(input: PacketBuffer): Int {
        var value = 0
        var byteIndex = 0
        while (byteIndex < 5) {
            val currentByte = input.readByte().toInt()
            value = value or ((currentByte and 0x7F) shl (byteIndex * 7))
            if (currentByte and 0x80 == 0) {
                return value
            }
            byteIndex++
        }

        throw IllegalArgumentException("VarInt is too large")
    }

    private fun writeVarInt(output: PacketBuffer, value: Int) {
        var remainingValue = value
        while (remainingValue and 0x7F.inv() != 0) {
            output.writeByte(((remainingValue and 0x7F) or 0x80).toByte())
            remainingValue = remainingValue ushr 7
        }
        output.writeByte(remainingValue.toByte())
    }

    private fun readVarLong(input: PacketBuffer): Long {
        var value = 0L
        var byteIndex = 0
        while (byteIndex < 10) {
            val currentByte = input.readByte().toInt()
            value = value or ((currentByte and 0x7F).toLong() shl (byteIndex * 7))
            if (currentByte and 0x80 == 0) {
                return value
            }
            byteIndex++
        }

        throw IllegalArgumentException("VarLong is too large")
    }

    private fun writeVarLong(output: PacketBuffer, value: Long) {
        var remainingValue = value
        while (remainingValue and 0x7FL.inv() != 0L) {
            output.writeByte(((remainingValue and 0x7F) or 0x80).toByte())
            remainingValue = remainingValue ushr 7
        }
        output.writeByte(remainingValue.toByte())
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (exception: CharacterCodingException) {
            throw IllegalArgumentException("Malformed UTF-8", exception)
        }
    }
}
