/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item.creativetab

import heckerpowered.bridge.adapter.item.ItemRegistry
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabAccess
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabBlueprint
import heckerpowered.lethal.platform.interop.item
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraft.util.NonNullList

class HostedCreativeModeTab(val blueprint: CreativeModeTabBlueprint) : CreativeTabs(legacyLabel(blueprint)), CreativeModeTabAccess {
    override val identifier
        get() = blueprint.identifier

    override fun getTranslationKey(): String {
        return blueprint.titleTranslationKey
    }

    override fun createIcon(): ItemStack {
        val item = checkNotNull(ItemRegistry[blueprint.icon]) { "Creative mode tab icon item has not been registered: " + blueprint.icon.identifier.asString() }
        return ItemStack(item.item())
    }

    override fun displayAllRelevantItems(items: NonNullList<ItemStack>) {
        for (itemBlueprint in blueprint.items) {
            val item = checkNotNull(ItemRegistry[itemBlueprint]) { "Creative mode tab item has not been registered: " + itemBlueprint.identifier.asString() }
            item.item().getSubItems(this, items)
        }
    }
}

private fun legacyLabel(blueprint: CreativeModeTabBlueprint): String {
    return blueprint.identifier.namespace + "." + blueprint.identifier.path.replace('/', '.')
}
