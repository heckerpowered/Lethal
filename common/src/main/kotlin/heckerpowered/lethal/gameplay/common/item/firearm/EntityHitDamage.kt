/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityPartAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit

class EntityHitDamage(private val damageType: VanillaDamageType, private val damagePointsPerShot: Double, private vararg val hitEffects: EntityHitEffect) {
    fun apply(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>): List<EntityDamageResult> {
        val damageSource = DamageSources.vanilla(damageType, player, player)
        val damagePoints = damagePointsPerShot * shotCount
        val results = mutableListOf<EntityDamageResult>()

        for (entityHit in entityHits.distinctBy { it.logicalTarget.id }) {
            val result = damage(player, weaponStack, entityHit, damageSource, damagePoints)
            hitEffects.forEach { it.apply(result) }
            results += result
        }

        return results
    }

    private fun damage(player: PlayerAccess, weaponStack: ItemStackAccess, entityHit: EntityRayHit, damageSource: DamageSourceView, damagePoints: Double): EntityDamageResult {
        val target = entityHit.logicalTarget
        val livingTarget = target as? LivingEntityAccess
        val healthBeforeDamage = livingTarget?.health
        val damageAccepted = target.hurt(damageSource, damagePoints)
        val actualDamagePoints = livingTarget?.let { (healthBeforeDamage!! - it.health).coerceIn(0.0, it.maximumHealth) } ?: 0.0

        return EntityDamageResult(player, weaponStack, entityHit, target, livingTarget, damageSource, damagePoints, actualDamagePoints, damageAccepted)
    }
}

private fun Sequence<EntityRayHit>.distinctLogicalTargets(): Sequence<LogicalEntityHit> {
    return map { LogicalEntityHit(it, it.logicalTarget) }
        .distinctBy { it.target.id }
}

private val EntityRayHit.logicalTarget: EntityAccess
    get() = (entity as? EntityPartAccess)?.parent ?: entity

private fun EntityAccess.hurtAndMeasureDamage(livingTarget: LivingEntityAccess?, damageSource: DamageSourceView, damagePoints: Double): DamageMeasurement {
    if (livingTarget == null) return DamageMeasurement(hurt(damageSource, damagePoints), 0.0)

    val healthBeforeDamage = livingTarget.health
    val damageAccepted = hurt(damageSource, damagePoints)
    val actualDamagePoints = (healthBeforeDamage - livingTarget.health).coerceIn(0.0, livingTarget.maximumHealth)
    return DamageMeasurement(damageAccepted, actualDamagePoints)
}

private data class LogicalEntityHit(
    val hit: EntityRayHit,
    val target: EntityAccess,
)

private data class DamageMeasurement(
    val accepted: Boolean,
    val points: Double,
)
