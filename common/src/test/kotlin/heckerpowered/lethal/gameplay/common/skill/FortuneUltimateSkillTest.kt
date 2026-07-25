/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.block.BlockAccess
import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.BlockPositions
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.math.intersect
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.lethal.gameplay.common.sound.ModSounds
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FortuneUltimateSkillTest {
    @Test
    fun chargeUsesActualLivingHealthLossAndEachVariantUsesItsLegacyMaximum() {
        val world = RecordingWorld()
        val player = createPlayer(world)
        val target = createLivingTarget()
        val standardStack = TestItemStack()
        val enhancedStack = TestItemStack()
        val standard = FortuneUltimateSkill(1_200.0, StarJudgementKind.Standard, RecordingSpawner())
        val enhanced = FortuneUltimateSkill(2_000.0, StarJudgementKind.Enhanced, RecordingSpawner())

        standard.apply(damageResult(player, standardStack, target, requestedDamagePoints = 30_000.0, actualDamagePoints = 17.0))
        enhanced.apply(damageResult(player, enhancedStack, target, requestedDamagePoints = 42_000.0, actualDamagePoints = 17.0))
        assertEquals(17.0, standard.currentCharge(standardStack))
        assertEquals(17.0, enhanced.currentCharge(enhancedStack))

        standard.apply(damageResult(player, standardStack, target, requestedDamagePoints = 30_000.0, actualDamagePoints = 3_000.0))
        enhanced.apply(damageResult(player, enhancedStack, target, requestedDamagePoints = 42_000.0, actualDamagePoints = 3_000.0))
        assertEquals(1_200.0, standard.currentCharge(standardStack))
        assertEquals(2_000.0, enhanced.currentCharge(enhancedStack))
    }

    @Test
    fun insufficientChargeDoesNotRaycastOrConsumeCharge() {
        val world = RecordingWorld()
        val player = createPlayer(world)
        val stack = TestItemStack()
        val target = createLivingTarget()
        val spawner = RecordingSpawner()
        val skill = FortuneUltimateSkill(1_200.0, StarJudgementKind.Standard, spawner)
        skill.apply(damageResult(player, stack, target, requestedDamagePoints = 399.0, actualDamagePoints = 399.0))

        skill.activate(player, stack)

        assertEquals(399.0, skill.currentCharge(stack))
        assertEquals(0, world.blockRaycastCount)
        assertEquals(emptyList(), spawner.spawns)
        assertEquals(emptyList(), world.playedSounds)
    }

    @Test
    fun missedTargetDoesNotConsumeReadyCharge() {
        val world = RecordingWorld()
        val player = createPlayer(world)
        val stack = TestItemStack()
        val target = createLivingTarget()
        val spawner = RecordingSpawner()
        val skill = FortuneUltimateSkill(1_200.0, StarJudgementKind.Standard, spawner)
        skill.apply(damageResult(player, stack, target, requestedDamagePoints = 400.0, actualDamagePoints = 400.0))

        skill.activate(player, stack)

        assertEquals(400.0, skill.currentCharge(stack))
        assertEquals(1, world.blockRaycastCount)
        assertEquals(BlockRaycastShape.Outline, world.lastBlockRaycastShape)
        assertEquals(512.0, world.lastBlockRaycastDistance)
        assertEquals(emptyList(), spawner.spawns)
        assertEquals(emptyList(), world.playedSounds)
    }

    @Test
    fun hitConsumesFourHundredChargeAndSpawnsTheConfiguredEffectAtTheExactHitPoint() {
        val hitPoint = Geometry.vector(4.25, 12.5, -8.75)
        val world = RecordingWorld(hitPoint)
        val player = createPlayer(world)
        val target = createLivingTarget()

        for (kind in StarJudgementKind.entries) {
            val stack = TestItemStack()
            val spawner = RecordingSpawner()
            val maximumCharge = if (kind == StarJudgementKind.Standard) 1_200.0 else 2_000.0
            val skill = FortuneUltimateSkill(maximumCharge, kind, spawner)
            skill.apply(damageResult(player, stack, target, requestedDamagePoints = 475.0, actualDamagePoints = 475.0))

            skill.activate(player, stack)

            assertEquals(75.0, skill.currentCharge(stack))
            val spawn = spawner.spawns.single()
            assertSame(world, spawn.world)
            assertSame(player, spawn.owner)
            assertSame(hitPoint, spawn.position)
            assertEquals(kind, spawn.kind)
        }

        assertEquals(2, world.blockRaycastCount)
        assertEquals(BlockRaycastShape.Outline, world.lastBlockRaycastShape)
        assertEquals(512.0, world.lastBlockRaycastDistance)
        assertEquals(Geometry.vector(1.0, 2.0, 3.0), world.lastRay?.origin)
        assertEquals(Geometry.vector(0.0, 0.0, 2.0), world.lastRay?.direction)
        assertEquals(listOf(hitPoint, hitPoint), world.playedSounds.map { sound -> sound.position })
        assertEquals(
            listOf(ModSounds.FortunePerkUltimate, ModSounds.FortunePerkUltimate),
            world.playedSounds.map { sound -> sound.playback },
        )
    }

    private fun damageResult(
        player: PlayerAccess,
        stack: ItemStackAccess,
        target: LivingEntityAccess,
        requestedDamagePoints: Double,
        actualDamagePoints: Double,
    ): EntityDamageResult {
        val ray = Geometry.ray(Geometry.vector(0.0, 0.0, 0.0), Geometry.vector(0.0, 0.0, 1.0))
        val intersection = requireNotNull(target.boundingBox.intersect(ray, 10.0))
        return EntityDamageResult(
            player = player,
            weaponStack = stack,
            hit = EntityRayHit(target, intersection),
            targetEntity = target,
            livingTarget = target,
            damageSource = DamageSources.vanilla(VanillaDamageType.FellOutOfWorld, player, player),
            requestedDamagePoints = requestedDamagePoints,
            actualDamagePoints = actualDamagePoints,
            damageAccepted = true,
        )
    }

    private fun createPlayer(world: WorldAccess): PlayerAccess {
        val identifier = UUID.randomUUID()
        return Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java)) { player, method, arguments ->
            when (method.name) {
                "getId" -> 1
                "getUuid" -> identifier
                "getWorld" -> world
                "getEyePosition" -> Geometry.vector(1.0, 2.0, 3.0)
                "getViewVector" -> Geometry.vector(0.0, 0.0, 2.0)
                "hashCode" -> System.identityHashCode(player)
                "equals" -> player === arguments?.firstOrNull()
                else -> error("Unsupported player method: ${method.name}")
            }
        } as PlayerAccess
    }

    private fun createLivingTarget(): LivingEntityAccess {
        val boundingBox = Geometry.box(-0.5, -0.5, 1.0, 0.5, 0.5, 2.0)
        return Proxy.newProxyInstance(LivingEntityAccess::class.java.classLoader, arrayOf(LivingEntityAccess::class.java)) { target, method, arguments ->
            when (method.name) {
                "getId" -> 2
                "getBoundingBox" -> boundingBox
                "getHealth", "getMaximumHealth" -> 1_000.0
                "hashCode" -> System.identityHashCode(target)
                "equals" -> target === arguments?.firstOrNull()
                else -> error("Unsupported target method: ${method.name}")
            }
        } as LivingEntityAccess
    }

    private class TestItemStack : ItemStackAccess, PersistentDataAccess {
        private val longValues = mutableMapOf<Identifier, Long>()
        private val doubleValues = mutableMapOf<Identifier, Double>()

        override val item: ItemAccess
            get() = error("Item is not used by this test")
        override var count = 1
        override var damagePoints = 0
        override val maxStackCount = 1
        override val maxDamagePoints = 0

        override fun getLong(key: Identifier): Long? = longValues[key]

        override fun setLong(key: Identifier, value: Long) {
            longValues[key] = value
        }

        override fun getDouble(key: Identifier): Double? = doubleValues[key]

        override fun setDouble(key: Identifier, value: Double) {
            doubleValues[key] = value
        }

        override fun remove(key: Identifier) {
            longValues.remove(key)
            doubleValues.remove(key)
        }
    }

    private class RecordingSpawner : StarJudgementSpawner {
        val spawns = mutableListOf<Spawn>()

        override fun spawn(
            world: WorldAccess,
            owner: PlayerAccess,
            position: VectorView,
            kind: StarJudgementKind,
        ) {
            spawns += Spawn(world, owner, position, kind)
        }
    }

    private data class Spawn(
        val world: WorldAccess,
        val owner: PlayerAccess,
        val position: VectorView,
        val kind: StarJudgementKind,
    )

    private class RecordingWorld(hitPoint: VectorView? = null) : WorldAccess {
        private val blockHits = hitPoint?.let { point ->
            sequence {
                yield(
                    BlockHitResult(
                        BlockPositions.Zero,
                        TestBlockState,
                        BlockDirection.Up,
                        point,
                        1.0,
                    ),
                )
                error("Ultimate targeting must stop after the nearest block hit")
            }
        } ?: emptySequence()

        var blockRaycastCount = 0
        var lastBlockRaycastShape: BlockRaycastShape? = null
        var lastBlockRaycastDistance: Double? = null
        var lastRay: RayView? = null
        val playedSounds = mutableListOf<PlayedSound>()

        override val isClientSide = false
        override val loadedEntityCount = 0
        override val entities = emptySequence<EntityAccess>()

        override fun getEntities(searchBox: BoxView): Sequence<EntityAccess> = emptySequence()

        override fun getEntityRayBuckets(ray: RayView, length: Double): Sequence<EntityRayBucket> = emptySequence()

        override fun raycastBlockHits(
            ray: RayView,
            distanceBlocks: Double,
            shape: BlockRaycastShape,
        ): Sequence<BlockHitResult> {
            blockRaycastCount++
            lastRay = ray
            lastBlockRaycastDistance = distanceBlocks
            lastBlockRaycastShape = shape
            return blockHits
        }

        override fun destroyBlock(position: BlockPositionView, dropItems: Boolean): Boolean = false

        override fun playSound(position: VectorView, playback: SoundPlayback) {
            playedSounds += PlayedSound(position, playback)
        }

        override fun spawnParticles(position: VectorView, effect: ParticleEffect) {
        }
    }

    private data class PlayedSound(val position: VectorView, val playback: SoundPlayback)

    private object TestBlockState : BlockStateAccess {
        override val block = TestBlock
    }

    private object TestBlock : BlockAccess {
        override val identifier = Identifier.create("test", "target")
    }
}
