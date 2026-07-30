/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.effect.StatusEffectInstance
import heckerpowered.bridge.adapter.effect.VanillaStatusEffect
import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

object StatusEffectInterop {
    @JvmStatic
    fun asHost(effect: StatusEffectInstance): MobEffectInstance {
        return MobEffectInstance(effect.type.asHost(), effect.durationTicks, effect.amplifier)
    }

    @JvmStatic
    fun asHost(effect: VanillaStatusEffect): Holder<MobEffect> {
        return when (effect) {
            VanillaStatusEffect.Glowing -> MobEffects.GLOWING
        }
    }
}

fun StatusEffectInstance.asHost(): MobEffectInstance = StatusEffectInterop.asHost(this)

fun VanillaStatusEffect.asHost(): Holder<MobEffect> = StatusEffectInterop.asHost(this)
