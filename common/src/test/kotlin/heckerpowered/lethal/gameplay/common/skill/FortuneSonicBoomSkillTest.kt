/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityExecutionAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
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
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.math.intersect
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FortuneSonicBoomSkillTest {
    @Test
    fun chargeUsesActualLivingHealthLossAndStopsAtTheLegacyMaximum() {
        val skill = FortuneSonicBoomSkill()
        val stack = TestItemStack()
        val world = RecordingWorld()
        val player = createPlayer(world)
        val target = createTarget(2, pointBox(0.0, 0.0, 5.0))

        skill.apply(damageResult(player, stack, target.entity, requestedDamagePoints = 30_000.0, actualDamagePoints = 17.0))
        assertEquals(17.0, skill.currentCharge(stack))

        skill.apply(damageResult(player, stack, target.entity, requestedDamagePoints = 30_000.0, actualDamagePoints = 2_000.0))
        assertEquals(1_000.0, skill.currentCharge(stack))
    }

    @Test
    fun activationConsumesTwoHundredChargeAndExecutesOnlyEligibleTargets() {
        val skill = FortuneSonicBoomSkill()
        val stack = TestItemStack()
        val world = RecordingWorld()
        val player = createPlayer(world)
        val target = createTarget(2, pointBox(0.0, 0.0, 10.0))
        val targetOutsideRadius = createTarget(3, pointBox(3.0, 0.0, 10.0))
        val ordinaryEntity = createOrdinaryEntity(4, pointBox(0.0, 0.0, 12.0))
        world.entitiesInSearch = listOf(player, target.entity, targetOutsideRadius.entity, ordinaryEntity)
        skill.apply(damageResult(player, stack, target.entity, requestedDamagePoints = 275.0, actualDamagePoints = 275.0))

        skill.activate(player, stack)

        assertEquals(75.0, skill.currentCharge(stack))
        assertEquals(1, target.executionSources.size)
        assertEquals(0, targetOutsideRadius.executionSources.size)
        val executionSource = target.executionSources.single()
        assertTrue(executionSource.directEntity === player)
        assertTrue(executionSource.causingEntity === player)
        assertEquals(VanillaDamageType.PlayerAttack.identifier, executionSource.type)
        assertEquals(1, world.entitySearchCount)
        assertEquals(0, world.blockRaycastCount)

        val searchBox = requireNotNull(world.lastSearchBox)
        assertEquals(-9.0, searchBox.minX)
        assertEquals(-9.0, searchBox.minY)
        assertEquals(-9.0, searchBox.minZ)
        assertEquals(9.0, searchBox.maxX)
        assertEquals(9.0, searchBox.maxY)
        assertEquals(73.0, searchBox.maxZ)
    }

    @Test
    fun insufficientChargeDoesNotConsumeStateOrScanTheWorld() {
        val skill = FortuneSonicBoomSkill()
        val stack = TestItemStack()
        val world = RecordingWorld()
        val player = createPlayer(world)
        val target = createTarget(2, pointBox(0.0, 0.0, 5.0))
        skill.apply(damageResult(player, stack, target.entity, requestedDamagePoints = 199.0, actualDamagePoints = 199.0))

        skill.activate(player, stack)

        assertEquals(199.0, skill.currentCharge(stack))
        assertEquals(0, world.entitySearchCount)

        skill.apply(damageResult(player, stack, target.entity, requestedDamagePoints = 1.0, actualDamagePoints = 1.0))
        skill.activate(player, stack)

        assertEquals(0.0, skill.currentCharge(stack))
        assertEquals(1, world.entitySearchCount)
    }

    @Test
    fun activationPreservesLegacyCapsuleBoundaryQuirks() {
        val skill = FortuneSonicBoomSkill()
        val stack = TestItemStack()
        val world = RecordingWorld()
        val player = createPlayer(world)
        val chargeTarget = createTarget(2, pointBox(0.0, 0.0, 5.0))
        val behindStartByEightBlocks = createTarget(3, pointBox(0.0, 0.0, -8.0))
        val beyondEndByEightBlocks = createTarget(4, pointBox(0.0, 0.0, 72.0))
        val beyondEndByNineBlocks = createTarget(5, pointBox(0.0, 0.0, 73.0))
        val boxCrossedBySegment = createTarget(6, Geometry.box(-4.0, -4.0, 10.0, 4.0, 4.0, 11.0))
        world.entitiesInSearch = listOf(
            behindStartByEightBlocks.entity,
            beyondEndByEightBlocks.entity,
            beyondEndByNineBlocks.entity,
            boxCrossedBySegment.entity,
        )
        skill.apply(damageResult(player, stack, chargeTarget.entity, requestedDamagePoints = 200.0, actualDamagePoints = 200.0))

        skill.activate(player, stack)

        assertEquals(1, behindStartByEightBlocks.executionSources.size)
        assertEquals(1, beyondEndByEightBlocks.executionSources.size)
        assertEquals(0, beyondEndByNineBlocks.executionSources.size)
        assertEquals(0, boxCrossedBySegment.executionSources.size)
    }

    private fun damageResult(
        player: PlayerAccess,
        stack: ItemStackAccess,
        target: LivingEntityAccess,
        requestedDamagePoints: Double,
        actualDamagePoints: Double,
    ): EntityDamageResult {
        val ray = Geometry.ray(Geometry.vector(0.0, 0.0, 0.0), Geometry.vector(0.0, 0.0, 1.0))
        val intersection = requireNotNull(target.boundingBox.intersect(ray, 100.0))
        val damageSource = DamageSources.vanilla(VanillaDamageType.FellOutOfWorld, player, player)
        return EntityDamageResult(
            player = player,
            weaponStack = stack,
            hit = EntityRayHit(target, intersection),
            targetEntity = target,
            livingTarget = target,
            damageSource = damageSource,
            requestedDamagePoints = requestedDamagePoints,
            actualDamagePoints = actualDamagePoints,
            damageAccepted = true,
        )
    }

    private fun createPlayer(world: WorldAccess): PlayerAccess {
        return Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java)) { player, method, arguments ->
            when (method.name) {
                "getId" -> 1
                "getWorld" -> world
                "getEyePosition", "getPosition" -> Geometry.vector(0.0, 0.0, 0.0)
                "getViewVector" -> Geometry.vector(0.0, 0.0, 1.0)
                "hashCode" -> System.identityHashCode(player)
                "equals" -> player === arguments?.firstOrNull()
                else -> error("Unsupported player method: ${method.name}")
            }
        } as PlayerAccess
    }

    private fun createTarget(id: Int, boundingBox: BoxView): RecordingTarget {
        val executionSources = mutableListOf<DamageSourceView>()
        val entity = Proxy.newProxyInstance(
            LivingEntityAccess::class.java.classLoader,
            arrayOf(LivingEntityAccess::class.java, EntityExecutionAccess::class.java),
        ) { target, method, arguments ->
            when (method.name) {
                "getId" -> id
                "getBoundingBox" -> boundingBox
                "getHealth", "getMaximumHealth" -> 1_000.0
                "execute" -> executionSources += arguments?.first() as DamageSourceView
                "hashCode" -> System.identityHashCode(target)
                "equals" -> target === arguments?.firstOrNull()
                else -> error("Unsupported target method: ${method.name}")
            }
        } as LivingEntityAccess
        return RecordingTarget(entity, executionSources)
    }

    private fun createOrdinaryEntity(id: Int, boundingBox: BoxView): EntityAccess {
        return Proxy.newProxyInstance(EntityAccess::class.java.classLoader, arrayOf(EntityAccess::class.java)) { entity, method, arguments ->
            when (method.name) {
                "getId" -> id
                "getBoundingBox" -> boundingBox
                "hashCode" -> System.identityHashCode(entity)
                "equals" -> entity === arguments?.firstOrNull()
                else -> error("Unsupported entity method: ${method.name}")
            }
        } as EntityAccess
    }

    private fun pointBox(x: Double, y: Double, z: Double): BoxView {
        return Geometry.box(x, y, z, x, y, z)
    }

    private data class RecordingTarget(
        val entity: LivingEntityAccess,
        val executionSources: MutableList<DamageSourceView>,
    )

    private class TestItemStack : ItemStackAccess, PersistentDataAccess {
        private val doubleValues = mutableMapOf<Identifier, Double>()
        private val longValues = mutableMapOf<Identifier, Long>()

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

    private class RecordingWorld : WorldAccess {
        var entitiesInSearch = emptyList<EntityAccess>()
        var entitySearchCount = 0
        var blockRaycastCount = 0
        var lastSearchBox: BoxView? = null

        override val isClientSide = false
        override val loadedEntityCount: Int
            get() = entitiesInSearch.size
        override val entities: Sequence<EntityAccess>
            get() = error("Sonic boom must use the spatial entity query")

        override fun getEntities(searchBox: BoxView): Sequence<EntityAccess> {
            entitySearchCount++
            lastSearchBox = searchBox
            return entitiesInSearch.asSequence()
        }

        override fun getEntityRayBuckets(ray: RayView, length: Double): Sequence<EntityRayBucket> = emptySequence()

        override fun raycastBlockHits(
            ray: RayView,
            distanceBlocks: Double,
            shape: BlockRaycastShape,
        ): Sequence<BlockHitResult> {
            blockRaycastCount++
            return emptySequence()
        }

        override fun destroyBlock(position: BlockPositionView, dropItems: Boolean): Boolean = false

        override fun playSound(position: VectorView, playback: SoundPlayback) {
        }

        override fun spawnParticles(position: VectorView, effect: ParticleEffect) {
        }
    }
}
