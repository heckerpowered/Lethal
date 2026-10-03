/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityPartAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.lethal.gameplay.common.item.CosmicStarshatterShotgun
import heckerpowered.lethal.gameplay.common.sound.ModSounds
import heckerpowered.math.Box
import heckerpowered.math.Vector
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CosmicStarshatterShotgunTest {
    @Test
    fun registeredGunKeepsGuardsProfileAndOneSoundPerBatch() {
        val target = Receiver(2)
        val scenario = Scenario { listOf(target.entity) }
        assertEquals("lethal:cosmic_starshatter_shotgun", CosmicStarshatterShotgun.identifier.asString())
        assertEquals(0L, CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, 0))
        assertEquals(0, scenario.queries)
        assertTrue(scenario.sounds.isEmpty())
        assertFailsWith<IllegalArgumentException> { CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, -1) }
        assertEquals(2L, CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, 2))
        assertEquals(listOf(4_800_000.0), target.requests)
        assertEquals(12, scenario.queries)
        assertEquals(listOf(ModSounds.ArchaeopteryxFire), scenario.sounds)
        target.sources.forEach { source ->
            assertSame(scenario.player, source.directEntity)
            assertSame(scenario.player, source.causingEntity)
        }
    }

    @Test
    fun batchDamageSumsEveryHitDistanceTierBeforeMultiplyingShotCount() {
        val target = Receiver(2)
        val scenario = Scenario { pellet ->
            target.distanceBlocks = when (pellet % 3) { 0 -> 9.0; 1 -> 12.0; else -> 14.0 }
            listOf(target.entity)
        }
        CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, 2)
        assertEquals(listOf((4 * 200_000.0 + 4 * 3.0 + 4 * 1.0) * 2), target.requests)
    }

    @Test
    fun firingUsesEachDistanceTierAndTheMaximumRange() {
        for ((distance, expectedDamage) in listOf(9.94 to 2_400_000.0, 10.001 to 36.0, 12.92 to 36.0, 13.001 to 12.0, 14.91 to 12.0, 15.001 to 0.0)) {
            val target = Receiver(2)
            target.distanceBlocks = distance
            val scenario = Scenario { listOf(target.entity) }
            CosmicStarshatterShotgun.fire(scenario.player, scenario.stack)
            assertEquals(expectedDamage, target.requests.singleOrNull() ?: 0.0, "Distance $distance")
        }
    }

    @Test
    fun multipartHitsRouteTheirOwnSumsToActualPartsUsingStableIds() {
        val parent = Receiver(2)
        val head = Receiver(3, parent = parent.entity)
        val body = Receiver(4, parent = parent.entity)
        val headAlias = head.view(parent.entity)
        val scenario = Scenario { pellet ->
            if (pellet < 6) {
                head.distanceBlocks = if (pellet % 2 == 0) 9.0 else 12.0
                listOf(if (pellet % 2 == 0) head.entity else headAlias)
            } else {
                body.distanceBlocks = 14.0
                listOf(body.entity)
            }
        }
        CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, 2)
        assertTrue(parent.requests.isEmpty())
        assertEquals(listOf(1_200_018.0), head.requests)
        assertEquals(listOf(12.0), body.requests)
    }

    @Test
    fun partRequestsRemainStableUnderAnOrderSensitiveHostRejectionPolicy() {
        val requestsByOrder = mutableListOf<Map<Int, List<Double>>>()
        val healthLossByOrder = mutableListOf<Double>()
        for (headFirst in listOf(true, false)) {
            var acceptedDamage: Double? = null
            val hostHurt: (Double) -> Boolean = { damage ->
                if (acceptedDamage != null) false else { acceptedDamage = damage; true }
            }
            val parent = Receiver(2)
            val head = Receiver(3, parent = parent.entity, acceptDamage = hostHurt)
            val body = Receiver(4, parent = parent.entity, acceptDamage = hostHurt)
            body.distanceBlocks = 14.0
            val scenario = Scenario { pellet ->
                listOf(if ((pellet < 6) == headFirst) head.entity else body.entity)
            }
            CosmicStarshatterShotgun.fire(scenario.player, scenario.stack)
            assertTrue(parent.requests.isEmpty())
            requestsByOrder += mapOf(3 to head.requests.toList(), 4 to body.requests.toList())
            healthLossByOrder += head.healthLoss + body.healthLoss
            assertEquals(1, (head.accepted + body.accepted).count { it })
        }
        assertEquals(requestsByOrder.first(), requestsByOrder.last())
        assertEquals(listOf(1_200_000.0, 6.0), healthLossByOrder)
    }

    @Test
    fun hostRejectionDoesNotTriggerFallbackOrRetryForTheBatch() {
        val target = Receiver(2, acceptDamage = { false })
        val scenario = Scenario { listOf(target.entity) }
        CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, 3)
        assertEquals(12, scenario.queries)
        assertEquals(listOf(7_200_000.0), target.requests)
        assertEquals(listOf(false), target.accepted)
        assertEquals(0.0, target.healthLoss)
        assertEquals(1, scenario.sounds.size)
    }

    @Test
    fun actualPartHurtRetainsItsHostDamageConversion() {
        val parent = Receiver(2)
        val convertedBodyDamage = mutableListOf<Double>()
        val head = Receiver(3, parent = parent.entity)
        val body = Receiver(4, parent = parent.entity, acceptDamage = { damage ->
            convertedBodyDamage += damage / 4.0 + minOf(damage, 1.0)
            true
        })
        val scenario = Scenario { pellet -> listOf(if (pellet < 6) head.entity else body.entity) }
        CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, 2)
        assertTrue(parent.requests.isEmpty())
        assertEquals(listOf(2_400_000.0), head.requests)
        assertEquals(listOf(2_400_000.0), body.requests)
        assertEquals(listOf(600_001.0), convertedBodyDamage)
    }

    @Test
    fun separateBatchesStillRespectTheHostCooldown() {
        var lastDamage = 0.0
        val appliedDamage = mutableListOf<Double>()
        val target = Receiver(2, acceptDamage = { damage ->
            if (damage <= lastDamage) false else {
                appliedDamage += damage - lastDamage
                lastDamage = damage
                true
            }
        })
        val scenario = Scenario { listOf(target.entity) }
        target.distanceBlocks = 12.0
        CosmicStarshatterShotgun.fire(scenario.player, scenario.stack)
        target.distanceBlocks = 14.0
        CosmicStarshatterShotgun.fire(scenario.player, scenario.stack)
        target.distanceBlocks = 9.0
        CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, 2)
        assertEquals(listOf(36.0, 12.0, 4_800_000.0), target.requests)
        assertEquals(listOf(true, false, true), target.accepted)
        assertEquals(listOf(36.0, 4_799_964.0), appliedDamage)
        assertEquals(36, scenario.queries)
        assertEquals(3, scenario.sounds.size)
    }

    @Test
    fun allQueriesCompleteBeforeAnyHurtAndSoundPrecedesQueries() {
        val target = Receiver(2)
        val scenario = Scenario { listOf(target.entity) }
        target.beforeHurt = {
            assertEquals(12, scenario.queries)
            assertEquals(12, scenario.blockQueries)
            scenario.events += "hurt"
        }
        CosmicStarshatterShotgun.fire(scenario.player, scenario.stack, 2)
        assertEquals(listOf("sound") + List(12) { "query" } + listOf("hurt"), scenario.events)
    }

    @Test
    fun wallsRangeMissesAndFirstTargetSelectionUseTheSharedQuery() {
        val front = Receiver(2)
        val behind = Receiver(3)
        behind.distanceBlocks = 12.0
        val visible = Scenario { listOf(behind.entity, front.entity) }
        CosmicStarshatterShotgun.fire(visible.player, visible.stack)
        assertEquals(listOf(2_400_000.0), front.requests)
        assertTrue(behind.requests.isEmpty())

        val blockedTarget = Receiver(4)
        val blocked = Scenario(blocked = true) { listOf(blockedTarget.entity) }
        CosmicStarshatterShotgun.fire(blocked.player, blocked.stack)
        assertEquals(12, blocked.blockQueries)
        assertTrue(blockedTarget.requests.isEmpty())

        val outOfRange = Receiver(5)
        outOfRange.distanceBlocks = 16.0
        val missed = Scenario { listOf(outOfRange.entity) }
        CosmicStarshatterShotgun.fire(missed.player, missed.stack)
        assertEquals(12, missed.queries)
        assertTrue(outOfRange.requests.isEmpty())
        assertEquals(0, missed.blockQueries)
    }

    private class Receiver(
        private val identifier: Int,
        parent: EntityAccess? = null,
        private val acceptDamage: (Double) -> Boolean = { true },
    ) {
        var distanceBlocks = 9.0
        var healthLoss = 0.0
        var beforeHurt: () -> Unit = {}
        val requests = mutableListOf<Double>()
        val sources = mutableListOf<DamageSourceView>()
        val accepted = mutableListOf<Boolean>()
        val entity: EntityAccess = view(parent)

        fun view(parent: EntityAccess?): EntityAccess {
            val call: (String, Array<out Any?>) -> Any? = { name, arguments ->
                when (name) {
                    "getId" -> identifier
                    "getParent" -> parent ?: error("Ordinary entity has no parent")
                    "getBoundingBox" -> Box(-3.0, -2.0, distanceBlocks, 3.0, 4.0, distanceBlocks + 0.5)
                    "hurt" -> {
                        beforeHurt()
                        sources += arguments[0] as DamageSourceView
                        val damage = arguments[1] as Double
                        requests += damage
                        val wasAccepted = acceptDamage(damage)
                        accepted += wasAccepted
                        if (wasAccepted) healthLoss += damage
                        wasAccepted
                    }
                    else -> error("Unexpected receiver call: $name")
                }
            }
            return if (parent == null) proxy<EntityAccess>(call) else proxy<EntityPartAccess>(call)
        }
    }

    private class Scenario(
        private val blocked: Boolean = false,
        private val targetsForPellet: (Int) -> List<EntityAccess>,
    ) {
        var queries = 0
        var blockQueries = 0
        private var currentTargets = emptyList<EntityAccess>()
        val sounds = mutableListOf<SoundPlayback>()
        val events = mutableListOf<String>()
        private val world = proxy<WorldAccess> { name, arguments ->
            when (name) {
                "getLoadedEntityCount" -> {
                    currentTargets = targetsForPellet(queries++)
                    events += "query"
                    currentTargets.size
                }
                "getEntities" -> currentTargets.asSequence()
                "getEntityRayBuckets" -> sequenceOf(EntityRayBucket(0.0, currentTargets))
                "raycastBlockHits" -> {
                    blockQueries++
                    if (!blocked) emptySequence<BlockHitResult>() else sequenceOf(
                        BlockHitResult(proxy { _, _ -> null }, proxy { _, _ -> null }, BlockDirection.North, Vector(0.0, 1.0, 2.0), 2.0),
                    )
                }
                "playSound" -> { sounds += arguments[1] as SoundPlayback; events += "sound"; Unit }
                else -> error("Unexpected world call: $name")
            }
        }
        val player = proxy<PlayerAccess> { name, _ ->
            when (name) {
                "getId" -> 1
                "getEyePosition" -> Vector(0.0, 1.0, 0.0)
                "getViewVector" -> Vector(0.0, 0.0, 1.0)
                "getWorld" -> world
                else -> error("Unexpected player call: $name")
            }
        }
        val stack = proxy<ItemStackAccess> { name, _ -> error("Unexpected stack call: $name") }
    }
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
