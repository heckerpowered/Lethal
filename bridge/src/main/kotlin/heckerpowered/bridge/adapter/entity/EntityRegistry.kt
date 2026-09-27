/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

import heckerpowered.bridge.resources.Identifier

object EntityRegistry {
    private val Registry = EntityBlueprintRegistry()

    fun <Blueprint : EntityBlueprint> register(blueprint: Blueprint): Blueprint = Registry.register(blueprint)

    operator fun get(identifier: Identifier): EntityBlueprint? = Registry[identifier]

    fun find(identifier: String): EntityBlueprint? = Registry.find(identifier)

    fun all(): List<EntityBlueprint> = Registry.all()
}

internal class EntityBlueprintRegistry {
    private val blueprintsByIdentifier = LinkedHashMap<String, EntityBlueprint>()

    fun <Blueprint : EntityBlueprint> register(blueprint: Blueprint): Blueprint {
        val identifier = blueprint.identifier.asString()
        require(identifier !in blueprintsByIdentifier) { "Entity identifier has already been registered: $identifier" }

        blueprintsByIdentifier[identifier] = blueprint
        return blueprint
    }

    operator fun get(identifier: Identifier): EntityBlueprint? {
        return find(identifier.asString())
    }

    fun find(identifier: String): EntityBlueprint? {
        return blueprintsByIdentifier[identifier]
    }

    fun all(): List<EntityBlueprint> {
        return blueprintsByIdentifier.values.toList()
    }
}
