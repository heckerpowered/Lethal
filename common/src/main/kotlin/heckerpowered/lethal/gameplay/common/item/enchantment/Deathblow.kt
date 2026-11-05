/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.enchantment

import heckerpowered.bridge.adapter.item.EquipmentSlot
import heckerpowered.bridge.adapter.item.enchantment.EnchantmentBlueprint
import heckerpowered.bridge.adapter.item.enchantment.EnchantmentCost
import heckerpowered.bridge.adapter.item.enchantment.EnchantmentProperties
import heckerpowered.bridge.adapter.item.enchantment.EnchantmentRarity
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.entity.EntityPolarity
import heckerpowered.lethal.gameplay.common.item.firearm.EnchantmentHitEffect

object Deathblow : EnchantmentBlueprint {
    override val identifier = Constants.identifier("deathblow")
    override val properties = EnchantmentProperties(
        setOf(Constants.identifier("zeus_blood")),
        setOf(EquipmentSlot.MainHand, EquipmentSlot.OffHand),
        EnchantmentRarity.VeryRare, 1,
        EnchantmentCost(1),
        EnchantmentCost(1)
    )

    val hitEffect = EnchantmentHitEffect(identifier) { result ->
        val target = result.livingTarget ?: return@EnchantmentHitEffect
        EntityPolarity.execute(target, result.damageSource)
    }
}
