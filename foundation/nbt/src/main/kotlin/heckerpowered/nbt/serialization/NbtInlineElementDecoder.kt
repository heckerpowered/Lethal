/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.nbt.serialization

import heckerpowered.nbt.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder

class NbtInlineElementDecoder(
    codec: NbtCodec,
    private val tagProvider: () -> NbtTag,
) : SingleSlotNbtDecoder(codec) {
    private inline fun <reified T : NbtTag> tag(): T = tagProvider() as T

    override fun decodeBoolean() = tag<NbtByte>().value.toInt() != 0
    override fun decodeByte() = tag<NbtByte>().value
    override fun decodeShort() = tag<NbtShort>().value
    override fun decodeChar() = tag<NbtString>().value.firstOrNull() ?: '\u0000'
    override fun decodeInt() = tag<NbtInt>().value
    override fun decodeLong() = tag<NbtLong>().value
    override fun decodeFloat() = tag<NbtFloat>().value
    override fun decodeDouble() = tag<NbtDouble>().value
    override fun decodeString(): String = tag<NbtString>().value

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = when (descriptor.kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> NbtObjectDecoder(codec, tag<NbtCompound>())
        StructureKind.MAP -> NbtMapDecoder(codec, tag<NbtCompound>())
        StructureKind.LIST -> NbtListDecoder(codec, tag<NbtList>())
        else -> error("Unsupported structure in inline value: ${descriptor.kind}")
    }
}
