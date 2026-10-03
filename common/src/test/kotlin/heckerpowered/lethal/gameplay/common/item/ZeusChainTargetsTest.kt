/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.math.Geometry
import heckerpowered.math.intersect
import java.lang.reflect.Proxy
import kotlin.jvm.java
import kotlin.test.Test
import kotlin.test.assertEquals

private inline fun <reified T> chainProxy(crossinline operation: (String) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> operation(method.name) } as T

private data class Node(val id: Int, val x: Double, val y: Double = 0.0, val alive: Boolean = true, val removed: Boolean = false)

private fun verifyTargets(name: String, mode: ZeusChainMode, nodes: List<Node>, expected: List<Int>, expectedOrigins: List<Double>? = null) {
    val hits = mutableListOf<Int>()
    val all = listOf(Node(100, 0.0)) + nodes
    val entities = all.map { node ->
        var health = if (node.alive) 20.0 else 0.0
        val position = Geometry.vector(node.x, node.y, 0.0)
        val box = Geometry.box(Geometry.vector(node.x - 0.125, node.y - 0.125, -0.125), Geometry.vector(node.x + 0.125, node.y + 0.125, 0.125))
        chainProxy<LivingEntityAccess> { method ->
            when (method) {
                "getId" -> node.id
                "getHealth" -> health
                "getMaximumHealth" -> 20.0
                "getPosition" -> position
                "getBoundingBox" -> box
                "isAlive" -> health > 0.0
                "isRemoved" -> node.removed
                "hurt" -> {
                    hits += node.id; health -= 1.0; true
                }

                else -> error("Unexpected entity operation: $method")
            }
        }
    }
    val world = chainProxy<WorldAccess> { method ->
        when (method) {
            "getEntities" -> entities.asSequence()
            else -> error("Unexpected world operation: $method")
        }
    }
    val player = chainProxy<PlayerAccess> { method ->
        when (method) {
            "getId" -> 0
            "getWorld" -> world
            else -> error("Unexpected player operation: $method")
        }
    }
    val first = entities.first()
    val ray = Geometry.ray(Geometry.vector(-1.0, 0.0, 0.0), Geometry.vector(1.0, 0.0, 0.0))
    val hit = EntityRayHit(first, requireNotNull(first.boundingBox.intersect(ray)))
    val source = chainProxy<DamageSourceView> { error("Unexpected damage source access: $it") }
    val stack = chainProxy<ItemStackAccess> { error("Unexpected stack access: $it") }
    val result = EntityDamageResult(player, stack, hit, first, first, source, 1.0, 1.0, true)
    val chain = ZeusChainTargets(2.0, mode)
    assertEquals(emptyList(), chain.findTargets(result.copy(livingTarget = null)).toList())
    val targets = chain.findTargets(result)
    repeat(2) {
        val selected = targets.toList()
        assertEquals(expected, selected.map { it.entity.id }, "$name/$mode")
        assertEquals(expected.size, selected.size)
        assertEquals(selected.map { it.entity.boundingBox.center }, selected.map { it.segment.endPosition })
        if (expectedOrigins != null) assertEquals(expectedOrigins, selected.map { it.segment.startPosition.x })
        assertEquals(emptyList(), hits, "Selecting targets must not cause damage")
    }
}

class ZeusChainTargetsTest {
    @Test
    fun preservesBothModesAcrossBranchLineAndCycleWithAnyCandidateOrder() {
        val graphs = listOf(
            Triple("branch", listOf(Node(1, 1.0), Node(2, -1.0), Node(3, 2.5)), listOf(1, 3) to listOf(1, 2, 3)),
            Triple("line", listOf(Node(1, 1.0), Node(2, 2.5), Node(3, 4.0)), listOf(1, 2, 3) to listOf(1, 2, 3)),
            Triple("cycle", listOf(Node(1, 1.0), Node(2, 0.0, 1.0), Node(3, 1.0, 1.0)), listOf(1, 3, 2) to listOf(1, 2, 3)),
        )
        for ([name, nodes, expected] in graphs) {
            for (candidates in listOf(nodes, nodes.reversed(), nodes + nodes.reversed())) {
                for (mode in ZeusChainMode.entries) {
                    verifyTargets(name, mode, candidates, if (mode == ZeusChainMode.NearestTargetPath) expected.first else expected.second)
                }
            }
        }
    }

    @Test
    fun ignoresEmptyUnreachableDeadRemovedAndExcludedEntityIds() {
        for (mode in ZeusChainMode.entries) {
            verifyTargets("no-candidates", mode, emptyList(), emptyList())
            verifyTargets("unreachable", mode, listOf(Node(1, 3.0)), emptyList())
            verifyTargets("dead-and-removed", mode, listOf(Node(1, 1.0, alive = false), Node(2, -1.0, removed = true)), emptyList())
            verifyTargets("player-and-first-target", mode, listOf(Node(0, 1.0), Node(100, -1.0)), emptyList())
        }
    }

    @Test
    fun segmentsFollowTheNearestPathOrFullCoverageBranches() {
        val nodes = listOf(Node(1, 1.0), Node(2, -1.0), Node(3, 2.5))
        verifyTargets("path-segments", ZeusChainMode.NearestTargetPath, nodes, listOf(1, 3), listOf(0.0, 1.0))
        verifyTargets("branch-segments", ZeusChainMode.FullCoverage, nodes, listOf(1, 2, 3), listOf(0.0, 0.0, 1.0))
    }

    @Test
    fun usesSphericalDistanceIncludingTheRadiusBoundary() {
        for (mode in ZeusChainMode.entries) {
            verifyTargets("outside-sphere-inside-box", mode, listOf(Node(1, 1.5, 1.5)), emptyList())
            verifyTargets("at-radius", mode, listOf(Node(1, 2.0)), listOf(1))
        }
    }
}
