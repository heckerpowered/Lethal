/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.asView
import heckerpowered.bridge.adapter.entity.*
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.EquipmentSlot
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.math.asPointBox
import heckerpowered.bridge.math.expandedBy
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.CaptureEntityHitEffect
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitDamage
import heckerpowered.lethal.gameplay.common.item.firearm.RayTraceGun
import heckerpowered.lethal.gameplay.common.network.ZeusChainPayload
import heckerpowered.lethal.gameplay.common.network.ZeusChainSegment
import heckerpowered.lethal.gameplay.common.network.ZeusShotPayload
import heckerpowered.lethal.gameplay.common.sound.ModSounds
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

open class Zeus protected constructor(identifierPath: String, damagePointsPerShot: Double, private val rayTraceDistanceBlocks: Double = 70.0, private val chainRadiusBlocks: Double = 2.0, private val chainMode: ZeusChainMode = ZeusChainMode.NearestTargetPath) : RayTraceGun() {
    companion object : Zeus("zeus", 200_000.0)

    private val hitDamage = EntityHitDamage(VanillaDamageType.FellOutOfWorld, damagePointsPerShot, CaptureEntityHitEffect)

    final override val identifier: Identifier = Constants.identifier(identifierPath)

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
        return Frequency.perSecond(2)
    }

    override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
        return rayTraceDistanceBlocks
    }

    override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
        player.world.playSound(player.eyePosition, ModSounds.ZeusFire)
        val firstDamageResult = hitDamage.apply(player, weaponStack, shotCount, entityHits)
        val mainTarget = firstDamageResult?.targetEntity
        val chainStartPosition = if (firstDamageResult?.livingTarget != null) firstDamageResult.hit.entity.boundingBox.center else null
        val chainPayload = if (mainTarget == null || chainStartPosition == null) null else applyChainDamage(player, mainTarget.id, chainStartPosition, shotCount)

        val equipment = player.asView<EntityEquipmentAccess>()
        val isMainHand = equipment.getEquippedStack(EquipmentSlot.MainHand) === weaponStack
        val serverPlayer = player as? ServerPlayerAccess ?: return
        Services.PayloadTransport.sendToPlayer(serverPlayer, ZeusShotPayload(isMainHand))
        if (chainPayload != null) {
            Services.PayloadTransport.sendToPlayersTracking(serverPlayer, chainPayload)
        }
        // TODO: move the chain logic to something like hit effect through combination
    }

    private fun findAvailableChainTargets(player: PlayerAccess, chainOrigin: VectorView, visitedEntityIds: Set<Int>): Sequence<ChainTarget> {
        val searchBox = chainOrigin.asPointBox().expandedBy(chainRadiusBlocks)
        val maximumDistanceSquared = chainRadiusBlocks * chainRadiusBlocks
        return player.world.getEntities(searchBox)
            .mapNotNull { it as? LivingEntityAccess }
            .filter { it.id !in visitedEntityIds && it.isAlive && !it.isRemoved }
            .distinctBy(EntityAccess::id)
            .map { ChainTarget(it, it.boundingBox.center) }
            .filter { it.position.distanceSquaredTo(chainOrigin) <= maximumDistanceSquared }
    }

    private fun applyChainDamage(player: PlayerAccess, mainTargetId: Int, startPosition: VectorView, shotCount: Long): ZeusChainPayload? {
        val damageSource = DamageSources.vanilla(VanillaDamageType.FellOutOfWorld, player, player)
        val damagePoints = CHAIN_DAMAGE_POINTS_PER_SHOT * shotCount.toDouble()
        val visitedEntityIds = mutableSetOf(player.id, mainTargetId)
        val chainSegments = when (chainMode) {
            ZeusChainMode.NearestTargetPath -> applyNearestTargetPath(player, startPosition, visitedEntityIds, damageSource, damagePoints)
            ZeusChainMode.FullCoverage -> applyFullCoverageChain(player, startPosition, visitedEntityIds, damageSource, damagePoints)
        }

        return chainSegments
            .takeIf { it.isNotEmpty() }
            ?.let(::ZeusChainPayload)
    }

    private fun applyNearestTargetPath(player: PlayerAccess, startPosition: VectorView, visitedEntityIds: MutableSet<Int>, damageSource: DamageSourceView, damagePoints: Double): List<ZeusChainSegment> {
        val chainSegments = mutableListOf<ZeusChainSegment>()
        var chainOrigin = startPosition

        while (true) {
            val target = findAvailableChainTargets(player, chainOrigin, visitedEntityIds)
                .minWithOrNull(chainTargetComparator(chainOrigin))
                ?: break
            visitedEntityIds += target.entity.id

            target.entity.hurt(damageSource, damagePoints)
            chainSegments += ZeusChainSegment(chainOrigin, target.position)
            chainOrigin = target.position
        }

        return chainSegments
    }

    private fun applyFullCoverageChain(player: PlayerAccess, startPosition: VectorView, visitedEntityIds: MutableSet<Int>, damageSource: DamageSourceView, damagePoints: Double): List<ZeusChainSegment> {
        val chainSegments = mutableListOf<ZeusChainSegment>()
        val pendingOrigins = ArrayDeque<VectorView>()
        pendingOrigins.addLast(startPosition)

        while (pendingOrigins.isNotEmpty()) {
            val chainOrigin = pendingOrigins.removeFirst()
            val targetsBeforeDamage = findAvailableChainTargets(player, chainOrigin, visitedEntityIds)
                .sortedWith(chainTargetComparator(chainOrigin))
                .toList()
            for ((entity, position) in targetsBeforeDamage) {
                if (!visitedEntityIds.add(entity.id)) continue

                entity.hurt(damageSource, damagePoints)
                chainSegments += ZeusChainSegment(chainOrigin, position)
                pendingOrigins.addLast(position)
            }
        }

        return chainSegments
    }
}

private data class ChainTarget(
    val entity: LivingEntityAccess,
    val position: VectorView,
)

private fun chainTargetComparator(chainOrigin: VectorView): Comparator<ChainTarget> {
    return compareBy<ChainTarget> { it.position.distanceSquaredTo(chainOrigin) }
        .thenBy { it.entity.id }
}

private const val CHAIN_DAMAGE_POINTS_PER_SHOT = 10_000.0
