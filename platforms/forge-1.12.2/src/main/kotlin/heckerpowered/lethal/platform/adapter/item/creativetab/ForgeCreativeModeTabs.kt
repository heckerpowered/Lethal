/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabBlueprint
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabRegistry
import java.util.LinkedHashMap

object ForgeCreativeModeTabs {
    private val tabsByIdentifier = LinkedHashMap<String, HostedCreativeModeTab>()

    fun initialize() {
        for (blueprint in CreativeModeTabRegistry.all()) {
            val identifier = blueprint.identifier.asString()
            if (identifier in tabsByIdentifier) {
                continue
            }

            tabsByIdentifier[identifier] = HostedCreativeModeTab(blueprint)
        }
    }

    fun findAll(item: ItemBlueprint): List<HostedCreativeModeTab> {
        return CreativeModeTabRegistry.findAll(item).map(::get)
    }

    private fun get(blueprint: CreativeModeTabBlueprint): HostedCreativeModeTab {
        val identifier = blueprint.identifier.asString()
        return checkNotNull(tabsByIdentifier[identifier]) {
            "Creative mode tab has not been initialized: $identifier"
        }
    }
}
