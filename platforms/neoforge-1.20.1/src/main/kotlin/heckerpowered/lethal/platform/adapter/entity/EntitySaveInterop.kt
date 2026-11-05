/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.entity

import heckerpowered.bridge.adapter.item.stack.PersistentValueInput
import heckerpowered.bridge.adapter.item.stack.PersistentValueOutput
import heckerpowered.lethal.platform.nbt.NativeNbt
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import java.util.*

internal class MinecraftEntitySaveOutput(private val data: CompoundTag) : PersistentValueOutput {
    override fun <T> encode(key: String, serializer: SerializationStrategy<T>, value: T) {
        val compound = NativeNbt.encode(serializer, value)
        data.put(key, compound)
    }

    override fun putInt(key: String, value: Int) = data.putInt(key, value)
    override fun putLong(key: String, value: Long) = data.putLong(key, value)
    override fun putDouble(key: String, value: Double) = data.putDouble(key, value)
    override fun putUuid(key: String, value: UUID) = data.putUUID(key, value)
    override fun remove(key: String) = data.remove(key)
}

internal class MinecraftEntitySaveInput(private val data: CompoundTag) : PersistentValueInput {
    override fun <T> decode(key: String, deserializer: DeserializationStrategy<T>): T? = NativeNbt.decode(data.get(key), deserializer)
    override fun getInt(key: String): Int? = if (data.contains(key, Tag.TAG_INT.toInt())) data.getInt(key) else null
    override fun getLong(key: String): Long? = if (data.contains(key, Tag.TAG_LONG.toInt())) data.getLong(key) else null
    override fun getDouble(key: String): Double? = if (data.contains(key, Tag.TAG_DOUBLE.toInt())) data.getDouble(key) else null
    override fun getUuid(key: String): UUID? = if (data.hasUUID(key)) data.getUUID(key) else null
}
