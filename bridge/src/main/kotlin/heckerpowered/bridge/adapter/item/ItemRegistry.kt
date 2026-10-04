/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

object ItemRegistry {
    private val Registry = ItemBlueprintRegistry()

    fun <T : ItemBlueprint> register(blueprint: T): T = Registry.register(blueprint)

    fun all(): List<ItemBlueprint> = Registry.all()
}

internal class ItemBlueprintRegistry {
    private val blueprintsByIdentifier = LinkedHashMap<String, ItemBlueprint>()

    fun <T : ItemBlueprint> register(blueprint: T): T {
        val identifier = blueprint.identifier.asString()
        require(identifier !in blueprintsByIdentifier) { "Item identifier has already been registered: $identifier" }

        blueprintsByIdentifier[identifier] = blueprint
        return blueprint
    }

    fun all(): List<ItemBlueprint> {
        return blueprintsByIdentifier.values.toList()
    }
}
