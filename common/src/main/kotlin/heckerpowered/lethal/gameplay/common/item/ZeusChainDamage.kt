/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.math.asPointBox
import heckerpowered.bridge.math.expandedBy
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.lethal.gameplay.common.network.ZeusChainSegment
import java.util.*

/**
 * Controls how a Zeus shot traverses living entities within its per-hop chain radius.
 */
enum class ZeusChainMode {
    /**
     * Continues through only the nearest unvisited target at each hop.
     */
    NearestTargetPath,

    /**
     * Branches until every living target reachable through repeated hops has been visited once.
     */
    FullCoverage,
}

internal class ZeusChainDamage(private val radiusBlocks: Double, private val mode: ZeusChainMode) {
    fun apply(firstDamageResult: EntityDamageResult, shotCount: Long): List<ZeusChainSegment> {
        if (firstDamageResult.livingTarget == null) return emptyList()

        val player = firstDamageResult.player
        val startPosition = firstDamageResult.hit.entity.boundingBox.center
        val damagePoints = DAMAGE_POINTS_PER_SHOT * shotCount
        val visitedEntityIds = mutableSetOf(player.id, firstDamageResult.targetEntity.id)
        return when (mode) {
            ZeusChainMode.NearestTargetPath -> applyNearestTargetPath(player, startPosition, visitedEntityIds, firstDamageResult.damageSource, damagePoints)
            ZeusChainMode.FullCoverage -> applyFullCoverageChain(player, startPosition, visitedEntityIds, firstDamageResult.damageSource, damagePoints)
        }
    }

    private fun findAvailableTargets(player: PlayerAccess, origin: VectorView, visitedEntityIds: Set<Int>): Sequence<ChainTarget> {
        val searchBox = origin.asPointBox().expandedBy(radiusBlocks)
        val maximumDistanceSquared = radiusBlocks * radiusBlocks
        return player.world.getEntities(searchBox)
            .mapNotNull { it as? LivingEntityAccess }
            .filter { it.id !in visitedEntityIds && it.isAlive && !it.isRemoved }
            .distinctBy(EntityAccess::id)
            .map { ChainTarget(it, it.boundingBox.center) }
            .filter { it.position.distanceSquaredTo(origin) <= maximumDistanceSquared }
    }

    private fun applyNearestTargetPath(player: PlayerAccess, startPosition: VectorView, visitedEntityIds: MutableSet<Int>, damageSource: DamageSourceView, damagePoints: Double): List<ZeusChainSegment> {
        val chainSegments = mutableListOf<ZeusChainSegment>()
        var origin = startPosition

        while (true) {
            val target = findAvailableTargets(player, origin, visitedEntityIds)
                .minWithOrNull(chainTargetComparator(origin))
                ?: break
            visitedEntityIds += target.entity.id

            target.entity.hurt(damageSource, damagePoints)
            chainSegments += ZeusChainSegment(origin, target.position)
            origin = target.position
        }

        return chainSegments
    }

    private fun applyFullCoverageChain(player: PlayerAccess, startPosition: VectorView, visitedEntityIds: MutableSet<Int>, damageSource: DamageSourceView, damagePoints: Double): List<ZeusChainSegment> {
        val chainSegments = mutableListOf<ZeusChainSegment>()
        val pendingOrigins = ArrayDeque<VectorView>()
        pendingOrigins.addLast(startPosition)

        while (pendingOrigins.isNotEmpty()) {
            val origin = pendingOrigins.removeFirst()
            val targetsBeforeDamage = findAvailableTargets(player, origin, visitedEntityIds)
                .sortedWith(chainTargetComparator(origin))
                .toList()
            for ((entity, position) in targetsBeforeDamage) {
                if (!visitedEntityIds.add(entity.id)) continue

                entity.hurt(damageSource, damagePoints)
                chainSegments += ZeusChainSegment(origin, position)
                pendingOrigins.addLast(position)
            }
        }

        return chainSegments
    }

    private data class ChainTarget(
        val entity: LivingEntityAccess,
        val position: VectorView,
    )

    private companion object {
        const val DAMAGE_POINTS_PER_SHOT = 10_000.0

        fun chainTargetComparator(origin: VectorView): Comparator<ChainTarget> {
            return compareBy<ChainTarget> { it.position.distanceSquaredTo(origin) }
                .thenBy { it.entity.id }
        }
    }
}
