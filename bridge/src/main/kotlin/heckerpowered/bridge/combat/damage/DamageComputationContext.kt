/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.combat.damage

import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView

data class DamageComputationContext(
    override val target: LivingEntityAccess,
    override val source: DamageSourceView,
    override val rawDamage: Float,
) : CancellableDamageContext {

    var baseDamageBonus: Double = 0.0
    var damageMultiplier: Double = 1.0
    var damageReductionMultiplier: Double = 1.0

    private var cancelled: Boolean = false

    override val isCancelled get() = cancelled

    override fun cancel() {
        cancelled = true
    }

    fun computeDamage(): Float {
        val damage = (rawDamage + baseDamageBonus) * damageMultiplier * damageReductionMultiplier
        return damage.coerceAtLeast(.0).toFloat()
    }
}