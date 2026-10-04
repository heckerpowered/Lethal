/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.combat.damage

import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView

data class DamageAttemptContext(
    override val target: LivingEntityAccess,
    override val source: DamageSourceView,
    override val rawDamage: Float,
) : CancellableDamageContext {
    private var cancelled: Boolean = false

    override val isCancelled get() = cancelled

    override fun cancel() {
        cancelled = true
    }
}