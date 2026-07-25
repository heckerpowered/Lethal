/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess
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

class HostedItemStackPersistentDataTest {
    private val longKey = IdentifierProvider.Freestanding.identifier("lethal", "skill/cooldown")
    private val doubleKey = IdentifierProvider.Freestanding.identifier("lethal", "skill/charge")

    @BeforeTest
    fun setUp() {
        Bootstrap.register()
    }

    @Test
    fun missingReadsDoNotCreateNativeStorage() {
        val nativeStack = ItemStack(Items.STICK)
        val data = HostedItemStackAccess(nativeStack) as PersistentDataAccess

        assertNull(data.getLong(longKey))
        assertNull(data.getDouble(doubleKey))
        assertFalse(nativeStack.hasTagCompound())
    }

    @Test
    fun primitiveValuesSurviveNativeStackSerialization() {
        val nativeStack = ItemStack(Items.STICK)
        val data = HostedItemStackAccess(nativeStack) as PersistentDataAccess
        data.setLong(longKey, 123_456_789L)
        data.setDouble(doubleKey, 875.5)

        val restoredData = HostedItemStackAccess(ItemStack(nativeStack.serializeNBT())) as PersistentDataAccess

        assertEquals(123_456_789L, restoredData.getLong(longKey))
        assertEquals(875.5, restoredData.getDouble(doubleKey))
    }

    @Test
    fun separateStacksRetainIndependentValues() {
        val firstData = HostedItemStackAccess(ItemStack(Items.STICK)) as PersistentDataAccess
        val secondData = HostedItemStackAccess(ItemStack(Items.STICK)) as PersistentDataAccess

        firstData.setDouble(doubleKey, 200.0)
        secondData.setDouble(doubleKey, 1_000.0)

        assertEquals(200.0, firstData.getDouble(doubleKey))
        assertEquals(1_000.0, secondData.getDouble(doubleKey))
    }

    @Test
    fun removingOneValuePreservesUnrelatedNativeData() {
        val nativeStack = ItemStack(Items.STICK)
        nativeStack.setTagInfo("foreign", NBTTagInt(7))
        val data = HostedItemStackAccess(nativeStack) as PersistentDataAccess
        data.setLong(longKey, 30L)

        data.remove(longKey)

        assertNull(data.getLong(longKey))
        assertTrue(nativeStack.hasTagCompound())
        assertEquals(7, nativeStack.tagCompound?.getInteger("foreign"))
    }
}
