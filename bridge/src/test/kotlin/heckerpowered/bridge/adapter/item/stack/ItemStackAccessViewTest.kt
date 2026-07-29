/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.stack

import heckerpowered.bridge.adapter.asView
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ItemStackAccessViewTest {
    @Test
    fun persistentDataRetainsItsStableItemStackBase() {
        assertTrue(ItemStackAccess::class.java.isAssignableFrom(PersistentDataAccess::class.java))
    }

    @Test
    fun supportedPersistentDataResolvesToTheOriginalStack() {
        val stack = PersistentStack()

        assertSame(stack, stack.asView<PersistentDataAccess>())
    }

    @Test
    fun unsupportedPersistentDataResolvesToNull() {
        val stack: ItemStackAccess = OrdinaryStack

        assertNull(stack as? PersistentDataAccess)
    }

    private class PersistentStack : ItemStackAccess by OrdinaryStack, PersistentDataAccess {
        override fun getLong(key: Identifier): Long? = null

        override fun setLong(key: Identifier, value: Long) {
        }

        override fun getDouble(key: Identifier): Double? = null

        override fun setDouble(key: Identifier, value: Double) {
        }

        override fun remove(key: Identifier) {
        }
    }

    private object OrdinaryStack : ItemStackAccess {
        override val item: ItemAccess
            get() = error("Item is not used by this test")
        override var count = 1
        override var damagePoints = 0
        override val maxStackCount = 1
        override val maxDamagePoints = 0
    }
}
