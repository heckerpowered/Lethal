/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.ItemBlueprint
import java.util.*

object CreativeModeTabRegistry {
    private val blueprintsByIdentifier = LinkedHashMap<String, CreativeModeTabBlueprint>()
    private val tabsByItem = IdentityHashMap<ItemBlueprint, MutableList<CreativeModeTabBlueprint>>()

    fun register(blueprint: CreativeModeTabBlueprint): CreativeModeTabBlueprint {
        val identifier = blueprint.identifier.asString()
        require(identifier !in blueprintsByIdentifier) { "Creative mode tab identifier has already been registered: $identifier" }

        blueprintsByIdentifier[identifier] = blueprint
        for (item in blueprint.items) {
            tabsByItem.getOrPut(item) { mutableListOf() } += blueprint
        }

        return blueprint
    }

    fun all(): List<CreativeModeTabBlueprint> {
        return blueprintsByIdentifier.values.toList()
    }

    fun findAll(item: ItemBlueprint): List<CreativeModeTabBlueprint> {
        return tabsByItem[item]?.toList() ?: emptyList()
    }

    // TODO: clear doesn't seems make sense. If it's for testing, use @VisibleForTesting
    internal fun clear() {
        blueprintsByIdentifier.clear()
        tabsByItem.clear()
    }
}
