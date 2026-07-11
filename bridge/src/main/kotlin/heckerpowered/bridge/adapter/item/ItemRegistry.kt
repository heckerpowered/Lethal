/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

import heckerpowered.bridge.platform.Services
import java.util.*

object ItemRegistry : ItemRegistrar {
    private val registrations = IdentityHashMap<ItemBlueprint, ItemAccess>()

    override fun register(blueprint: ItemBlueprint): ItemAccess {
        require(blueprint !in registrations) { "Item blueprint has already been registered" }

        val item = Services.ItemRegistrar.register(blueprint)
        registrations[blueprint] = item
        return item
    }

    operator fun get(blueprint: ItemBlueprint): ItemAccess? {
        return registrations[blueprint]
    }

    fun find(blueprint: ItemBlueprint): ItemAccess? {
        return registrations[blueprint]
    }
}