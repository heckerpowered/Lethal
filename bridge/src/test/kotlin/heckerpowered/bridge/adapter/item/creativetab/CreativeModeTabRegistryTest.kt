/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.SimpleItemBlueprint
import heckerpowered.bridge.resources.Identifier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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
        assertEquals(listOf(firstTab, secondTab), CreativeModeTabRegistry.all())
        assertEquals(listOf(firstTab, secondTab), CreativeModeTabRegistry.findAll(item))
    }

    @Test
    fun duplicateIdentifiersAreRejected() {
        val item = item("item")
        CreativeModeTabRegistry.register(tab("duplicate", item))

        assertFailsWith<IllegalArgumentException> {
            CreativeModeTabRegistry.register(tab("duplicate", item))
        }
    }

    private fun item(path: String): SimpleItemBlueprint {
        return SimpleItemBlueprint(Identifier.create("test", path))
    }

    private fun tab(path: String, item: SimpleItemBlueprint): CreativeModeTabBlueprint {
        return CreativeModeTabBlueprint(
            identifier = Identifier.create("test", path),
            titleTranslationKey = "itemGroup.test.$path",
            icon = item,
            items = listOf(item),
        )
    }
}
