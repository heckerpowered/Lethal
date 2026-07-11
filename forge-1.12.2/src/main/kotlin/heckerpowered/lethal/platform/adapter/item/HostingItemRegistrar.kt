/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.adapter.item.ItemRegistrar
import net.minecraft.item.Item
import net.minecraftforge.fml.common.registry.GameRegistry

class HostingItemRegistrar : ItemRegistrar {
    private val provider = HostingItemProvider()

    override fun register(blueprint: ItemBlueprint): ItemAccess {
        val registry = GameRegistry.findRegistry(Item::class.java)
        val item = provider.item(blueprint)
        registry.register(item)
        return item
    }
}
