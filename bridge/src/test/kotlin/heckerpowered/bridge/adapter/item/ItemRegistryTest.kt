/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

import heckerpowered.bridge.resources.Identifier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ItemRegistryTest {
    @BeforeTest
    fun setUp() {
        ItemRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        ItemRegistry.clear()
    }

    @Test
    fun registrationPreservesDefinitionsAndOrder() {
        val firstItem = item("first")
        val secondItem = item("second")

        assertSame(firstItem, ItemRegistry.register(firstItem))
        assertSame(secondItem, ItemRegistry.register(secondItem))
        assertEquals(listOf(firstItem, secondItem), ItemRegistry.all())
    }

    @Test
    fun duplicateIdentifiersAreRejected() {
        ItemRegistry.register(item("duplicate"))

        assertFailsWith<IllegalArgumentException> {
            ItemRegistry.register(item("duplicate"))
        }
    }

    private fun item(path: String): SimpleItemBlueprint {
        return SimpleItemBlueprint(Identifier.create("test", path))
    }
}
