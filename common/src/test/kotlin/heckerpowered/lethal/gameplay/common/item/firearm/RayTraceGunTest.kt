/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityPartAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.math.Box
import heckerpowered.math.BoxView
import heckerpowered.math.RayView
import heckerpowered.math.Vector
import heckerpowered.math.VectorView
import heckerpowered.math.normalized
import heckerpowered.math.plus
import heckerpowered.math.times
import java.lang.reflect.Proxy
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RayTraceGunTest {
    @Test
    fun singleRayRetainsLazyCallbackOrderAndBatchedDamage() {
        val first = Target(2, Box(-1.0, 0.0, 9.0, 1.0, 2.0, 10.0))
        val second = Target(3, Box(-1.0, 0.0, 12.0, 1.0, 2.0, 13.0))
        val scenario = Scenario(listOf(second.entity, first.entity))
        val weapon = object : TestRegularGun() {
            override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
                scenario.events += "callback"
                assertEquals(2L, shotCount)
                val results = EntityHitDamage(VanillaDamageType.Generic, 7.0).apply(player, weaponStack, shotCount, entityHits)
                assertEquals(listOf(2, 3), results.map { it.targetEntity.id })
                assertEquals(listOf(14.0, 14.0), results.map { it.requestedDamagePoints })
            }
        }
        assertEquals(2L, weapon.fire(scenario.player, scenario.stack, 2))
        assertTrue(scenario.events.indexOf("callback") < scenario.events.indexOf("entities"))
        assertEquals(1, first.hurtCalls)
        assertEquals(1, second.hurtCalls)
    }

    @Test
    fun selectivePenetrationAndCompleteBlockBypassRemainAvailable() {
        val first = Target(2, Box(-1.0, 0.0, 9.0, 1.0, 2.0, 10.0))
        val second = Target(3, Box(-1.0, 0.0, 12.0, 1.0, 2.0, 13.0))
        val scenario = Scenario(listOf(first.entity, second.entity), listOf(5.0, 10.0))
        val selective = object : TestRegularGun() {
            override fun isRayBlockedBy(player: PlayerAccess, weaponStack: ItemStackAccess, blockHit: BlockHitResult): Boolean = blockHit.point.z >= 10.0
        }
        selective.fire(scenario.player, scenario.stack)
        assertEquals(listOf(2), selective.hits.map { it.entity.id })
        assertEquals(2, scenario.blockQueries)

        val bypass = object : TestRegularGun() {
            override fun canRayPassThroughAllBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean = true
        }
        val queriesBeforeBypass = scenario.blockQueries
        bypass.fire(scenario.player, scenario.stack)
        assertEquals(listOf(2, 3), bypass.hits.map { it.entity.id })
        assertEquals(queriesBeforeBypass, scenario.blockQueries)
    }

    @Test
    fun shotgunSamplesOneDistributionAndCarriesTheBatchCount() {
        val target = Target(2, Box(-1.0, 0.0, 9.0, 1.0, 2.0, 10.0))
        val scenario = Scenario(listOf(target.entity))
        val weapon = TestShotgun { it }
        assertEquals(0L, weapon.fire(scenario.player, scenario.stack, 0))
        assertEquals(0, weapon.callbacks)
        assertEquals(0, weapon.directionSamples)
        assertFailsWith<IllegalArgumentException> { weapon.fire(scenario.player, scenario.stack, -1) }
        assertEquals(2L, weapon.fire(scenario.player, scenario.stack, 2))
        assertEquals(12, weapon.directionSamples)
        assertEquals(1, weapon.callbacks)
        assertEquals(2L, weapon.batchShotCount)
        assertEquals(12, weapon.entityHits.size)

        val missed = TestShotgun { Vector(1.0, 0.0, 0.0) }
        missed.fire(scenario.player, scenario.stack, 2)
        assertEquals(12, missed.directionSamples)
        assertEquals(1, missed.callbacks)
        assertEquals(2L, missed.batchShotCount)
        assertTrue(missed.entityHits.isEmpty())
        assertEquals(0, target.hurtCalls)
    }

    @Test
    fun shotgunReadsCountAndSpreadOncePerBatchUsingTheCurrentPlayerAndStack() {
        val target = Target(2, Box(-1.0, 0.0, 9.0, 1.0, 2.0, 10.0))
        val scenario = Scenario(listOf(target.entity))
        val firstStack = proxy<ItemStackAccess> { name, _ -> when (name) {
            "getCount" -> 2
            else -> error("Unexpected stack call: $name")
        } }
        val secondStack = proxy<ItemStackAccess> { name, _ -> when (name) {
            "getCount" -> 4
            else -> error("Unexpected stack call: $name")
        } }
        val secondPlayer = proxy<PlayerAccess> { name, _ -> when (name) {
            "getId" -> 8
            "getEyePosition" -> scenario.player.eyePosition
            "getViewVector" -> scenario.player.viewVector
            "getWorld" -> scenario.player.world
            else -> error("Unexpected player call: $name")
        } }
        val contexts = mutableListOf<Pair<PlayerAccess, ItemStackAccess>>()
        val spreadContexts = mutableListOf<Pair<PlayerAccess, ItemStackAccess>>()
        val sampledAngles = mutableListOf<Double>()
        val weapon = object : TestShotgun({ it }) {
            override fun getPelletCount(player: PlayerAccess, weaponStack: ItemStackAccess): Int {
                contexts += player to weaponStack
                return weaponStack.count + if (player.id == 8) 1 else 0
            }
            override fun getSpreadAngle(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
                spreadContexts += player to weaponStack
                return weaponStack.count * 0.01 + if (player.id == 8) 0.1 else 0.0
            }
            override fun samplePelletDirection(forward: VectorView, spreadAngle: Double): VectorView {
                sampledAngles += spreadAngle
                return super.samplePelletDirection(forward, spreadAngle)
            }
        }
        for ((player, stack, expectedCount) in listOf(
            Triple(scenario.player, firstStack, 2),
            Triple(scenario.player, secondStack, 4),
            Triple(secondPlayer, firstStack, 3),
        )) {
            val samplesBefore = weapon.directionSamples
            val queriesBefore = scenario.blockQueries
            val readsBefore = contexts.size
            val expectedAngle = stack.count * 0.01 + if (player.id == 8) 0.1 else 0.0
            weapon.fire(player, stack, 3)
            assertEquals(readsBefore + 1, contexts.size)
            assertSame(player, contexts.last().first)
            assertSame(stack, contexts.last().second)
            assertEquals(readsBefore + 1, spreadContexts.size)
            assertSame(player, spreadContexts.last().first)
            assertSame(stack, spreadContexts.last().second)
            assertEquals(List(expectedCount) { expectedAngle }, sampledAngles.takeLast(expectedCount))
            assertEquals(expectedCount, weapon.directionSamples - samplesBefore)
            assertEquals(expectedCount, scenario.blockQueries - queriesBefore)
            assertEquals(expectedCount, weapon.entityHits.size)
            assertEquals(3L, weapon.batchShotCount)
        }
    }

    @Test
    fun shotgunValidatesDynamicPelletCountBeforeGeneratingRays() {
        val scenario = Scenario(emptyList())
        var count = 0
        var reads = 0
        var spreadReads = 0
        var rangeReads = 0
        val weapon = object : TestShotgun({ it }) {
            override fun getPelletCount(player: PlayerAccess, weaponStack: ItemStackAccess): Int {
                reads++
                return count
            }
            override fun getSpreadAngle(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
                spreadReads++
                return super.getSpreadAngle(player, weaponStack)
            }
            override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
                rangeReads++
                return super.getRayTraceDistanceBlocks(player, weaponStack)
            }
        }
        assertEquals(0L, weapon.fire(scenario.player, scenario.stack, 0))
        assertFailsWith<IllegalArgumentException> { weapon.fire(scenario.player, scenario.stack, -1) }
        assertFailsWith<IllegalArgumentException> { weapon.shoot(scenario.player, scenario.stack, -1) }
        weapon.shoot(scenario.player, scenario.stack, 0)
        assertEquals(0, reads)
        assertEquals(0, spreadReads)
        assertEquals(0, rangeReads)
        assertEquals(0, weapon.directionSamples)
        assertEquals(0, weapon.callbacks)
        assertEquals(0, scenario.blockQueries)
        assertTrue(scenario.events.isEmpty())

        assertFailsWith<IllegalArgumentException> { weapon.fire(scenario.player, scenario.stack) }
        assertEquals(1, reads)
        count = -1
        assertFailsWith<IllegalArgumentException> { weapon.fire(scenario.player, scenario.stack) }
        assertEquals(2, reads)
        assertEquals(0, spreadReads)
        assertEquals(0, rangeReads)
        assertEquals(0, weapon.directionSamples)
        assertEquals(0, weapon.callbacks)
        assertTrue(scenario.events.isEmpty())

        count = 3
        weapon.shoot(scenario.player, scenario.stack, 0)
        assertEquals(2, reads)
        assertEquals(0, spreadReads)
        assertEquals(0, rangeReads)
        assertEquals(0, weapon.directionSamples)
        assertEquals(0, weapon.callbacks)
        weapon.fire(scenario.player, scenario.stack, 2)
        assertEquals(3, reads)
        assertEquals(1, spreadReads)
        assertEquals(1, rangeReads)
        assertEquals(3, weapon.directionSamples)
        assertEquals(1, weapon.callbacks)
        assertEquals(2L, weapon.batchShotCount)
        assertTrue(weapon.entityHits.isEmpty())
    }

    @Test
    fun shotgunSelectsOnlyTheFirstTargetOfEachRayAndKeepsRepeatedHits() {
        val left = Target(2, Box(-2.2, 0.0, 9.0, -1.8, 2.0, 9.5))
        val right = Target(3, Box(1.8, 0.0, 9.0, 2.2, 2.0, 9.5))
        val behind = Target(4, Box(-3.0, 0.0, 12.0, -2.2, 2.0, 13.0))
        val scenario = Scenario(listOf(left.entity, right.entity, behind.entity))
        val directions = (List(6) { Vector(-2.0, 0.0, 9.0).normalized() } + List(3) { Vector(2.0, 0.0, 9.0).normalized() } + List(3) { Vector(0.0, 0.0, 1.0) }).iterator()
        val weapon = TestShotgun { directions.next() }
        weapon.fire(scenario.player, scenario.stack)
        val hits = weapon.entityHits.groupBy { it.entity.id }
        assertEquals(setOf(2, 3), hits.keys)
        assertEquals(6, hits.getValue(2).size)
        assertEquals(3, hits.getValue(3).size)
        assertEquals(0, behind.hurtCalls)
    }

    @Test
    fun shotgunChecksWallsIndependentlyForEveryPellet() {
        val target = Target(2, Box(-1.0, 0.0, 9.0, 1.0, 2.0, 10.0))
        val scenario = Scenario(listOf(target.entity), listOf(5.0)) { it.direction.x < 0.0 }
        val directions = (List(6) { Vector(-0.45, 0.0, 9.0).normalized() } + List(6) { Vector(0.45, 0.0, 9.0).normalized() }).iterator()
        val weapon = TestShotgun { directions.next() }
        weapon.fire(scenario.player, scenario.stack)
        assertEquals(12, scenario.blockQueries)
        assertEquals(6, weapon.entityHits.size)
        assertEquals(0, target.hurtCalls)
    }

    @Test
    fun shotgunRetainsRepeatedActualPartHitsAndTheirLogicalParents() {
        val firstParentView = Target(2, Box(-1.0, 0.0, 9.0, 1.0, 2.0, 15.0))
        val secondParentView = Target(2, firstParentView.box)
        val head = part(3, firstParentView.entity, Box(-0.65, 0.0, 9.0, -0.25, 2.0, 9.5))
        val body = part(4, secondParentView.entity, Box(0.25, 0.0, 14.0, 0.65, 2.0, 14.5))
        val scenario = Scenario(listOf(head, body))
        val directions = (List(6) { Vector(-0.45, 0.0, 9.0).normalized() } + List(6) { Vector(0.45, 0.0, 14.0).normalized() }).iterator()
        val weapon = TestShotgun { directions.next() }
        weapon.fire(scenario.player, scenario.stack)
        val hits = weapon.entityHits
        assertEquals(setOf(2), hits.map { (it.entity as EntityPartAccess).parent.id }.toSet())
        assertEquals(12, hits.size)
        hits.take(6).forEach { assertSame(head, it.entity) }
        hits.drop(6).forEach { assertSame(body, it.entity) }
        hits.take(6).forEach { assertEquals(Vector(-0.45, 0.0, 9.0).length, it.intersection.ray.origin.distanceTo(it.point), 1.0e-12) }
        hits.drop(6).forEach { assertEquals(Vector(0.45, 0.0, 14.0).length, it.intersection.ray.origin.distanceTo(it.point), 1.0e-12) }
        assertEquals(0, firstParentView.hurtCalls)
        assertEquals(0, secondParentView.hurtCalls)
    }

    @Test
    fun shotgunPreservesPhysicalHitDistancesThroughItsRangeBoundary() {
        for (distance in listOf(10.0, Math.nextUp(10.0), 13.0, Math.nextUp(13.0), 15.0, Math.nextUp(15.0))) {
            val target = Target(2, Box(-1.0, 0.0, distance, 1.0, 2.0, distance + 1.0))
            val scenario = Scenario(listOf(target.entity))
            val weapon = TestShotgun { Vector(0.0, 0.0, 2.0) }
            weapon.fire(scenario.player, scenario.stack)
            val hits = weapon.entityHits
            assertEquals(if (distance <= 15.0) 12 else 0, hits.size, "Distance $distance")
            hits.forEach { assertEquals(distance, it.intersection.ray.origin.distanceTo(it.point), 1.0e-12) }
            assertEquals(0, target.hurtCalls)
        }
    }

    @Test
    fun shotgunExcludesBothShooterAndShooterParts() {
        val scenario = Scenario(emptyList())
        val shooterPart = part(8, scenario.player, Box(-1.0, 0.0, 2.0, 1.0, 2.0, 3.0))
        scenario.targets = listOf(scenario.player, shooterPart)
        val weapon = TestShotgun { it }
        weapon.fire(scenario.player, scenario.stack)
        assertTrue(weapon.entityHits.isEmpty())
        assertEquals(0, scenario.blockQueries)
    }

    @Test
    fun shotgunSamplesAndQueriesOnlyConsumedRaysAndRepeatsWorkOnTraversal() {
        val target = Target(2, Box(-1.0, 0.0, 9.0, 1.0, 2.0, 10.0))
        val scenario = Scenario(listOf(target.entity))
        val weapon = object : TestShotgun({ forward -> scenario.events += "sample"; forward }) {
            override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
                assertTrue(scenario.events.isEmpty())
                assertEquals(2L, shotCount)
                assertEquals(0, directionSamples)
                assertEquals(0, scenario.blockQueries)
                scenario.events += "callback"

                val firstHit = entityHits.take(1).toList()
                assertEquals(1, firstHit.size)
                assertEquals(1, directionSamples)
                assertEquals(1, scenario.blockQueries)
                assertEquals(listOf("callback", "sample", "entities"), scenario.events)

                super.onRayTrace(player, weaponStack, shotCount, entityHits)
                assertEquals(12, this.entityHits.size)
                assertEquals(13, directionSamples)
                assertEquals(13, scenario.blockQueries)

                assertEquals(12, entityHits.toList().size)
                assertEquals(25, directionSamples)
                assertEquals(25, scenario.blockQueries)
                assertEquals(listOf("callback") + List(25) { listOf("sample", "entities") }.flatten(), scenario.events)
            }
        }
        weapon.fire(scenario.player, scenario.stack, 2)
        assertEquals(12, weapon.entityHits.size)
        assertEquals(2L, weapon.batchShotCount)
        assertEquals(0, target.hurtCalls)
    }

    private open class TestRegularGun : RegularGun() {
        override val identifier: Identifier = Constants.identifier("test_single_ray")
        var hits = emptyList<EntityRayHit>()
        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency = Frequency.perSecond(2)
        override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double = 15.0
        override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
            hits = entityHits.toList()
        }
    }

    private open class TestShotgun(private val direction: (VectorView) -> VectorView) : Shotgun() {
        override val identifier: Identifier = Constants.identifier("test_shotgun")
        var directionSamples = 0
        var callbacks = 0
        var batchShotCount = 0L
        var entityHits = emptyList<EntityRayHit>()
        override fun getPelletCount(player: PlayerAccess, weaponStack: ItemStackAccess): Int = 12
        override fun getSpreadAngle(player: PlayerAccess, weaponStack: ItemStackAccess): Double = 6.0 * PI / 180.0
        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency = Frequency.perSecond(2)
        override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double = 15.0
        override fun samplePelletDirection(forward: VectorView, spreadAngle: Double): VectorView {
            directionSamples++
            return direction(forward)
        }
        override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
            callbacks++
            batchShotCount = shotCount
            this.entityHits = entityHits.toList()
        }
    }

    private class Target(identifier: Int, val box: BoxView) {
        var hurtCalls = 0
        var health = 10_000_000.0
        val entity = proxy<LivingEntityAccess> { name, arguments -> when (name) {
            "getId" -> identifier
            "getBoundingBox" -> box
            "getHealth" -> health
            "hurt" -> { hurtCalls++; health -= arguments[1] as Double; true }
            else -> error("Unexpected target call: $name")
        } }
    }

    private class Scenario(var targets: List<EntityAccess>, private val wallDistances: List<Double> = emptyList(), private val wallsApply: (RayView) -> Boolean = { true }) {
        var blockQueries = 0
        val events = mutableListOf<String>()
        private fun entities() = sequence { events += "entities"; yieldAll(targets) }
        val world = proxy<WorldAccess> { name, arguments -> when (name) {
            "getLoadedEntityCount" -> targets.size
            "getEntities" -> entities()
            "getEntityRayBuckets" -> sequence { events += "entities"; yield(EntityRayBucket(0.0, targets)) }
            "raycastBlockHits" -> {
                blockQueries++
                val ray = arguments[0] as RayView
                val length = arguments[1] as Double
                if (!wallsApply(ray)) emptySequence<BlockHitResult>() else wallDistances.filter { it >= ray.origin.z && it <= ray.origin.z + ray.direction.normalized().z * length }.map { wallDistance ->
                    val time = (wallDistance - ray.origin.z) / ray.direction.z
                    BlockHitResult(proxy { _, _ -> null }, proxy { _, _ -> null }, BlockDirection.North, ray.origin + ray.direction * time, time)
                }.asSequence()
            }
            else -> error("Unexpected world call: $name")
        } }
        val player = proxy<PlayerAccess> { name, _ -> when (name) {
            "getId" -> 1
            "getEyePosition" -> Vector(0.0, 1.0, 0.0)
            "getViewVector" -> Vector(0.0, 0.0, 1.0)
            "getWorld" -> world
            else -> error("Unexpected player call: $name")
        } }
        val stack = proxy<ItemStackAccess> { name, _ -> error("Unexpected stack call: $name") }
    }

    private fun part(identifier: Int, parent: EntityAccess, box: BoxView): EntityPartAccess = proxy { name, _ -> when (name) {
        "getId" -> identifier
        "getParent" -> parent
        "getBoundingBox" -> box
        else -> error("Unexpected part call: $name")
    } }

}

private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T {
    return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, arguments ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.firstOrNull()
            else -> call(method.name, arguments ?: emptyArray())
        }
    } as T
}
