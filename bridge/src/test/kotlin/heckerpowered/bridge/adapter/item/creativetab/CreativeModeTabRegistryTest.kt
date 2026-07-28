/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.SimpleItemBlueprint
import heckerpowered.bridge.resources.Identifier
import kotlin.test.*

class CreativeModeTabRegistryTest {
    @BeforeTest
    fun setUp() {
        CreativeModeTabRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        CreativeModeTabRegistry.clear()
    }

    @Test
    fun registrationPreservesDefinitionsOrderAndItemMembership() {
        val item = item("item")
        val firstTab = tab("first", item)
        val secondTab = tab("second", item)

        assertSame(firstTab, CreativeModeTabRegistry.register(firstTab))
        assertSame(secondTab, CreativeModeTabRegistry.register(secondTab))
        assertEquals(expected = listOf(firstTab, secondTab), actual = CreativeModeTabRegistry.all())
        assertEquals(expected = listOf(firstTab, secondTab), actual = CreativeModeTabRegistry.findAll(item))
    }

    @Test
    fun duplicateIdentifiersAreRejected() {
        val item = item("item")
        CreativeModeTabRegistry.register(tab("duplicate", item))

        assertFailsWith<IllegalArgumentException> {
            CreativeModeTabRegistry.register(tab("duplicate", item))
        }
    }

    @Suppress("SameParameterValue")
    private fun item(path: String): SimpleItemBlueprint {
        return SimpleItemBlueprint(Identifier.create("test", path))
    }

    private fun tab(path: String, item: SimpleItemBlueprint): CreativeModeTabBlueprint {
        return CreativeModeTabBlueprint(Identifier.create("test", path), "itemGroup.test.$path", item, listOf(item))
    }
}
