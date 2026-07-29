/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import heckerpowered.bridge.resources.Identifier
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

object ItemStackPersistentData {
    @JvmStatic
    fun getLong(stack: ItemStack, key: Identifier): Long? {
        val data = data(stack) ?: return null
        val name = key.asString()
        if (!data.contains(name, Tag.TAG_LONG.toInt())) return null
        return data.getLong(name)
    }

    @JvmStatic
    fun setLong(stack: ItemStack, key: Identifier, value: Long) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putLong(key.asString(), value) }
    }

    @JvmStatic
    fun getDouble(stack: ItemStack, key: Identifier): Double? {
        val data = data(stack) ?: return null
        val name = key.asString()
        if (!data.contains(name, Tag.TAG_DOUBLE.toInt())) return null
        return data.getDouble(name)
    }

    @JvmStatic
    fun setDouble(stack: ItemStack, key: Identifier, value: Double) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putDouble(key.asString(), value) }
    }

    @JvmStatic
    fun remove(stack: ItemStack, key: Identifier) {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return
        if (!customData.contains(key.asString())) return
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.remove(key.asString()) }
    }

    private fun data(stack: ItemStack): CompoundTag? {
        return stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
    }
}
