/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.nbt.serialization

import heckerpowered.nbt.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder

abstract class NbtContainerDecoder(
    codec: NbtCodec,
) : NbtDecoder(codec) {
    protected var currentIndex: Int = -1

    protected abstract fun selectNextIndex(descriptor: SerialDescriptor): Int

    protected abstract fun currentTag(): NbtTag

    @OptIn(ExperimentalSerializationApi::class)
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = when (descriptor.kind) {
        StructureKind.CLASS, StructureKind.OBJECT,
        PolymorphicKind.OPEN, PolymorphicKind.SEALED,
            -> {
            val compound = currentTag() as NbtCompound
            NbtObjectDecoder(codec, compound)
        }

        StructureKind.LIST -> {
            val list = currentTag() as NbtList
            NbtListDecoder(codec, list)
        }

        StructureKind.MAP -> {
            val compound = currentTag() as NbtCompound
            NbtMapDecoder(codec, compound)
        }

        else -> this
    }

    override fun endStructure(descriptor: SerialDescriptor) {

    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val index = selectNextIndex(descriptor)
        currentIndex = index
        return index
    }

    override fun decodeBoolean(): Boolean = (currentTag() as NbtByte).value.toInt() != 0
    override fun decodeByte(): Byte = (currentTag() as NbtByte).value
    override fun decodeShort(): Short = (currentTag() as NbtShort).value
    override fun decodeChar(): Char = (currentTag() as NbtString).value.firstOrNull() ?: '\u0000'
    override fun decodeInt(): Int = (currentTag() as NbtInt).value
    override fun decodeLong(): Long = (currentTag() as NbtLong).value
    override fun decodeFloat(): Float = (currentTag() as NbtFloat).value
    override fun decodeDouble(): Double = (currentTag() as NbtDouble).value
    override fun decodeString(): String = (currentTag() as NbtString).value
}
