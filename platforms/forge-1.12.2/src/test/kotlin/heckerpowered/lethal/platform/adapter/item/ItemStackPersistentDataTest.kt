/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import heckerpowered.bridge.resources.IdentifierProvider
import net.minecraft.init.Bootstrap
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagInt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemStackPersistentDataTest {
    private val longKey = IdentifierProvider.Freestanding.identifier("lethal", "skill/cooldown")
    private val doubleKey = IdentifierProvider.Freestanding.identifier("lethal", "skill/charge")

    @BeforeTest
    fun setUp() {
        Bootstrap.register()
    }

    @Test
    fun missingReadsDoNotCreateNativeStorage() {
        val stack = ItemStack(Items.STICK)

        assertNull(ItemStackPersistentData.getLong(stack, longKey))
        assertNull(ItemStackPersistentData.getDouble(stack, doubleKey))
        assertFalse(stack.hasTagCompound())
    }

    @Test
    fun primitiveValuesSurviveNativeStackSerialization() {
        val stack = ItemStack(Items.STICK)
        ItemStackPersistentData.setLong(stack, longKey, 123_456_789L)
        ItemStackPersistentData.setDouble(stack, doubleKey, 875.5)

        val restoredStack = ItemStack(stack.serializeNBT())

        assertEquals(expected = 123_456_789L, actual = ItemStackPersistentData.getLong(restoredStack, longKey))
        assertEquals(expected = 875.5, actual = ItemStackPersistentData.getDouble(restoredStack, doubleKey))
    }

    @Test
    fun separateStacksRetainIndependentValues() {
        val firstStack = ItemStack(Items.STICK)
        val secondStack = ItemStack(Items.STICK)

        ItemStackPersistentData.setDouble(firstStack, doubleKey, 200.0)
        ItemStackPersistentData.setDouble(secondStack, doubleKey, 1_000.0)

        assertEquals(expected = 200.0, actual = ItemStackPersistentData.getDouble(firstStack, doubleKey))
        assertEquals(expected = 1_000.0, actual = ItemStackPersistentData.getDouble(secondStack, doubleKey))
    }

    @Test
    fun removingOneValuePreservesUnrelatedNativeData() {
        val stack = ItemStack(Items.STICK)
        stack.setTagInfo("foreign", NBTTagInt(7))
        ItemStackPersistentData.setLong(stack, longKey, 30L)

        ItemStackPersistentData.remove(stack, longKey)

        assertNull(ItemStackPersistentData.getLong(stack, longKey))
        assertTrue(stack.hasTagCompound())
        assertEquals(expected = 7, actual = stack.tagCompound?.getInteger("foreign"))
    }
}
