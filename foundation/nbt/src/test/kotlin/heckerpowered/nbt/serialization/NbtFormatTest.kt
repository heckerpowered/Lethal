/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.nbt.serialization

import heckerpowered.nbt.*
import heckerpowered.nbt.serialization.serializer.UUIDSerializer
import kotlinx.serialization.*
import java.util.*
import kotlin.test.*

class NbtFormatTest {
    @Serializable
    private data class Scalars(
        val boolean: Boolean,
        val byte: Byte,
        val short: Short,
        val int: Int,
        val long: Long,
        val float: Float,
        val double: Double,
        val char: Char,
        val string: String,
    )

    @Serializable
    private enum class Mode {
        @SerialName("grenade")
        GRENADE,
        BULLET,
    }

    @Serializable
    private data class Defaults(val mode: Mode = Mode.BULLET, val note: String? = null)

    @Serializable
    private data class RequiredNullable(val note: String?)

    @Test
    fun `object scalars retain exact NBT types`() {
        val value = Scalars(true, 2, 3, 4, 5, 6.5F, 7.25, '★', "名称")
        val tag = NbtCodec.encode(value)
        assertEquals(NbtByte(1), tag["boolean"])
        assertEquals(NbtByte(2), tag["byte"])
        assertEquals(NbtShort(3), tag["short"])
        assertEquals(NbtInt(4), tag["int"])
        assertEquals(NbtLong(5), tag["long"])
        assertEquals(NbtFloat(6.5F), tag["float"])
        assertEquals(NbtDouble(7.25), tag["double"])
        assertEquals(NbtString("★"), tag["char"])
        assertEquals(NbtString("名称"), tag["string"])
        assertEquals(value, NbtCodec.decode<Scalars>(tag))
    }

    @Test
    fun `root primitives use value wrapper`() {
        val values = listOf(
            NbtCodec.encode(true)["value"],
            NbtCodec.encode(2.toByte())["value"],
            NbtCodec.encode(3.toShort())["value"],
            NbtCodec.encode(4)["value"],
            NbtCodec.encode(5L)["value"],
            NbtCodec.encode(6.5F)["value"],
            NbtCodec.encode(7.25)["value"],
            NbtCodec.encode('★')["value"],
            NbtCodec.encode("名称")["value"],
        )
        assertEquals(
            listOf(
                NbtByte(1), NbtByte(2), NbtShort(3), NbtInt(4), NbtLong(5),
                NbtFloat(6.5F), NbtDouble(7.25), NbtString("★"), NbtString("名称")
            ),
            values,
        )
        assertEquals(5L, NbtCodec.decode<Long>(NbtCodec.encode(5L)))
        assertEquals('★', NbtCodec.decode<Char>(NbtCodec.encode('★')))
    }

    @Test
    fun `root numeric conversions retain native Matrix rules`() {
        val floatTag = NbtCodec.encode(-1.25F)
        val doubleTag = NbtCodec.encode(-1.25)
        assertEquals(-2, NbtCodec.decode<Int>(floatTag))
        assertEquals(-2, NbtCodec.decode<Int>(doubleTag))
        assertEquals((-2).toByte(), NbtCodec.decode<Byte>(floatTag))
        assertEquals((-2).toShort(), NbtCodec.decode<Short>(doubleTag))
        assertEquals(-1L, NbtCodec.decode<Long>(floatTag))
        assertEquals(-2L, NbtCodec.decode<Long>(doubleTag))
    }

    @Test
    fun `enum names are serialized instead of ordinal numbers`() {
        val tag = NbtCodec.encode(Mode.GRENADE)
        assertEquals(NbtString("grenade"), tag["value"])
        assertEquals(Mode.GRENADE, NbtCodec.decode<Mode>(tag))
        tag.putString("value", "missing")
        assertFailsWith<IllegalStateException> { NbtCodec.decode<Mode>(tag) }
    }

    @Test
    fun `missing object fields use generated serializer defaults`() {
        assertEquals(Defaults(), NbtCodec.decode<Defaults>(NbtCompound()))
        val tag = NbtCodec.encode(Defaults(Mode.GRENADE))
        assertFalse(tag.contains("note"))
        assertEquals(NbtString("grenade"), tag["mode"])
        assertEquals(Defaults(Mode.GRENADE), NbtCodec.decode<Defaults>(tag))
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `required nullable fields inherit Matrix omission limitation`() {
        val tag = NbtCodec.encode(RequiredNullable(null))
        assertFalse(tag.contains("note"))
        assertFailsWith<MissingFieldException> { NbtCodec.decode<RequiredNullable>(tag) }
    }

    @Test
    fun `root lists wrap homogeneous primitive elements`() {
        val values = listOf(1L, 2L, Long.MAX_VALUE)
        val tag = NbtCodec.encode(values)
        val list = assertIs<NbtList>(tag["value"])
        assertEquals(listOf<NbtTag>(NbtLong(1), NbtLong(2), NbtLong(Long.MAX_VALUE)), list)
        assertEquals(values, NbtCodec.decode<List<Long>>(tag))
    }

    @Test
    fun `primitive arrays retain Matrix list encoding`() {
        val bytes = byteArrayOf(-128, 0, 127)
        val ints = intArrayOf(Int.MIN_VALUE, 0, Int.MAX_VALUE)
        val longs = longArrayOf(Long.MIN_VALUE, 0, Long.MAX_VALUE)
        assertIs<NbtList>(NbtCodec.encode(bytes)["value"])
        assertTrue(bytes.contentEquals(NbtCodec.decode<ByteArray>(NbtCodec.encode(bytes))))
        assertTrue(ints.contentEquals(NbtCodec.decode<IntArray>(NbtCodec.encode(ints))))
        assertTrue(longs.contentEquals(NbtCodec.decode<LongArray>(NbtCodec.encode(longs))))
    }

    @Test
    fun `typed object values reject a mismatched numeric tag`() {
        val tag = NbtCodec.encode(Scalars(true, 2, 3, 4, 5, 6.5F, 7.25, '★', "text"))
        tag.putInt("long", 5)
        assertFailsWith<ClassCastException> { NbtCodec.decode<Scalars>(tag) }
    }

    @Test
    fun `map numeric keys are unsupported`() {
        assertFailsWith<SerializationException> {
            NbtCodec.encode(mapOf(1 to "value"))
        }
    }

    @Test
    fun `enum keyed maps keep names as compound keys`() {
        val value = mapOf(Mode.GRENADE to 2L, Mode.BULLET to 3L)
        val tag = NbtCodec.encode(value)
        assertEquals(NbtLong(2), tag["grenade"])
        assertEquals(value, NbtCodec.decode<Map<Mode, Long>>(tag))
    }

    @Test
    fun `UUID serializer is independent of game types`() {
        val value = UUID.fromString("d33d4c11-67d0-4fcb-a940-e37b6fcc86df")
        val tag = NbtCodec.encode(value, serializer = UUIDSerializer)
        assertEquals(NbtString(value.toString()), tag["value"])
        assertEquals(value, NbtCodec.decode(tag, deserializer = UUIDSerializer))
    }
}
