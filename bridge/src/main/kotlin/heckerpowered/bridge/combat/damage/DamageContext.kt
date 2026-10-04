/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.combat.damage

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView

interface DamageContext {
    val target: LivingEntityAccess
    val source: DamageSourceView
    val rawDamage: Float
}

val DamageContext.attacker: EntityAccess?
    get() = source.causingEntity

val DamageContext.directEntity: EntityAccess?
    get() = source.directEntity

fun DamageContext.attackerAsLiving(): LivingEntityAccess? {
    return attacker as? LivingEntityAccess
}