/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import heckerpowered.bridge.resources.Identifier
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants

object ItemStackPersistentData {
    @JvmStatic
    fun getLong(stack: ItemStack, key: Identifier): Long? {
        val tag = stack.tagCompound ?: return null
        val name = key.asString()
        return if (tag.hasKey(name, Constants.NBT.TAG_LONG)) tag.getLong(name) else null
    }

    @JvmStatic
    fun setLong(stack: ItemStack, key: Identifier, value: Long) {
        getOrCreateTag(stack).setLong(key.asString(), value)
    }

    @JvmStatic
    fun getDouble(stack: ItemStack, key: Identifier): Double? {
        val tag = stack.tagCompound ?: return null
        val name = key.asString()
        return if (tag.hasKey(name, Constants.NBT.TAG_DOUBLE)) tag.getDouble(name) else null
    }

    @JvmStatic
    fun setDouble(stack: ItemStack, key: Identifier, value: Double) {
        getOrCreateTag(stack).setDouble(key.asString(), value)
    }

    @JvmStatic
    fun remove(stack: ItemStack, key: Identifier) {
        val tag = stack.tagCompound ?: return
        tag.removeTag(key.asString())
        if (tag.isEmpty) stack.tagCompound = null
    }

    private fun getOrCreateTag(stack: ItemStack): NBTTagCompound {
        val existingTag = stack.tagCompound
        if (existingTag != null) return existingTag

        val tag = NBTTagCompound()
        stack.tagCompound = tag
        return tag
    }
}
