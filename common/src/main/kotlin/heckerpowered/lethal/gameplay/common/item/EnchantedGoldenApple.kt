/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

/* SPDX-License-Identifier: MIT */
package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.effect.StatusEffectAccess
import heckerpowered.bridge.adapter.effect.StatusEffectInstance
import heckerpowered.bridge.adapter.effect.VanillaStatusEffect
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.item.FoodItem
import heckerpowered.bridge.adapter.item.ItemGlint
import heckerpowered.bridge.adapter.item.ItemProperties
import heckerpowered.bridge.adapter.item.form.Regular
import heckerpowered.bridge.adapter.item.interaction.ItemUseAnimation
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.lethal.Constants

/** The craftable golden apple from Java 1.8, with its original regeneration and absorption. */
object EnchantedGoldenApple : FoodItem, ItemGlint {
    override val identifier get() = Constants.identifier("enchanted_golden_apple")
    override val form = Regular
    override val properties = ItemProperties(maxUseDurationTicks = 32)
    override val nutrition = 4
    override val saturationModifier = 1.2F
    override val canAlwaysEat = true

    override fun hasGlint(stack: ItemStackAccess): Boolean = true
    override fun getUseAnimation(stack: ItemStackAccess): ItemUseAnimation = ItemUseAnimation.Eat

    override fun finishUsing(stack: ItemStackAccess, world: WorldAccess, user: EntityAccess): ItemStackAccess {
        if (world.isClientSide) return stack
        val consumer = user as? StatusEffectAccess ?: return stack

        consumer.addStatusEffect(StatusEffectInstance(VanillaStatusEffect.Absorption, 2400))
        consumer.addStatusEffect(StatusEffectInstance(VanillaStatusEffect.Regeneration, 600, 4))
        consumer.addStatusEffect(StatusEffectInstance(VanillaStatusEffect.Resistance, 6000))
        consumer.addStatusEffect(StatusEffectInstance(VanillaStatusEffect.FireResistance, 6000))

        return stack
    }
}
