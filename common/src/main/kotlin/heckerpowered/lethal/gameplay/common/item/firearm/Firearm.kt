/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.adapter.item.ItemForm
import heckerpowered.bridge.adapter.item.ItemProperties
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

abstract class Firearm : ItemBlueprint, ItemForm, Gun {
    override val properties: ItemProperties
        get() = ItemProperties(1, 0, null)
    override val form: ItemForm
        get() = this

    override fun mayFire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean {
        return true
    }

    override fun fire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean {
        if (!mayFire(player, weaponStack)) return false
        shoot(player, weaponStack)

        return true
    }
}
