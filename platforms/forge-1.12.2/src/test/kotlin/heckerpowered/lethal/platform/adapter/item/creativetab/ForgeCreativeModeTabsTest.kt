/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.SimpleItemBlueprint
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabBlueprint
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabRegistry
import heckerpowered.bridge.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertSame

class ForgeCreativeModeTabsTest {
    @Test
    fun repeatedInitializationRetainsOneHostedTabForEachBlueprint() {
        val item = SimpleItemBlueprint(Identifier.create("test", "forge_creative_tab_item"))
        val blueprint = CreativeModeTabBlueprint(
            identifier = Identifier.create("test", "forge_creative_tab"),
            titleTranslationKey = "itemGroup.test.forge_creative_tab",
            icon = item,
            items = listOf(item),
        )
        CreativeModeTabRegistry.register(blueprint)

        ForgeCreativeModeTabs.initialize()
        val firstHostedTab = ForgeCreativeModeTabs.findAll(item).single()
        ForgeCreativeModeTabs.initialize()

        assertSame(blueprint, firstHostedTab.blueprint)
        assertSame(firstHostedTab, ForgeCreativeModeTabs.findAll(item).single())
    }
}
