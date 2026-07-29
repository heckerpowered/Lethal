/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
import heckerpowered.bridge.adapter.item.Hand
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.asSlot
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register

object WeaponSkillActivation : SkillActivationRule {
    init {
        RuleRegistry.register<SkillActivationRule>(this)
    }

    fun onInitialize() {
    }

    override fun onSkillActivation(request: SkillActivationRequest) {
        val equipment = request.player as? EntityEquipmentAccess ?: return

        for (hand in Hand.entries) {
            val weaponStack = equipment.getEquippedStack(hand.asSlot())
            if (weaponStack.isEmpty) continue

            val skillWeapon = weaponStack.item.skillWeapon() ?: continue
            val skill = skillWeapon.getSkill(request.slot) ?: continue
            skill.activate(request.player, weaponStack)
        }
    }
}

private fun ItemAccess.skillWeapon(): SkillWeapon? {
    return this as? SkillWeapon ?: form as? SkillWeapon
}
