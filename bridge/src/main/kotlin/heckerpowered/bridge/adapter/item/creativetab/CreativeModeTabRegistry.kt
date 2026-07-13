/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.platform.Services
import java.util.IdentityHashMap

object CreativeModeTabRegistry : CreativeModeTabRegistrar {
    private val registrations = IdentityHashMap<CreativeModeTabBlueprint, CreativeModeTabAccess>()
    private val registrationsByIdentifier = mutableMapOf<String, CreativeModeTabAccess>()
    private val tabsByItem = IdentityHashMap<ItemBlueprint, MutableList<CreativeModeTabAccess>>()

    override fun register(blueprint: CreativeModeTabBlueprint): CreativeModeTabAccess {
        val identifier = blueprint.identifier.asString()
        require(blueprint !in registrations) { "Creative mode tab blueprint has already been registered" }
        require(identifier !in registrationsByIdentifier) { "Creative mode tab identifier has already been registered: $identifier" }

        val tab = Services.CreativeModeTabRegistrar.register(blueprint)
        registrations[blueprint] = tab
        registrationsByIdentifier[identifier] = tab

        for (item in blueprint.items) {
            tabsByItem.getOrPut(item) { mutableListOf() } += tab
        }

        return tab
    }

    operator fun get(blueprint: CreativeModeTabBlueprint): CreativeModeTabAccess? {
        return registrations[blueprint]
    }

    fun findAll(item: ItemBlueprint): List<CreativeModeTabAccess> {
        return tabsByItem[item] ?: emptyList()
    }
}
