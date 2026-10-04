/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.combat.damage

import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView

data class DamageSettlementContext(
    override val target: LivingEntityAccess,
    override val source: DamageSourceView,
    override val rawDamage: Float,
    val reducedDamage: Float,
    val realizedDamage: Float,
) : DamageContext {
    val retention: Float = if (rawDamage > 0f) realizedDamage / rawDamage else 0f

    var remainingDamage: Float = realizedDamage
        private set

    fun consume(capacity: Float): Float {
        if (capacity <= 0f || remainingDamage <= 0f) return 0f
        val consumed = capacity.coerceAtMost(remainingDamage)
        remainingDamage -= consumed
        return consumed
    }
}
