/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.Shotgun
import heckerpowered.lethal.gameplay.common.sound.ModSounds
import kotlin.math.PI

object CosmicStarshatterShotgun : Shotgun() {
    override val identifier: Identifier
        get() = Constants.identifier("cosmic_starshatter_shotgun")

    override fun getPelletCount(player: PlayerAccess, weaponStack: ItemStackAccess): Int {
        return 12
    }

    override fun getSpreadAngle(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
        return 6.0 * PI / 180.0
    }

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
        return Frequency.perSecond(2)
    }

    override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        require(shotCount >= 0)
        if (shotCount == 0L) return

        player.world.playSound(player.eyePosition, ModSounds.ArchaeopteryxFire)
        super.shoot(player, weaponStack, shotCount)
    }

    override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
        return 15.0
    }

    override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
        val damageSource = DamageSources.vanilla(VanillaDamageType.Generic, player, player)
        val damageByEntity = entityHits
            .groupBy { it.entity.id }.values
            .associate { entityHits ->
                entityHits.first().entity to entityHits.sumOf(::damageAtHit) * shotCount
            }
        damageByEntity.forEach { [entity, damagePoints] -> entity.hurt(damageSource, damagePoints) }
    }

    private fun damageAtHit(hit: EntityRayHit): Double {
        val distanceBlocks = hit.intersection.ray.origin.distanceTo(hit.point)
        require(distanceBlocks.isFinite() && distanceBlocks >= 0.0)
        return when {
            distanceBlocks <= 10.0 -> 200_000.0
            distanceBlocks <= 13.0 -> 3.0
            distanceBlocks <= 15.0 -> 1.0
            else -> 0.0
        }
    }
}
