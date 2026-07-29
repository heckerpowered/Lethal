/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

import heckerpowered.bridge.resources.Identifier
import kotlin.test.*

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
        assertEquals(expected = listOf(firstItem, secondItem), actual = ItemRegistry.all())
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
