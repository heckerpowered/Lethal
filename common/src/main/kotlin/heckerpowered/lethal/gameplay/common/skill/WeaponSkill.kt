/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

interface WeaponSkill {
    fun activate(player: PlayerAccess, weaponStack: ItemStackAccess)
}

interface SkillWeapon {
    fun getSkill(slot: SkillSlot): WeaponSkill?
}
