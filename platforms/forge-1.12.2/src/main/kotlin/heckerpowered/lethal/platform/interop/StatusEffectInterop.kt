/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.effect.StatusEffectInstance
import heckerpowered.bridge.adapter.effect.VanillaStatusEffect
import net.minecraft.init.MobEffects
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect

object StatusEffectInterop {
    @JvmStatic
    fun asHost(effect: StatusEffectInstance): PotionEffect {
        return PotionEffect(effect.type.asHost(), effect.durationTicks, effect.amplifier)
    }

    @JvmStatic
    fun asHost(effect: VanillaStatusEffect): Potion {
        return when (effect) {
            VanillaStatusEffect.Glowing -> MobEffects.GLOWING
        }
    }
}

fun StatusEffectInstance.asHost(): PotionEffect = StatusEffectInterop.asHost(this)

fun VanillaStatusEffect.asHost(): Potion = StatusEffectInterop.asHost(this)
