/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.effect.VanillaParticle
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityInterop
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.sound.SoundCategory
import heckerpowered.bridge.adapter.sound.SoundEventSpec
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.math.expandedBy
import heckerpowered.bridge.math.minus
import heckerpowered.bridge.math.plus
import heckerpowered.bridge.math.times
import heckerpowered.bridge.resources.Identifier
import java.util.UUID
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
    private val position: () -> VectorView,
    val kind: StarJudgementKind,
    initialFuseTicks: Int = InitialFuseTicks,
    private val randomFraction: () -> Double = { Random.nextDouble() },
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
        val position = position()
        val owner = resolveOwner()
        val executionSource = owner?.executionSource()
        val damageSource = starJudgementDamageSource(owner, position)

        for (target in collectTargets(kind.detonationRadiusBlocks, position)) {
            displayStrike(target, position)

            if (target.minimumDistanceSquared <= ExecutionRadiusSquared) {
                execute(target.entity, executionSource)
                continue
            }

            val livingEntity = EntityInterop.living(target.entity) ?: continue
            when {
                livingEntity.health <= ExecutionHealthPoints -> execute(livingEntity, executionSource)
                livingEntity.health <= HeavyDamageHealthPoints -> target.damageReceiver.hurt(
                    damageSource,
                    livingEntity.maximumHealth * HeavyDamageFraction,
                )

                else -> target.damageReceiver.hurt(
                    damageSource,
                    livingEntity.maximumHealth * StandardDamageFraction,
                )
            }
        }
    }

    private fun updateAfterDetonation() {
        if (fuseTicks >= -ExecutionDurationTicks) {
            executeNearbyEntities()
        }

        if (kind == StarJudgementKind.Enhanced && fuseTicks % DecayIntervalTicks == 0) {
            applyDecay()
        }
    }

    private fun executeNearbyEntities() {
        val executionSource = resolveOwner()?.executionSource()
        for (target in collectTargets(ExecutionRadiusBlocks, position())) {
            execute(target.entity, executionSource)
        }
    }

    private fun applyDecay() {
        val position = position()
        val damageSource = starJudgementDamageSource(resolveOwner(), position)
        for (target in collectTargets(EnhancedDetonationRadiusBlocks, position)) {
            displayStrike(target, position)
            target.damageReceiver.hurt(damageSource, DecayDamagePoints)
        }
    }

    private fun displayStrike(target: AreaTarget, effectPosition: VectorView) {
        val traceStart = Geometry.vector(effectPosition.x, TraceStartY, effectPosition.z)
        val traceOffset = target.damageReceiver.eyePosition - traceStart
        val traceLength = traceOffset.length
        if (traceLength > 0.0) {
            val traceDirection = traceOffset * (1.0 / traceLength)
            var distance = 0.0
            while (distance < traceLength) {
                world.spawnParticles(traceStart + traceDirection * distance, ExplosionParticle)
                distance += TraceParticleIntervalBlocks
            }
        }

        val pitch = (1.0 + (randomFraction() - randomFraction()) * ExplosionPitchVariation) * ExplosionBasePitch
        world.playSound(
            target.damageReceiver.position,
            SoundPlayback(ExplosionSound, SoundCategory.Players, volume = ExplosionVolume, pitch = pitch),
        )
    }

    private fun collectTargets(radiusBlocks: Double, position: VectorView): Collection<AreaTarget> {
        val searchBox = Geometry.box(position, position).expandedBy(radiusBlocks)
        val targetsByEntityId = LinkedHashMap<Int, AreaTarget>()

        for (candidate in world.getEntities(searchBox)) {
            if (!candidate.isAlive || candidate.isRemoved) continue
            if (StarJudgementEntityInterop.entity(candidate) != null) continue

            val entityPart = EntityInterop.part(candidate)
            val targetEntity = entityPart?.parent ?: candidate
            if (!targetEntity.isAlive || targetEntity.isRemoved) continue
            if (targetEntity.uuid == ownerIdentifier) continue

            val distanceSquared = candidate.position.distanceSquaredTo(position)
            val target = targetsByEntityId[targetEntity.id]
            if (target == null) {
                targetsByEntityId[targetEntity.id] = AreaTarget(
                    entity = targetEntity,
                    damageReceiver = candidate,
                    damageReceiverDistanceSquared = distanceSquared,
                    minimumDistanceSquared = distanceSquared,
                )
                continue
            }

            target.minimumDistanceSquared = minOf(target.minimumDistanceSquared, distanceSquared)
            if (entityPart != null &&
                (EntityInterop.part(target.damageReceiver) == null || distanceSquared < target.damageReceiverDistanceSquared)
            ) {
                target.damageReceiver = candidate
                target.damageReceiverDistanceSquared = distanceSquared
            }
        }

        return targetsByEntityId.values
    }

    private fun execute(entity: EntityAccess, source: DamageSourceView?) {
        if (source != null && EntityInterop.living(entity) != null) {
            val execution = EntityInterop.execution(entity)
                ?: error("Living entity ${entity.id} does not expose execution access")
            execution.execute(source)
            return
        }

        val removal = EntityInterop.removal(entity)
            ?: error("Entity ${entity.id} does not expose removal access")
        removal.remove()
    }

    private fun resolveOwner(): PlayerAccess? {
        val ownerIdentifier = ownerIdentifier ?: return null
        val cachedOwner = cachedOwner
        if (cachedOwner != null && cachedOwner.uuid == ownerIdentifier && !cachedOwner.isRemoved) {
            return cachedOwner
        }

        for (entity in world.entities) {
            if (entity.uuid != ownerIdentifier || entity.isRemoved) continue

            val owner = EntityInterop.serverPlayer(entity) ?: continue
            this.cachedOwner = owner
            return owner
        }

        this.cachedOwner = null
        return null
    }

    private fun PlayerAccess.executionSource(): DamageSourceView {
        return DamageSources.vanilla(
            VanillaDamageType.PlayerAttack,
            directEntity = this,
            causingEntity = this,
        )
    }

    private fun starJudgementDamageSource(owner: PlayerAccess?, position: VectorView): DamageSourceView {
        return DamageSources.vanilla(
            VanillaDamageType.FellOutOfWorld,
            directEntity = owner,
            causingEntity = owner,
            position = position,
        )
    }

    private data class AreaTarget(
        val entity: EntityAccess,
        var damageReceiver: EntityAccess,
        var damageReceiverDistanceSquared: Double,
        var minimumDistanceSquared: Double,
    )

    private val StarJudgementKind.detonationRadiusBlocks: Double
        get() = when (this) {
            StarJudgementKind.Standard -> StandardDetonationRadiusBlocks
            StarJudgementKind.Enhanced -> EnhancedDetonationRadiusBlocks
        }

    private val StarJudgementKind.durationAfterDetonationTicks: Int
        get() = when (this) {
            StarJudgementKind.Standard -> ExecutionDurationTicks
            StarJudgementKind.Enhanced -> EnhancedDurationAfterDetonationTicks
        }

    private companion object {
        val ExplosionParticle = ParticleEffect(VanillaParticle.ExplosionNormal, longDistance = true)
        val ExplosionSound = SoundEventSpec(Identifier.create("minecraft", "entity.generic.explode"))

        const val InitialFuseTicks = 20 * 5

        const val StandardDetonationRadiusBlocks = 100.0
        const val EnhancedDetonationRadiusBlocks = 200.0
        const val ExecutionRadiusBlocks = 64.0
        const val ExecutionRadiusSquared = ExecutionRadiusBlocks * ExecutionRadiusBlocks

        const val ExecutionHealthPoints = 90.0
        const val HeavyDamageHealthPoints = 500.0
        const val HeavyDamageFraction = 0.75
        const val StandardDamageFraction = 0.5

        const val ExecutionDurationTicks = 20 * 10
        const val EnhancedDurationAfterDetonationTicks = 20 * 30
        const val DecayIntervalTicks = 20
        const val DecayDamagePoints = 3_000.0

        const val TraceStartY = 319.0
        const val TraceParticleIntervalBlocks = 5.0
        const val ExplosionVolume = 4.0
        const val ExplosionBasePitch = 0.7
        const val ExplosionPitchVariation = 0.2
    }
}
