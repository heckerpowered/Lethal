/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.network

import heckerpowered.bridge.network.StreamBuffer
import io.netty.buffer.ByteBuf

class ByteBufStreamBuffer(private val buffer: ByteBuf) : StreamBuffer {
    override val readableByteCount: Int
        get() = buffer.readableBytes()

    override fun writeBoolean(value: Boolean) {
        buffer.writeBoolean(value)
    }
    override fun writeByte(value: Byte) {
        buffer.writeByte(value.toInt())
    }
    override fun writeShort(value: Short) {
        buffer.writeShort(value.toInt())
    }
    override fun writeInt(value: Int) {
        buffer.writeInt(value)
    }
    override fun writeLong(value: Long) {
        buffer.writeLong(value)
    }
    override fun writeFloat(value: Float) {
        buffer.writeFloat(value)
    }
    override fun writeDouble(value: Double) {
        buffer.writeDouble(value)
    }

    override fun writeBytes(value: ByteArray) {
        buffer.writeBytes(value)
    }

    override fun readBoolean() = buffer.readBoolean()
    override fun readByte(): Byte = buffer.readByte()
    override fun readShort(): Short = buffer.readShort()
    override fun readInt(): Int = buffer.readInt()
    override fun readLong(): Long = buffer.readLong()
    override fun readFloat(): Float = buffer.readFloat()
    override fun readDouble(): Double =  buffer.readDouble()

    override fun readBytes(byteCount: Int): ByteArray {
        require(byteCount >= 0) { "Byte count must not be negative: $byteCount" }
        require(byteCount <= buffer.readableBytes()) { "Byte count $byteCount exceeds readable bytes ${buffer.readableBytes()}" }

        val value = ByteArray(byteCount)
        buffer.readBytes(value)
        return value
    }
}
