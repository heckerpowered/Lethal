/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit

fun interface EntityHitEffect {
    fun apply(result: EntityDamageResult)
}

data class EntityDamageResult(
    val player: PlayerAccess,
    val weaponStack: ItemStackAccess,
    val hit: EntityRayHit,
    val targetEntity: EntityAccess,
    val livingTarget: LivingEntityAccess?,
    val damageSource: DamageSourceView,
    val requestedDamagePoints: Double,
    val actualDamagePoints: Double,
    val damageAccepted: Boolean,
)
