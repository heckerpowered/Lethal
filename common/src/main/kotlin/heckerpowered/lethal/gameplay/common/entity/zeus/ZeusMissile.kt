/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.entity.zeus

import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.effect.VanillaParticle
import heckerpowered.bridge.adapter.entity.*
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.sound.SoundCategory
import heckerpowered.bridge.adapter.sound.SoundEventSpec
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.raycast.raycastEntityHits
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.hurtAndMeasureDamage
import heckerpowered.math.*

object ZeusMissile : EntityBlueprint {
    override val identifier = Constants.identifier("zeus_missile")
    override val properties = EntityProperties(
        EntityDimensions(0.25F, 0.25F), EntityTracking(96, 1, true),
        isFireImmune = true, hasGravity = false, isSerializable = false, isSummonable = false,
    )

    override fun createBehavior(): EntityBehavior = ZeusMissileBehavior()

    fun createBehavior(owner: PlayerAccess): EntityBehavior = ZeusMissileBehavior(owner)

    const val MAXIMUM_FLIGHT_DISTANCE_BLOCKS = 30.0
    const val EXPLOSION_RADIUS_BLOCKS = 14.0
    const val EXPLOSION_DAMAGE_POINTS = 30_000.0
    const val SPEED_BLOCKS_PER_TICK = 1.5
}

/** The server advances flight and detonates once; hosts interpolate tracked positions on clients. */
class ZeusMissileBehavior(private val owner: PlayerAccess? = null) : EntityBehavior {
    private var flightDistanceBlocks = 0.0
    private var detonated = false

    override fun tick(entity: EntityAccess) {
        if (entity.world.isClientSide || entity.isRemoved || detonated) return
        val owner = owner
        if (owner == null) {
            entity.remove()
            return
        }

        val remainingDistance = ZeusMissile.MAXIMUM_FLIGHT_DISTANCE_BLOCKS - flightDistanceBlocks
        val velocity = entity.velocity
        val speed = velocity.length
        if (!speed.isFinite() || speed <= 0.0) {
            entity.remove()
            return
        }

        val stepDistance = minOf(speed, remainingDistance)
        val ray = Ray(entity.position, velocity * (1.0 / speed))
        val blockHit = entity.world.raycastBlockHits(ray, stepDistance).firstOrNull()
        val entityHit = entity.world.raycastEntityHits(ray, stepDistance, owner)
            .firstOrNull { it.entity.id != entity.id && it.entity.type.identifier != ZeusMissile.identifier && it.entity.isAlive && !it.entity.isRemoved }
        val impact = listOfNotNull(blockHit?.point, entityHit?.point).minByOrNull { it.distanceSquaredTo(ray.origin) }
        val destination = impact ?: ray.pointAt(stepDistance)

        flightDistanceBlocks += entity.position.distanceTo(destination)
        entity.position = destination
        if (impact != null || stepDistance >= remainingDistance) detonate(entity, owner)
    }

    private fun detonate(entity: EntityAccess, owner: PlayerAccess) {
        detonated = true
        val world = entity.world
        val position = entity.position
        val damageSource = DamageSources.vanilla(VanillaDamageType.PlayerExplosion, entity, owner, position)
        entity.remove()
        val searchBox = Box(position, position).expandedBy(ZeusMissile.EXPLOSION_RADIUS_BLOCKS)
        val damagedTargetIds = HashSet<Int>()
        val radiusSquared = ZeusMissile.EXPLOSION_RADIUS_BLOCKS.square()

        val physicalTargets = world.getEntities(searchBox).flatMap { candidate ->
            val parts = (candidate as? MultipartEntityAccess)?.parts
            if (parts.isNullOrEmpty()) sequenceOf(candidate) else parts.asSequence()
        }
        for (candidate in physicalTargets) {
            val target = (candidate as? EntityPartAccess)?.parent ?: candidate
            val livingTarget = target as? LivingEntityAccess ?: continue
            if (!livingTarget.isAlive || livingTarget.isRemoved || livingTarget.uuid == owner.uuid) continue
            if (candidate.boundingBox.distanceSquaredTo(position) > radiusSquared) continue
            if (!damagedTargetIds.add(livingTarget.id)) continue

            candidate.hurtAndMeasureDamage(livingTarget, damageSource, ZeusMissile.EXPLOSION_DAMAGE_POINTS)
        }

        world.spawnParticles(position, ExplosionParticles)
        world.playSound(position, ExplosionSound)
    }

    private companion object {
        val ExplosionParticles = ParticleEffect(VanillaParticle.ExplosionNormal, 24, Vector(1.5, 1.5, 1.5), 0.0, true)
        val ExplosionSound = SoundPlayback(SoundEventSpec(Identifier.create("minecraft", "entity.generic.explode")), SoundCategory.Players, 4.0, 0.7)
    }
}
