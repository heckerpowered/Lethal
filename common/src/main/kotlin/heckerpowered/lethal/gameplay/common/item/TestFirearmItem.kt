/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm

object TestFirearmItem : Firearm() {
    override val identifier: Identifier
        get() = Constants.identifier("test_firearm")

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
        return Frequency.perSecond(2)
    }

    override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess) {
        println("Shoot")
    }
}