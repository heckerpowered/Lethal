/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.SimpleItemBlueprint
import heckerpowered.bridge.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreativeModeTabTest {
    @Test
    fun blueprintPreservesItemOrder() {
        val firstItem = item("first")
        val secondItem = item("second")
        val blueprint = tab("ordered", firstItem, listOf(firstItem, secondItem))

        assertEquals(listOf(firstItem, secondItem), blueprint.items)
    }

    @Test
    fun blueprintRejectsInvalidTitleAndDuplicateItems() {
        val item = item("validation")

        assertFailsWith<IllegalArgumentException> {
            CreativeModeTabBlueprint(Identifier.create("test", "blank_title"), "", item, listOf(item))
        }
        assertFailsWith<IllegalArgumentException> {
            CreativeModeTabBlueprint(Identifier.create("test", "duplicate_items"), "itemGroup.test", item, listOf(item, item))
        }
    }

    private fun item(path: String): SimpleItemBlueprint {
        return SimpleItemBlueprint(Identifier.create("test", path))
    }

    @Suppress("SameParameterValue")
    private fun tab(path: String, icon: SimpleItemBlueprint, items: List<SimpleItemBlueprint>): CreativeModeTabBlueprint {
        return CreativeModeTabBlueprint(Identifier.create("test", path), "itemGroup.test.$path", icon, items)
    }
}
