/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
import heckerpowered.bridge.adapter.entity.EntityPartAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.math.Box
import heckerpowered.math.Ray
import heckerpowered.math.Vector
import heckerpowered.math.intersect
import heckerpowered.math.plus
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZeusEnergyChargingTest {
    @Test
    fun chargesDirectAndBothChainModesOncePerDamageEvenWithDuplicateCandidates() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val first = Target(1, 1.0, 5.0) { -10.0 }
            val second = Target(2, 2.0, 7.0) { -10.0 }
            val third = Target(3, 3.0, 9.0) { -10.0 }
            val stack = EnergyTestStack()
            val player = player(stack, listOf(first, first, second, second, third, third))
            val hit = first.hit()

            weapon.trace(player, stack, sequenceOf(hit, hit))

            assertEquals(21.0, weapon.energy.currentEnergyPoints(stack), mode.name)
            assertEquals(listOf(1, 1, 1), listOf(first.hurtCount, second.hurtCount, third.hurtCount), mode.name)
        }
    }

    @Test
    fun immunityAndRestoredHealthDoNotRechargeEvenWhenDamageIsReportedAccepted() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val first = Target(1, 1.0, 5.0) { 2.0 }
            val immune = Target(2, 2.0, 20.0) { it }
            val restored = Target(3, 3.0, 20.0) { it + 1.0 }
            val stack = EnergyTestStack()

            weapon.trace(player(stack, listOf(first, immune, restored)), stack, sequenceOf(first.hit()))

            assertEquals(3.0, weapon.energy.currentEnergyPoints(stack), mode.name)
            assertEquals(listOf(1, 1, 1), listOf(first.hurtCount, immune.hurtCount, restored.hurtCount), mode.name)
        }
    }

    @Test
    fun decreasingMaximumHealthDuringDamageDoesNotReduceActualLoss() {
        val weapon = TestZeus(ZeusChainMode.NearestTargetPath)
        lateinit var first: Target
        first = Target(1, 1.0, 20.0) { first.maximumHealth = 5.0; 0.0 }
        val stack = EnergyTestStack()

        weapon.trace(player(stack, listOf(first)), stack, sequenceOf(first.hit()))

        assertEquals(5.0, first.maximumHealth)
        assertEquals(20.0, weapon.energy.currentEnergyPoints(stack))
    }

    @Test
    fun completedTargetsRechargeBeforeLaterDamageThrows() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val stack = EnergyTestStack()
            val first = Target(1, 0.0, 5.0) { 0.0 }
            val second = Target(2, 1.0, 7.0) {
                assertEquals(5.0, weapon.energy.currentEnergyPoints(stack))
                0.0
            }
            val failing = Target(3, 2.5, 9.0) {
                assertEquals(12.0, weapon.energy.currentEnergyPoints(stack))
                error("Damage failed")
            }

            assertFailsWith<IllegalStateException> {
                weapon.trace(player(stack, listOf(first, second, failing)), stack, sequenceOf(first.hit()))
            }

            assertEquals(12.0, weapon.energy.currentEnergyPoints(stack), mode.name)
            assertEquals(listOf(1, 1, 1), listOf(first.hurtCount, second.hurtCount, failing.hurtCount), mode.name)
        }
    }

    @Test
    fun healthChangedByAThrowingHurtDoesNotChargeWithoutAMeasurementReturn() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val stack = EnergyTestStack()
            val first = Target(1, 0.0, 5.0) { 0.0 }
            lateinit var failing: Target
            failing = Target(2, 1.0, 7.0) {
                failing.health = 0.0
                error("Failed after changing health")
            }

            assertFailsWith<IllegalStateException> {
                weapon.trace(player(stack, listOf(first, failing)), stack, sequenceOf(first.hit()))
            }

            assertEquals(0.0, failing.health, mode.name)
            assertEquals(5.0, weapon.energy.currentEnergyPoints(stack), mode.name)
        }
    }

    @Test
    fun completedTargetsRechargeBeforeLaterSearchThrows() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val stack = EnergyTestStack()
            val first = Target(1, 0.0, 5.0) { 0.0 }
            val second = Target(2, 1.0, 7.0) { 0.0 }
            var queryCount = 0
            val player = player(stack, listOf(first, second)) {
                queryCount++
                if (queryCount == 3) error("Search failed")
            }

            assertFailsWith<IllegalStateException> {
                weapon.trace(player, stack, sequenceOf(first.hit()))
            }

            assertEquals(12.0, weapon.energy.currentEnergyPoints(stack), mode.name)
            assertEquals(3, queryCount, mode.name)
        }
    }

    @Test
    fun laterSearchesObserveSpawnAndMovementWhileFullCoverageKeepsItsCurrentBatch() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val stack = EnergyTestStack()
            val first = Target(1, 0.0, 5.0) { 0.0 }
            val removed = Target(3, -1.0, 9.0) { 0.0 }
            val spawned = Target(4, 2.5, 11.0) { 0.0 }
            val targets = mutableListOf(first, removed)
            lateinit var second: Target
            second = Target(2, 1.0, 7.0) {
                removed.removed = true
                second.position = Vector(0.0, 0.0, 50.0)
                targets += spawned
                0.0
            }
            targets += second

            weapon.trace(player(stack, targets), stack, sequenceOf(first.hit()))

            val removedHitCount = if (mode == ZeusChainMode.FullCoverage) 1 else 0
            assertEquals(removedHitCount, removed.hurtCount, mode.name)
            assertEquals(1, spawned.hurtCount, mode.name)
            assertEquals(23.0 + 9.0 * removedHitCount, weapon.energy.currentEnergyPoints(stack), mode.name)
        }
    }

    @Test
    fun captureWorldChangesHappenBeforeChainSelectionAndChainsDoNotCaptureAgain() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val stack = EnergyTestStack()
            val first = Target(1, 0.0, 5.0) { 0.0 }
            val removed = Target(2, 1.0, 7.0) { 0.0 }
            val last = Target(3, 1.5, 9.0) { 0.0 }
            var queryCount = 0
            val player = player(stack, listOf(first, removed, last)) {
                queryCount++
                if (queryCount == 1) removed.removed = true
            }

            weapon.trace(player, stack, sequenceOf(first.hit()))

            assertEquals(0, removed.hurtCount, mode.name)
            assertEquals(1, last.hurtCount, mode.name)
            assertEquals(14.0, weapon.energy.currentEnergyPoints(stack), mode.name)
            assertEquals(3, queryCount, mode.name)
        }
    }

    @Test
    fun rejectedDamageStillContinuesTheChainAndScalesRequestsByShotCount() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val stack = EnergyTestStack()
            val first = Target(1, 0.0, 5.0) { 0.0 }
            val immune = Target(2, 1.5, 7.0) { it }.apply { acceptsDamage = false }
            val last = Target(3, 3.0, 9.0) { 0.0 }

            weapon.trace(player(stack, listOf(first, immune, last)), stack, sequenceOf(first.hit()), 2)

            assertEquals(14.0, weapon.energy.currentEnergyPoints(stack), mode.name)
            assertEquals(listOf(400_000.0, 20_000.0, 20_000.0), listOf(first.requestedDamage, immune.requestedDamage, last.requestedDamage), mode.name)
            assertEquals(1, last.hurtCount, mode.name)
        }
    }

    @Test
    fun multipartHitsExcludeTheLogicalParentButStartTheChainAtTheHitPart() {
        for (mode in ZeusChainMode.entries) {
            val weapon = TestZeus(mode)
            val stack = EnergyTestStack()
            lateinit var parent: Target
            parent = Target(1, 50.0, 5.0) {
                parent.position = Vector(0.0, 0.0, 1.5)
                2.0
            }
            val partBox = Box(Vector(0.0, 0.0, 0.0), Vector(0.5, 0.5, 0.5))
            var partHitCount = 0
            val part = Proxy.newProxyInstance(EntityPartAccess::class.java.classLoader, arrayOf(EntityPartAccess::class.java)) { _, method, arguments ->
                when (method.name) {
                    "getParent" -> parent.entity
                    "getBoundingBox" -> partBox
                    "hurt" -> { partHitCount++; parent.entity.hurt(arguments!![0] as DamageSourceView, arguments[1] as Double) }
                    else -> error("Unexpected part operation: ${method.name}")
                }
            } as EntityPartAccess
            val ray = Ray(Vector(0.25, 0.25, -1.0), Vector(0.0, 0.0, 1.0))
            val hit = EntityRayHit(part, requireNotNull(partBox.intersect(ray)))
            val chained = Target(2, 1.0, 7.0) { 0.0 }

            weapon.trace(player(stack, listOf(parent, chained)), stack, sequenceOf(hit, hit))

            assertEquals(1, partHitCount, mode.name)
            assertEquals(1, parent.hurtCount, mode.name)
            assertEquals(1, chained.hurtCount, mode.name)
            assertEquals(10.0, weapon.energy.currentEnergyPoints(stack), mode.name)
        }
    }

    private class TestZeus(mode: ZeusChainMode) : Zeus("energy_test", 200_000.0, chainMode = mode) {
        fun trace(player: PlayerAccess, stack: ItemStackAccess, hits: Sequence<EntityRayHit>, shotCount: Long = 1) {
            onRayTrace(player, stack, shotCount, hits)
        }
    }

    private class Target(
        val id: Int,
        distanceBlocks: Double,
        initialHealth: Double,
        private val healthAfterHit: (Double) -> Double,
    ) {
        var health = initialHealth
        var maximumHealth = 20.0
        var hurtCount = 0
            private set
        var position = Vector(0.0, 0.0, distanceBlocks)
        private val boundingBox get() = Box(position, position + Vector(0.5, 0.5, 0.5))
        var removed = false
        var acceptsDamage = true
        var requestedDamage = 0.0
        val entity = Proxy.newProxyInstance(LivingEntityAccess::class.java.classLoader, arrayOf(LivingEntityAccess::class.java)) { _, method, arguments ->
            when (method.name) {
                "getId" -> id
                "getHealth" -> health
                "getMaximumHealth" -> maximumHealth
                "getPosition" -> position
                "getBoundingBox" -> boundingBox
                "isAlive" -> health > 0.0
                "isRemoved" -> removed
                "hurt" -> { hurtCount++; requestedDamage = arguments!![1] as Double; health = healthAfterHit(health); acceptsDamage }
                else -> error("Unexpected target operation: ${method.name}")
            }
        } as LivingEntityAccess

        fun hit(): EntityRayHit {
            val ray = Ray(Vector(0.25, 0.25, 0.0), Vector(0.0, 0.0, 1.0))
            return EntityRayHit(entity, requireNotNull(boundingBox.intersect(ray)))
        }
    }

    private fun player(stack: ItemStackAccess, targets: List<Target>, beforeQuery: () -> Unit = {}): PlayerAccess {
        val world = Proxy.newProxyInstance(WorldAccess::class.java.classLoader, arrayOf(WorldAccess::class.java)) { _, method, _ ->
            when (method.name) {
                "isClientSide" -> false
                "getEntities" -> { beforeQuery(); targets.asSequence().map(Target::entity) }
                "playSound" -> Unit
                else -> error("Unexpected world operation: ${method.name}")
            }
        } as WorldAccess
        return Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java, EntityEquipmentAccess::class.java)) { _, method, _ ->
            when (method.name) {
                "getId" -> 0
                "getWorld" -> world
                "getEyePosition" -> Vector(0.0, 0.0, 0.0)
                "getEquippedStack" -> stack
                else -> error("Unexpected player operation: ${method.name}")
            }
        } as PlayerAccess
    }
}
