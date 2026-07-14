/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit

class EntityHitDamage(private val damageType: VanillaDamageType, private val damagePointsPerShot: Double) {
    fun apply(player: PlayerAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
        val damageSource = DamageSources.vanilla(damageType, player, player)
        val damagePoints = damagePointsPerShot * shotCount.toDouble()
        entityHits.forEach { entityHit ->
            entityHit.entity.hurt(damageSource, damagePoints)
        }
    }
}
