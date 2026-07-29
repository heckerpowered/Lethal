/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.EntityExecutionAccess
import heckerpowered.bridge.adapter.asView
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.math.BoxView

class HeadshotExecutionEffect private constructor(private val minimumHeightFraction: (BoxView) -> Double) : EntityHitEffect {
    companion object {
        val Legendary = HeadshotExecutionEffect { 0.5 }
        val Mythic = HeadshotExecutionEffect { boundingBox ->
            val size = boundingBox.size
            val averageEdgeLength = (size.x + size.y + size.z) / 3.0
            if (averageEdgeLength > 1.7) 0.5 else 0.7
        }
    }

    override fun apply(result: EntityDamageResult) {
        val livingHitEntity = result.hit.entity as? LivingEntityAccess ?: return
        val boundingBox = livingHitEntity.boundingBox
        val minimumHitHeight = livingHitEntity.position.y + boundingBox.size.y * minimumHeightFraction(boundingBox)
        if (result.hit.point.y <= minimumHitHeight) return

        val execution = livingHitEntity.asView<EntityExecutionAccess>()
        val executionSource = DamageSources.vanilla(VanillaDamageType.PlayerAttack, result.player, result.player)
        execution.execute(executionSource)
    }
}
