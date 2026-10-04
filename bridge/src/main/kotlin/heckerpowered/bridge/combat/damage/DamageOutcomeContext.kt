/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.combat.damage

import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView

data class DamageOutcomeContext(
    override val target: LivingEntityAccess,
    override val source: DamageSourceView,
    override val rawDamage: Float,
    val reducedDamage: Float,
    val retention: Float,
) : DamageContext {
    val realizedDamage: Float = rawDamage * retention
    val baseRetention: Float = if (rawDamage > 0f) reducedDamage / rawDamage else 0f
}