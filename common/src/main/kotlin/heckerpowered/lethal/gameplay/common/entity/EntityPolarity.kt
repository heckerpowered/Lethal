/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.entity

import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView

/** Matrix vulnerability bits consumed by native living entity health and death hooks. */
object EntityPolarity {
    const val FORCE_DEATH_CHECK = 1L shl 32
    const val ZERO_HEALTH_SPOOF = 1L shl 33

    fun execute(target: LivingEntityAccess, source: DamageSourceView) {
        if (target.world.isClientSide) return
        target.polarity = target.polarity or ZERO_HEALTH_SPOOF or FORCE_DEATH_CHECK
        target.health = 0.0
        target.die(source)
    }
}
