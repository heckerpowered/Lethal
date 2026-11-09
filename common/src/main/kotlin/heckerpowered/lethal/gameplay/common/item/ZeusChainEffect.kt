/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitDamage
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitEffect
import heckerpowered.lethal.gameplay.common.network.ZeusChainSegment
import heckerpowered.math.Geometry
import heckerpowered.math.Vectors
import heckerpowered.math.asPointBox
import heckerpowered.math.intersect

/**
 * Applies one shot's chain through the configured damage and hit effects. Completed segments remain
 * available if a later target fails; completed damage and energy are never rolled back.
 */
class ZeusChainEffect(
    private val targets: ZeusChainTargets,
    private val hitDamage: EntityHitDamage,
    private val shotCount: Long,
) : EntityHitEffect {
    private val completedSegments = mutableListOf<ZeusChainSegment>()
    val chainSegments: List<ZeusChainSegment>
        get() = completedSegments.toList()

    override fun apply(result: EntityDamageResult) {
        for ((entity, segment) in targets.findTargets(result)) {
            // Full coverage retains each selected center even if earlier damage moves that target.
            val ray = Geometry.ray(segment.endPosition, Vectors.UnitX)
            val hit = EntityRayHit(entity, requireNotNull(segment.endPosition.asPointBox().intersect(ray)))
            hitDamage.apply(result.player, result.weaponStack, shotCount, sequenceOf(hit))
            completedSegments += segment
        }
    }
}
