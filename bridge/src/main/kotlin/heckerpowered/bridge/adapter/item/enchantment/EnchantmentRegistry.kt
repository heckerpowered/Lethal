/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.enchantment

object EnchantmentRegistry {
    private val blueprintsByIdentifier = LinkedHashMap<String, EnchantmentBlueprint>()

    fun <Blueprint : EnchantmentBlueprint> register(blueprint: Blueprint): Blueprint {
        val identifier = blueprint.identifier.asString()
        require(identifier !in blueprintsByIdentifier) { "Enchantment identifier has already been registered: $identifier" }
        blueprintsByIdentifier[identifier] = blueprint
        return blueprint
    }

    fun all(): List<EnchantmentBlueprint> = blueprintsByIdentifier.values.toList()
}
