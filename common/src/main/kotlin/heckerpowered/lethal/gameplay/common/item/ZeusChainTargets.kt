/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.lethal.gameplay.common.network.ZeusChainSegment
import heckerpowered.math.VectorView
import heckerpowered.math.asPointBox
import heckerpowered.math.expandedBy
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

class ZeusChainTargets(private val radiusBlocks: Double, private val mode: ZeusChainMode) {
    /**
     * Selects targets as the caller advances the chain. Apply each target's damage before requesting
     * the next target so later searches observe world changes. Full coverage keeps the candidates
     * found at each origin as a snapshot, including targets changed by earlier damage in that batch.
     */
    fun findTargets(firstDamageResult: EntityDamageResult): Sequence<ZeusChainTarget> = sequence {
        if (firstDamageResult.livingTarget == null) return@sequence

        val player = firstDamageResult.player
        val startPosition = firstDamageResult.hit.entity.boundingBox.center
        val visitedEntityIds = mutableSetOf(player.id, firstDamageResult.targetEntity.id)
        yieldAll(
            when (mode) {
                ZeusChainMode.NearestTargetPath -> findNearestTargetPath(player, startPosition, visitedEntityIds)
                ZeusChainMode.FullCoverage -> findFullCoverageChain(player, startPosition, visitedEntityIds)
            }
        )
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

    private fun findNearestTargetPath(player: PlayerAccess, startPosition: VectorView, visitedEntityIds: MutableSet<Int>): Sequence<ZeusChainTarget> = sequence {
        var origin = startPosition

        while (true) {
            val target = findAvailableTargets(player, origin, visitedEntityIds)
                .minWithOrNull(chainTargetComparator(origin))
                ?: break
            visitedEntityIds += target.entity.id

            yield(ZeusChainTarget(target.entity, ZeusChainSegment(origin, target.position)))
            origin = target.position
        }
    }

    private fun findFullCoverageChain(player: PlayerAccess, startPosition: VectorView, visitedEntityIds: MutableSet<Int>): Sequence<ZeusChainTarget> = sequence {
        val pendingOrigins = ArrayDeque<VectorView>()
        pendingOrigins.addLast(startPosition)

        while (pendingOrigins.isNotEmpty()) {
            val origin = pendingOrigins.removeFirst()
            val targetsAtOrigin = findAvailableTargets(player, origin, visitedEntityIds)
                .sortedWith(chainTargetComparator(origin))
                .toList()
            for ((entity, position) in targetsAtOrigin) {
                if (!visitedEntityIds.add(entity.id)) continue

                yield(ZeusChainTarget(entity, ZeusChainSegment(origin, position)))
                pendingOrigins.addLast(position)
            }
        }
    }

    private data class ChainTarget(
        val entity: LivingEntityAccess,
        val position: VectorView,
    )

    private companion object {
        fun chainTargetComparator(origin: VectorView): Comparator<ChainTarget> {
            return compareBy<ChainTarget> { it.position.distanceSquaredTo(origin) }
                .thenBy { it.entity.id }
        }
    }
}

data class ZeusChainTarget(
    val entity: LivingEntityAccess,
    val segment: ZeusChainSegment,
)
