/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.effect.VanillaParticle
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityExecutionAccess
import heckerpowered.bridge.adapter.entity.EntityPartAccess
import heckerpowered.bridge.adapter.entity.EntityRemovalAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.MultipartEntityAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.math.Vectors
import heckerpowered.bridge.math.intersects
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StarJudgementEffectTest {
    @Test
    fun standardDetonationPreservesTheLegacyRadiusAndHealthTiers() {
        val world = RecordingWorld()
        val owner = TestPlayer(1, world, point(0.0), UUID.randomUUID())
        val innerLiving = TestLivingEntity(2, world, point(10.0), health = 1_000.0, maximumHealth = 1_000.0)
        val lowHealthOuterLiving = TestLivingEntity(3, world, point(80.0), health = 90.0, maximumHealth = 1_000.0)
        val mediumHealthOuterLiving = TestLivingEntity(4, world, point(85.0), health = 400.0, maximumHealth = 1_000.0)
        val highHealthOuterLiving = TestLivingEntity(5, world, point(90.0), health = 600.0, maximumHealth = 1_000.0)
        val innerNonLiving = TestEntity(6, world, point(20.0))
        val outerNonLiving = TestEntity(7, world, point(80.0))
        val outsideDetonation = TestLivingEntity(8, world, point(101.0), health = 1_000.0, maximumHealth = 1_000.0)
        val otherJudgement = TestStarJudgementEntity(9, world, point(5.0))
        world.entitiesInWorld += listOf(
            owner,
            innerLiving,
            lowHealthOuterLiving,
            mediumHealthOuterLiving,
            highHealthOuterLiving,
            innerNonLiving,
            outerNonLiving,
            outsideDetonation,
            otherJudgement,
        )
        val center = Geometry.vector(0.0, 0.0, 0.0)
        val effect = effect(world, owner, center, StarJudgementKind.Standard)

        repeat(99) { effect.tick() }
        assertEquals(emptyList(), innerLiving.executionSources)

        effect.tick()

        assertEquals(0, effect.fuseTicks)
        assertEquals(1, innerLiving.executionSources.size)
        assertEquals(1, lowHealthOuterLiving.executionSources.size)
        assertEquals(listOf(750.0), mediumHealthOuterLiving.damagePoints)
        assertEquals(listOf(500.0), highHealthOuterLiving.damagePoints)
        assertTrue(innerNonLiving.isRemoved)
        assertFalse(outerNonLiving.isRemoved)
        assertEquals(emptyList(), outerNonLiving.damagePoints)
        assertEquals(emptyList(), outsideDetonation.damagePoints)
        assertFalse(otherJudgement.isRemoved)
        assertFalse(owner.isRemoved)

        val executionSource = innerLiving.executionSources.single()
        assertEquals(VanillaDamageType.PlayerAttack.identifier, executionSource.type)
        assertSame(owner, executionSource.directEntity)
        assertSame(owner, executionSource.causingEntity)

        val damageSource = highHealthOuterLiving.damageSources.single()
        assertEquals(VanillaDamageType.FellOutOfWorld.identifier, damageSource.type)
        assertSame(owner, damageSource.directEntity)
        assertSame(owner, damageSource.causingEntity)
        assertSame(center, damageSource.position)

        val detonationSearch = world.searchBoxes.single()
        assertEquals(-100.0, detonationSearch.minX)
        assertEquals(100.0, detonationSearch.maxX)
    }

    @Test
    fun standardExecutionZoneUsesTheLegacyCubeThroughPostDetonationTickTwoHundred() {
        val world = RecordingWorld()
        val owner = TestPlayer(1, world, point(0.0), UUID.randomUUID())
        world.entitiesInWorld += owner
        val effect = effect(world, owner, Geometry.vector(0.0, 0.0, 0.0), StarJudgementKind.Standard)
        repeat(100) { effect.tick() }

        val cubeCornerTarget = TestLivingEntity(
            2,
            world,
            Geometry.vector(63.0, 63.0, 63.0),
            health = 1_000.0,
            maximumHealth = 1_000.0,
        )
        world.entitiesInWorld += cubeCornerTarget
        effect.tick()
        assertEquals(-1, effect.fuseTicks)
        assertEquals(1, cubeCornerTarget.executionSources.size)

        repeat(198) { effect.tick() }
        assertEquals(-199, effect.fuseTicks)
        val finalIncludedTarget = TestLivingEntity(3, world, point(10.0), health = 1_000.0, maximumHealth = 1_000.0)
        world.entitiesInWorld += finalIncludedTarget
        effect.tick()
        assertEquals(-200, effect.fuseTicks)
        assertEquals(1, finalIncludedTarget.executionSources.size)
        assertFalse(effect.isComplete)

        val firstExcludedTarget = TestLivingEntity(4, world, point(10.0), health = 1_000.0, maximumHealth = 1_000.0)
        world.entitiesInWorld += firstExcludedTarget
        effect.tick()
        assertEquals(-201, effect.fuseTicks)
        assertEquals(emptyList(), firstExcludedTarget.executionSources)
        assertTrue(effect.isComplete)
    }

    @Test
    fun detonationDisplaysTheLegacyTraceAndExplosionSoundForEachLogicalTarget() {
        val world = RecordingWorld()
        val owner = TestPlayer(1, world, point(0.0), UUID.randomUUID())
        val target = TestLivingEntity(
            2,
            world,
            Geometry.vector(0.0, 9.0, 0.0),
            health = 1_000.0,
            maximumHealth = 1_000.0,
        )
        world.entitiesInWorld += listOf(owner, target)
        val effect = effect(
            world,
            owner,
            Geometry.vector(0.0, 0.0, 0.0),
            StarJudgementKind.Standard,
            randomFraction = { 0.5 },
        )

        repeat(100) { effect.tick() }

        assertEquals(62, world.spawnedParticles.size)
        assertEquals(Geometry.vector(0.0, 319.0, 0.0), world.spawnedParticles.first().position)
        assertEquals(Geometry.vector(0.0, 14.0, 0.0), world.spawnedParticles.last().position)
        assertTrue(world.spawnedParticles.all { particle ->
            particle.effect.particle == VanillaParticle.ExplosionNormal &&
                    particle.effect.count == 1 &&
                    particle.effect.longDistance
        })

        val sound = world.playedSounds.single()
        assertSame(target.position, sound.position)
        assertEquals("minecraft:entity.generic.explode", sound.playback.sound.identifier.asString())
        assertEquals(4.0, sound.playback.volume)
        assertEquals(0.7, sound.playback.pitch)
    }

    @Test
    fun enhancedJudgementAppliesThirtyDecayPulsesAndExpiresAfterPostTickSixHundred() {
        val world = RecordingWorld()
        val owner = TestPlayer(1, world, point(0.0), UUID.randomUUID())
        val outerLiving = TestLivingEntity(2, world, point(150.0), health = 1_000.0, maximumHealth = 1_000.0)
        val decayTarget = TestEntity(3, world, point(150.0))
        world.entitiesInWorld += listOf(owner, outerLiving, decayTarget)
        val center = Geometry.vector(0.0, 0.0, 0.0)
        val effect = effect(world, owner, center, StarJudgementKind.Enhanced)

        repeat(100) { effect.tick() }
        assertEquals(listOf(500.0), outerLiving.damagePoints)

        repeat(600) { effect.tick() }

        assertEquals(-600, effect.fuseTicks)
        assertFalse(effect.isComplete)
        assertEquals(30, decayTarget.damagePoints.size)
        assertTrue(decayTarget.damagePoints.all { damagePoints -> damagePoints == 3_000.0 })
        assertTrue(decayTarget.damageSources.all { source -> source.type == VanillaDamageType.FellOutOfWorld.identifier })
        assertTrue(decayTarget.damageSources.all { source -> source.directEntity === owner && source.causingEntity === owner })
        assertTrue(decayTarget.damageSources.all { source -> source.position === center })

        effect.tick()
        assertEquals(-601, effect.fuseTicks)
        assertTrue(effect.isComplete)
        assertEquals(30, decayTarget.damagePoints.size)
    }

    @Test
    fun enhancedExecutionZoneStopsAfterPostDetonationTickTwoHundred() {
        val world = RecordingWorld()
        val owner = TestPlayer(1, world, point(0.0), UUID.randomUUID())
        val finalIncludedTarget = TestLivingEntity(2, world, point(10.0), health = 1_000.0, maximumHealth = 1_000.0)
        world.entitiesInWorld += listOf(owner, finalIncludedTarget)
        val effect = effect(
            world,
            owner,
            Geometry.vector(0.0, 0.0, 0.0),
            StarJudgementKind.Enhanced,
            initialFuseTicks = -199,
        )

        effect.tick()
        assertEquals(1, finalIncludedTarget.executionSources.size)

        val firstExcludedTarget = TestLivingEntity(3, world, point(10.0), health = 1_000.0, maximumHealth = 1_000.0)
        world.entitiesInWorld += firstExcludedTarget
        effect.tick()

        assertEquals(-201, effect.fuseTicks)
        assertEquals(emptyList(), firstExcludedTarget.executionSources)
        assertFalse(effect.isComplete)
    }

    @Test
    fun enhancedDecayRepeatsTheStrikeDisplayOnAllThirtyPulses() {
        val world = RecordingWorld()
        val owner = TestPlayer(1, world, point(0.0), UUID.randomUUID())
        val target = TestEntity(2, world, point(150.0))
        world.entitiesInWorld += listOf(owner, target)
        val effect = effect(world, owner, Geometry.vector(0.0, 0.0, 0.0), StarJudgementKind.Enhanced)

        repeat(700) { effect.tick() }

        assertEquals(31, world.playedSounds.size)
        assertEquals(30, target.damagePoints.size)
        assertTrue(world.spawnedParticles.all { particle ->
            particle.effect.particle == VanillaParticle.ExplosionNormal
        })
    }

    @Test
    fun multipartTargetsUseTheLogicalParentAndAreProcessedOnce() {
        val world = RecordingWorld()
        val owner = TestPlayer(1, world, point(0.0), UUID.randomUUID())
        val innerParent = TestMultipartLivingEntity(2, world, point(150.0), health = 1_000.0, maximumHealth = 1_000.0)
        val innerFirstPart = TestPart(20, world, point(10.0), innerParent)
        val innerSecondPart = TestPart(21, world, point(20.0), innerParent)
        innerParent.setParts(innerFirstPart, innerSecondPart)

        val outerParent = TestMultipartLivingEntity(3, world, point(150.0), health = 600.0, maximumHealth = 1_000.0)
        val outerNearestPart = TestPart(30, world, point(80.0), outerParent)
        val outerSecondPart = TestPart(31, world, point(90.0), outerParent)
        outerParent.setParts(outerNearestPart, outerSecondPart)

        world.entitiesInWorld += listOf(
            owner,
            innerParent,
            innerFirstPart,
            innerSecondPart,
            outerParent,
            outerNearestPart,
            outerSecondPart,
        )
        val effect = effect(world, owner, Geometry.vector(0.0, 0.0, 0.0), StarJudgementKind.Standard)

        repeat(100) { effect.tick() }

        assertEquals(1, innerParent.executionSources.size)
        assertEquals(emptyList(), innerFirstPart.damagePoints)
        assertEquals(emptyList(), innerSecondPart.damagePoints)
        assertEquals(listOf(500.0), outerNearestPart.damagePoints)
        assertEquals(emptyList(), outerSecondPart.damagePoints)
        assertEquals(100.0, outerParent.health)
        assertEquals(
            listOf(innerFirstPart.position, outerNearestPart.position),
            world.playedSounds.map { sound -> sound.position },
        )
    }

    @Test
    fun reconnectedOwnerIsResolvedByUuidForAttributionAndExclusion() {
        val world = RecordingWorld()
        val ownerIdentifier = UUID.randomUUID()
        val disconnectedOwner = TestPlayer(1, world, point(0.0), ownerIdentifier)
        disconnectedOwner.remove()
        val reconnectedOwner = TestPlayer(2, world, point(0.0), ownerIdentifier)
        val target = TestLivingEntity(3, world, point(10.0), health = 1_000.0, maximumHealth = 1_000.0)
        world.entitiesInWorld += listOf(reconnectedOwner, target)
        val effect = StarJudgementEffect(
            world,
            ownerIdentifier,
            disconnectedOwner,
            position = { Geometry.vector(0.0, 0.0, 0.0) },
            kind = StarJudgementKind.Standard,
        )

        repeat(100) { effect.tick() }

        val source = target.executionSources.single()
        assertSame(reconnectedOwner, source.directEntity)
        assertSame(reconnectedOwner, source.causingEntity)
        assertEquals(emptyList(), reconnectedOwner.executionSources)
        assertFalse(reconnectedOwner.isRemoved)
    }

    @Test
    fun missingOwnerFallsBackToRemovalInsteadOfUnattributedLivingExecution() {
        val world = RecordingWorld()
        val target = TestLivingEntity(2, world, point(10.0), health = 1_000.0, maximumHealth = 1_000.0)
        world.entitiesInWorld += target
        val effect = StarJudgementEffect(
            world,
            UUID.randomUUID(),
            owner = null,
            position = { Geometry.vector(0.0, 0.0, 0.0) },
            kind = StarJudgementKind.Standard,
        )

        repeat(100) { effect.tick() }

        assertTrue(target.isRemoved)
        assertEquals(emptyList(), target.executionSources)
    }

    private fun effect(
        world: RecordingWorld,
        owner: TestPlayer,
        position: VectorView,
        kind: StarJudgementKind,
        initialFuseTicks: Int = 100,
        randomFraction: () -> Double = { 0.5 },
    ): StarJudgementEffect {
        return StarJudgementEffect(
            world,
            owner.uuid,
            owner,
            position = { position },
            kind = kind,
            initialFuseTicks = initialFuseTicks,
            randomFraction = randomFraction,
        )
    }

    private open class TestEntity(
        override val id: Int,
        override val world: WorldAccess,
        position: VectorView,
        override val uuid: UUID = UUID.randomUUID(),
    ) : EntityAccess, EntityRemovalAccess {
        val damageSources = mutableListOf<DamageSourceView>()
        val damagePoints = mutableListOf<Double>()

        private var alive = true
        private var removed = false

        override var position = position
        override var velocity: VectorView = Vectors.Zero
        override var boundingBox: BoxView = Geometry.box(position, position)
        override var pitch = 0.0
        override var yaw = 0.0
        override val eyeHeight = 0.0
        override val isAlive: Boolean
            get() = alive
        override val isRemoved: Boolean
            get() = removed
        override val isOnGround = false
        override val isOnFire = false

        override fun hurt(source: DamageSourceView, damagePoints: Double): Boolean {
            damageSources += source
            this.damagePoints += damagePoints
            return true
        }

        override fun remove() {
            alive = false
            removed = true
        }

        protected fun markDead() {
            alive = false
        }
    }

    private open class TestLivingEntity(
        id: Int,
        world: WorldAccess,
        position: VectorView,
        health: Double,
        override val maximumHealth: Double,
        uuid: UUID = UUID.randomUUID(),
    ) : TestEntity(id, world, position, uuid), LivingEntityAccess, EntityExecutionAccess {
        val executionSources = mutableListOf<DamageSourceView>()

        override var health = health

        override fun hurt(source: DamageSourceView, damagePoints: Double): Boolean {
            super.hurt(source, damagePoints)
            health = (health - damagePoints).coerceAtLeast(0.0)
            if (health == 0.0) markDead()
            return true
        }

        override fun execute(source: DamageSourceView) {
            executionSources += source
            health = 0.0
            markDead()
        }
    }

    private class TestPlayer(
        id: Int,
        world: WorldAccess,
        position: VectorView,
        uuid: UUID,
    ) : TestLivingEntity(id, world, position, health = 20.0, maximumHealth = 20.0, uuid = uuid), ServerPlayerAccess

    private class TestMultipartLivingEntity(
        id: Int,
        world: WorldAccess,
        position: VectorView,
        health: Double,
        maximumHealth: Double,
    ) : TestLivingEntity(id, world, position, health, maximumHealth), MultipartEntityAccess {
        private var physicalParts = emptyArray<EntityPartAccess>()

        override val parts: Array<out EntityPartAccess>
            get() = physicalParts

        fun setParts(vararg parts: EntityPartAccess) {
            physicalParts = arrayOf(*parts)
        }
    }

    private class TestPart(
        id: Int,
        world: WorldAccess,
        position: VectorView,
        override val parent: TestMultipartLivingEntity,
    ) : TestEntity(id, world, position), EntityPartAccess {
        override fun hurt(source: DamageSourceView, damagePoints: Double): Boolean {
            super.hurt(source, damagePoints)
            return parent.hurt(source, damagePoints)
        }
    }

    private class TestStarJudgementEntity(
        id: Int,
        world: WorldAccess,
        position: VectorView,
    ) : TestEntity(id, world, position), StarJudgementEntityAccess {
        override val starJudgementKind = StarJudgementKind.Standard
    }

    private class RecordingWorld : WorldAccess {
        val entitiesInWorld = mutableListOf<EntityAccess>()
        val searchBoxes = mutableListOf<BoxView>()
        val playedSounds = mutableListOf<PlayedSound>()
        val spawnedParticles = mutableListOf<SpawnedParticle>()

        override val isClientSide = false
        override val loadedEntityCount: Int
            get() = entitiesInWorld.size
        override val entities: Sequence<EntityAccess>
            get() = entitiesInWorld.asSequence()

        override fun getEntities(searchBox: BoxView): Sequence<EntityAccess> {
            searchBoxes += searchBox
            return entitiesInWorld.asSequence().filter { entity -> searchBox.intersects(entity.boundingBox) }
        }

        override fun getEntityRayBuckets(ray: RayView, length: Double): Sequence<EntityRayBucket> = emptySequence()

        override fun raycastBlockHits(
            ray: RayView,
            distanceBlocks: Double,
            shape: BlockRaycastShape,
        ): Sequence<BlockHitResult> = emptySequence()

        override fun destroyBlock(position: BlockPositionView, dropItems: Boolean): Boolean = false

        override fun playSound(position: VectorView, playback: SoundPlayback) {
            playedSounds += PlayedSound(position, playback)
        }

        override fun spawnParticles(position: VectorView, effect: ParticleEffect) {
            spawnedParticles += SpawnedParticle(position, effect)
        }
    }

    private data class PlayedSound(val position: VectorView, val playback: SoundPlayback)

    private data class SpawnedParticle(val position: VectorView, val effect: ParticleEffect)

    private companion object {
        fun point(x: Double): VectorView = Geometry.vector(x, 0.0, 0.0)
    }
}
