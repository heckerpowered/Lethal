/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.input

import heckerpowered.bridge.resources.Identifier

object KeyBindingRegistry {
    private val registrations = LinkedHashMap<String, KeyBindingBlueprint>()

    fun register(blueprint: KeyBindingBlueprint): KeyBindingBlueprint {
        val identifier = blueprint.identifier.asString()
        require(identifier !in registrations) { "Key binding has already been registered: $identifier" }

        registrations[identifier] = blueprint
        return blueprint
    }

    operator fun get(identifier: Identifier): KeyBindingBlueprint? {
        return registrations[identifier.asString()]
    }

    fun all(): List<KeyBindingBlueprint> {
        return registrations.values.toList()
    }

    internal fun clear() {
        registrations.clear()
    }
}
