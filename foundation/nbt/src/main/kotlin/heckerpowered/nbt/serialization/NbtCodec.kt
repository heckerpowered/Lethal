/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.nbt.serialization

import heckerpowered.nbt.NbtCompound

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * Serializes Kotlin values using Matrix's NBT object, list, and map format.
 * Root scalars and lists are wrapped in a compound's `value` field; enum values use their names.
 *
 * Null object fields and null map values are omitted. Nullable object fields therefore need a
 * default to round-trip a null value. Lists reject null elements, and map keys must be strings
 * or enums. This format has no distinct representation for a null root value.
 */
class NbtCodec(
    val serializersModule: SerializersModule = EmptySerializersModule(),
) {
    companion object {
        fun <T> encode(serializer: SerializationStrategy<T>, value: T, codec: NbtCodec = NbtCodec()): NbtCompound {
            val encoder = RootNbtEncoder(codec)
            encoder.encodeSerializableValue(serializer, value)
            return encoder.result
        }

        fun <T> decode(deserializer: DeserializationStrategy<T>, tag: NbtCompound, codec: NbtCodec = NbtCodec()): T {
            val decoder = RootNbtDecoder(codec, tag)
            return decoder.decodeSerializableValue(deserializer)
        }

        inline fun <reified T> encode(value: T, codec: NbtCodec = NbtCodec(), serializer: SerializationStrategy<T> = serializer()): NbtCompound {
            return encode(serializer, value, codec)
        }

        inline fun <reified T> decode(tag: NbtCompound, codec: NbtCodec = NbtCodec(), deserializer: DeserializationStrategy<T> = serializer()): T {
            return decode(deserializer, tag, codec)
        }
    }
}
