/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.nbt

/**
 * A typed NBT value independent of a game's native tag classes.
 * Platform adapters translate these values at their persistence boundary.
 */
sealed interface NbtTag

sealed interface NbtNumber : NbtTag {
    val number: Number
}

data class NbtByte(val value: Byte) : NbtNumber {
    override val number: Number get() = value
}

data class NbtShort(val value: Short) : NbtNumber {
    override val number: Number get() = value
}

data class NbtInt(val value: Int) : NbtNumber {
    override val number: Number get() = value
}

data class NbtLong(val value: Long) : NbtNumber {
    override val number: Number get() = value
}

data class NbtFloat(val value: Float) : NbtNumber {
    override val number: Number get() = value
}

data class NbtDouble(val value: Double) : NbtNumber {
    override val number: Number get() = value
}

data class NbtString(val value: String) : NbtTag

class NbtList : AbstractMutableList<NbtTag>(), NbtTag {
    private val elements = ArrayList<NbtTag>()

    override val size: Int get() = elements.size

    override fun get(index: Int): NbtTag = elements[index]

    override fun set(index: Int, element: NbtTag): NbtTag = elements.set(index, element)

    override fun add(index: Int, element: NbtTag) = elements.add(index, element)

    override fun removeAt(index: Int): NbtTag = elements.removeAt(index)
}

class NbtCompound : NbtTag {
    private val values = LinkedHashMap<String, NbtTag>()

    val entries: Map<String, NbtTag> get() = values

    operator fun get(key: String): NbtTag? = values[key]

    fun contains(key: String): Boolean = values.containsKey(key)

    fun keySet(): Set<String> = values.keys

    fun put(key: String, value: NbtTag) {
        values[key] = value
    }

    fun remove(key: String): NbtTag? = values.remove(key)

    fun putBoolean(key: String, value: Boolean) = putByte(key, if (value) 1 else 0)
    fun putByte(key: String, value: Byte) = put(key, NbtByte(value))
    fun putShort(key: String, value: Short) = put(key, NbtShort(value))
    fun putInt(key: String, value: Int) = put(key, NbtInt(value))
    fun putLong(key: String, value: Long) = put(key, NbtLong(value))
    fun putFloat(key: String, value: Float) = put(key, NbtFloat(value))
    fun putDouble(key: String, value: Double) = put(key, NbtDouble(value))
    fun putString(key: String, value: String) = put(key, NbtString(value))

    fun getBooleanOr(key: String, defaultValue: Boolean): Boolean =
        (values[key] as? NbtNumber)?.byteValue()?.let { it != 0.toByte() } ?: defaultValue

    fun getByteOr(key: String, defaultValue: Byte): Byte =
        (values[key] as? NbtNumber)?.byteValue() ?: defaultValue

    fun getShortOr(key: String, defaultValue: Short): Short =
        (values[key] as? NbtNumber)?.shortValue() ?: defaultValue

    fun getIntOr(key: String, defaultValue: Int): Int =
        (values[key] as? NbtNumber)?.intValue() ?: defaultValue

    fun getLongOr(key: String, defaultValue: Long): Long =
        (values[key] as? NbtNumber)?.longValue() ?: defaultValue

    fun getFloatOr(key: String, defaultValue: Float): Float =
        (values[key] as? NbtNumber)?.number?.toFloat() ?: defaultValue

    fun getDoubleOr(key: String, defaultValue: Double): Double =
        (values[key] as? NbtNumber)?.number?.toDouble() ?: defaultValue

    fun getStringOr(key: String, defaultValue: String): String =
        (values[key] as? NbtString)?.value ?: defaultValue

    override fun equals(other: Any?): Boolean = other is NbtCompound && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = values.toString()
}

// Matrix's root decoder uses native numeric getters. Float and double tags floor integer reads,
// while native float-to-long reads truncate; preserve those rules when removing the native types.
private fun NbtNumber.intValue(): Int = when (this) {
    is NbtFloat -> value.toInt().let { if (value < it) it - 1 else it }
    is NbtDouble -> value.toInt().let { if (value < it) it - 1 else it }
    else -> number.toInt()
}

private fun NbtNumber.byteValue(): Byte = when (this) {
    is NbtFloat, is NbtDouble -> intValue().toByte()
    else -> number.toByte()
}

private fun NbtNumber.shortValue(): Short = when (this) {
    is NbtFloat, is NbtDouble -> intValue().toShort()
    else -> number.toShort()
}

private fun NbtNumber.longValue(): Long = when (this) {
    is NbtDouble -> Math.floor(value).toLong()
    else -> number.toLong()
}
