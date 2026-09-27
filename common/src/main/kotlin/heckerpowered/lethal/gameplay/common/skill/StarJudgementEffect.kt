/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.asView
import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.effect.VanillaParticle
import heckerpowered.bridge.adapter.entity.*
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.sound.SoundCategory
import heckerpowered.bridge.adapter.sound.SoundEventSpec
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.*
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.gameplay.common.entity.isStarJudgement
import java.util.*
import kotlin.random.Random

/**
 * Advances the server-side gameplay state of a persistent Star Judgement entity.
 *
 * The hosting entity owns serialization and calls [tick] once per authoritative game tick.
 */
class StarJudgementEffect(
    private val world: WorldAccess,
    private val ownerIdentifier: UUID?,
    owner: PlayerAccess?,
    private val position: VectorView,
    val kind: StarJudgementKind,
    initialFuseTicks: Int = INITIAL_FUSE_TICKS,
) {
    var fuseTicks: Int = initialFuseTicks
        private set

    val isComplete: Boolean
        get() = fuseTicks < -kind.durationAfterDetonationTicks

    private var cachedOwner = owner

    fun tick() {
        if (isComplete) return

        fuseTicks--
        when {
            fuseTicks == 0 -> detonate()
            fuseTicks < 0 -> updateAfterDetonation()
        }
    }

    private fun detonate() {
        val owner = resolveOwner()
        val executionSource = owner?.executionSource()
        val damageSource = starJudgementDamageSource(owner, position)

        for (target in collectTargets(kind.detonationRadiusBlocks, position)) {
            displayStrike(target, position)

            if (target.minimumDistanceSquared <= EXECUTION_RADIUS_SQUARED) {
                execute(target.entity, executionSource)
                continue
            }

            val livingEntity = target.entity as? LivingEntityAccess ?: continue
            when {
                livingEntity.health <= EXECUTION_HEALTH_POINTS -> execute(livingEntity, executionSource)
                livingEntity.health <= HEAVY_DAMAGE_HEALTH_POINTS -> target.damageReceiver.hurt(damageSource, livingEntity.maximumHealth * HEAVY_DAMAGE_FRACTION)

                else -> target.damageReceiver.hurt(damageSource, livingEntity.maximumHealth * STANDARD_DAMAGE_FRACTION)
            }
        }
    }

    private fun updateAfterDetonation() {
        if (fuseTicks >= -EXECUTION_DURATION_TICKS) {
            executeNearbyEntities()
        }

        if (kind == StarJudgementKind.Enhanced && fuseTicks % DECAY_INTERVAL_TICKS == 0) {
            applyDecay()
        }
    }

    private fun executeNearbyEntities() {
        val executionSource = resolveOwner()?.executionSource()
        for ((entity) in collectTargets(EXECUTION_RADIUS_BLOCKS, position)) {
            execute(entity, executionSource)
        }
    }

    private fun applyDecay() {
        val damageSource = starJudgementDamageSource(resolveOwner(), position)
        for (target in collectTargets(ENHANCED_DETONATION_RADIUS_BLOCKS, position)) {
            displayStrike(target, position)
            target.damageReceiver.hurt(damageSource, DECAY_DAMAGE_POINTS)
        }
    }

    private fun displayStrike(target: AreaTarget, effectPosition: VectorView) {
        val traceStart = Geometry.vector(effectPosition.x, TRACE_START_Y, effectPosition.z)
        val traceOffset = target.damageReceiver.eyePosition - traceStart
        val traceLength = traceOffset.length
        if (traceLength > 0.0) {
            val traceDirection = traceOffset * (1.0 / traceLength)
            var distance = 0.0
            while (distance < traceLength) {
                world.spawnParticles(traceStart + traceDirection * distance, ExplosionParticle)
                distance += TRACE_PARTICLE_INTERVAL_BLOCKS
            }
        }

        val pitch = (1.0 + (Random.nextDouble() - Random.nextDouble()) * EXPLOSION_PITCH_VARIATION) * EXPLOSION_BASE_PITCH
        world.playSound(target.damageReceiver.position, SoundPlayback(ExplosionSound, SoundCategory.Players, EXPLOSION_VOLUME, pitch))
    }

    private fun collectTargets(radiusBlocks: Double, position: VectorView): Collection<AreaTarget> {
        val searchBox = Geometry.box(position, position).expandedBy(radiusBlocks)
        val targetsByEntityId = LinkedHashMap<Int, AreaTarget>()

        for (candidate in world.getEntities(searchBox)) {
            if (!candidate.isAlive || candidate.isRemoved) continue
            if (candidate.type.isStarJudgement()) continue

            val entityPart = candidate as? EntityPartAccess
            val targetEntity = entityPart?.parent ?: candidate
            if (!targetEntity.isAlive || targetEntity.isRemoved) continue
            if (targetEntity.uuid == ownerIdentifier) continue

            val distanceSquared = candidate.position.distanceSquaredTo(position)
            val target = targetsByEntityId[targetEntity.id]
            if (target == null) {
                targetsByEntityId[targetEntity.id] = AreaTarget(targetEntity, candidate, distanceSquared, distanceSquared)
                continue
            }

            target.minimumDistanceSquared = minOf(target.minimumDistanceSquared, distanceSquared)
            if (entityPart != null && (target.damageReceiver !is EntityPartAccess || distanceSquared < target.damageReceiverDistanceSquared)) {
                target.damageReceiver = candidate
                target.damageReceiverDistanceSquared = distanceSquared
            }
        }

        return targetsByEntityId.values
    }

    private fun execute(entity: EntityAccess, source: DamageSourceView?) {
        if (source != null && entity is LivingEntityAccess) {
            val execution = entity.asView<EntityExecutionAccess>()
            execution.execute(source)
            return
        }

        entity.remove()
    }

    private fun resolveOwner(): PlayerAccess? {
        val ownerIdentifier = ownerIdentifier ?: return null
        val cachedOwner = cachedOwner
        if (cachedOwner != null && cachedOwner.uuid == ownerIdentifier && !cachedOwner.isRemoved) {
            return cachedOwner
        }

        for (entity in world.entities) {
            if (entity.uuid != ownerIdentifier || entity.isRemoved) continue

            val owner = entity as? ServerPlayerAccess ?: continue
            this.cachedOwner = owner
            return owner
        }

        this.cachedOwner = null
        return null
    }

    private fun PlayerAccess.executionSource(): DamageSourceView {
        return DamageSources.vanilla(VanillaDamageType.PlayerAttack, this, this)
    }

    private fun starJudgementDamageSource(owner: PlayerAccess?, position: VectorView): DamageSourceView {
        return DamageSources.vanilla(VanillaDamageType.FellOutOfWorld, owner, owner, position)
    }

    private data class AreaTarget(
        val entity: EntityAccess,
        var damageReceiver: EntityAccess,
        var damageReceiverDistanceSquared: Double,
        var minimumDistanceSquared: Double,
    )

    private val StarJudgementKind.detonationRadiusBlocks: Double
        get() = when (this) {
            StarJudgementKind.Standard -> STANDARD_DETONATION_RADIUS_BLOCKS
            StarJudgementKind.Enhanced -> ENHANCED_DETONATION_RADIUS_BLOCKS
        }

    private val StarJudgementKind.durationAfterDetonationTicks: Int
        get() = when (this) {
            StarJudgementKind.Standard -> EXECUTION_DURATION_TICKS
            StarJudgementKind.Enhanced -> ENHANCED_DURATION_AFTER_DETONATION_TICKS
        }

    private companion object {
        val ExplosionParticle = ParticleEffect(VanillaParticle.ExplosionNormal, 1, Vectors.Zero, 0.0, true)
        val ExplosionSound = SoundEventSpec(Identifier.create("minecraft", "entity.generic.explode"))

        const val INITIAL_FUSE_TICKS = 20 * 5

        const val STANDARD_DETONATION_RADIUS_BLOCKS = 100.0
        const val ENHANCED_DETONATION_RADIUS_BLOCKS = 200.0
        const val EXECUTION_RADIUS_BLOCKS = 64.0
        const val EXECUTION_RADIUS_SQUARED = EXECUTION_RADIUS_BLOCKS * EXECUTION_RADIUS_BLOCKS

        const val EXECUTION_HEALTH_POINTS = 90.0
        const val HEAVY_DAMAGE_HEALTH_POINTS = 500.0
        const val HEAVY_DAMAGE_FRACTION = 0.75
        const val STANDARD_DAMAGE_FRACTION = 0.5

        const val EXECUTION_DURATION_TICKS = 20 * 10
        const val ENHANCED_DURATION_AFTER_DETONATION_TICKS = 20 * 30
        const val DECAY_INTERVAL_TICKS = 20
        const val DECAY_DAMAGE_POINTS = 3_000.0

        const val TRACE_START_Y = 319.0
        const val TRACE_PARTICLE_INTERVAL_BLOCKS = 5.0
        const val EXPLOSION_VOLUME = 4.0
        const val EXPLOSION_BASE_PITCH = 0.7
        const val EXPLOSION_PITCH_VARIATION = 0.2
    }
}
