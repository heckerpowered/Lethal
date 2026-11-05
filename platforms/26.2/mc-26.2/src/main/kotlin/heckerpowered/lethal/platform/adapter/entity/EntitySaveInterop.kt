/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.entity

import com.mojang.serialization.Codec
import heckerpowered.bridge.adapter.item.stack.PersistentValueInput
import heckerpowered.bridge.adapter.item.stack.PersistentValueOutput
import heckerpowered.lethal.platform.nbt.NativeNbt
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import net.minecraft.core.UUIDUtil
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.LongTag
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.*

internal class MinecraftEntitySaveInput(private val input: ValueInput) : PersistentValueInput {
    override fun <T> decode(key: String, deserializer: DeserializationStrategy<T>): T? =
        NativeNbt.decode(input.read(key, CompoundTag.CODEC).orElse(null), deserializer)

    override fun getInt(key: String): Int? = input.getInt(key).orElse(null)
    override fun getUuid(key: String): UUID? = input.read(key, UUIDUtil.CODEC).orElse(null)
    override fun getLong(key: String): Long? = (input.read(key, Codec.PASSTHROUGH).orElse(null)?.value as? LongTag)?.longValue()
    override fun getDouble(key: String): Double? = (input.read(key, Codec.PASSTHROUGH).orElse(null)?.value as? DoubleTag)?.doubleValue()
}

internal class MinecraftEntitySaveOutput(private val output: ValueOutput) : PersistentValueOutput {
    override fun <T> encode(key: String, serializer: SerializationStrategy<T>, value: T) {
        val compound = NativeNbt.encode(serializer, value)
        output.store(key, CompoundTag.CODEC, compound)
    }

    override fun putInt(key: String, value: Int) = output.putInt(key, value)
    override fun putLong(key: String, value: Long) = output.putLong(key, value)
    override fun putDouble(key: String, value: Double) = output.putDouble(key, value)
    override fun putUuid(key: String, value: UUID) = output.store(key, UUIDUtil.CODEC, value)
    override fun remove(key: String) = output.discard(key)
}
