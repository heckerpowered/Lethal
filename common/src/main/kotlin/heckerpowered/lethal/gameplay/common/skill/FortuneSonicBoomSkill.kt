/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.EntityInterop
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackInterop
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.math.expandedBy
import heckerpowered.bridge.math.minus
import heckerpowered.bridge.math.normalized
import heckerpowered.bridge.math.plus
import heckerpowered.bridge.math.times
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitEffect
import kotlin.math.min

internal val fortuneSonicBoomSkill = FortuneSonicBoomSkill()

internal class FortuneSonicBoomSkill : WeaponSkill, EntityHitEffect {
    override fun apply(result: EntityDamageResult) {
        if (result.livingTarget == null) return

        val persistentData = ItemStackInterop.persistentData(result.weaponStack) ?: return
        val currentCharge = persistentData.getDouble(ChargeKey) ?: 0.0
        persistentData.setDouble(
            ChargeKey,
            (currentCharge + result.actualDamagePoints).coerceIn(MinimumChargePoints, MaximumChargePoints),
        )
    }

    override fun activate(player: PlayerAccess, weaponStack: ItemStackAccess) {
        val persistentData = ItemStackInterop.persistentData(weaponStack) ?: return
        val currentCharge = persistentData.getDouble(ChargeKey) ?: 0.0
        if (currentCharge < ActivationChargePoints) return

        persistentData.setDouble(ChargeKey, currentCharge - ActivationChargePoints)

        val startPosition = player.eyePosition
        val direction = player.viewVector.normalized()
        val endPosition = startPosition + direction * RangeBlocks
        val searchBox = segmentBounds(startPosition, endPosition).expandedBy(LegacyEndpointSearchRadiusBlocks)
        val executionSource = DamageSources.vanilla(
            VanillaDamageType.PlayerAttack,
            directEntity = player,
            causingEntity = player,
        )

        for (entity in player.world.getEntities(searchBox)) {
            if (entity.id == player.id) continue
            val livingEntity = EntityInterop.living(entity) ?: continue
            if (!isInsideLegacyCapsule(startPosition, endPosition, livingEntity.boundingBox)) continue

            val execution = EntityInterop.execution(livingEntity)
                ?: error("Living entity ${livingEntity.id} does not expose execution access")
            execution.execute(executionSource)
        }
    }

    internal fun currentCharge(weaponStack: ItemStackAccess): Double {
        return ItemStackInterop.persistentData(weaponStack)?.getDouble(ChargeKey) ?: 0.0
    }

    internal fun chargeStatus(weaponStack: ItemStackAccess): FortuneSkillChargeStatus {
        return FortuneSkillChargeStatus(currentCharge(weaponStack), ActivationChargePoints)
    }

    private fun segmentBounds(startPosition: VectorView, endPosition: VectorView): BoxView {
        return Geometry.box(
            minOf(startPosition.x, endPosition.x),
            minOf(startPosition.y, endPosition.y),
            minOf(startPosition.z, endPosition.z),
            maxOf(startPosition.x, endPosition.x),
            maxOf(startPosition.y, endPosition.y),
            maxOf(startPosition.z, endPosition.z),
        )
    }

    private fun isInsideLegacyCapsule(startPosition: VectorView, endPosition: VectorView, box: BoxView): Boolean {
        /*
         * Legacy tests only the minimum and maximum AABB corners. It also compares an endpoint's
         * linear distance with radius squared. Preserve both observable quirks while using a
         * spatial broad phase; changing the shape would silently change which targets are hit.
         */
        val minimumCornerDistance = legacySegmentDistanceMeasure(startPosition, endPosition, box.min)
        val maximumCornerDistance = legacySegmentDistanceMeasure(startPosition, endPosition, box.max)
        return min(minimumCornerDistance, maximumCornerDistance) < RadiusSquared
    }

    private fun legacySegmentDistanceMeasure(
        startPosition: VectorView,
        endPosition: VectorView,
        point: VectorView,
    ): Double {
        val segmentDirection = endPosition - startPosition
        val projection = (point - startPosition).dot(segmentDirection) / segmentDirection.lengthSquared
        if (projection < 0.0) return point.distanceTo(startPosition)
        if (projection > 1.0) return point.distanceTo(endPosition)

        val closestPoint = startPosition + segmentDirection * projection
        return point.distanceSquaredTo(closestPoint)
    }

    private companion object {
        val ChargeKey = Constants.identifier("fortune_sonic_boom_charge")

        const val MinimumChargePoints = 0.0
        const val ActivationChargePoints = 200.0
        const val MaximumChargePoints = 1_000.0
        const val RangeBlocks = 64.0
        const val RadiusBlocks = 3.0
        const val RadiusSquared = RadiusBlocks * RadiusBlocks

        // The Legacy endpoint comparison accepts a linear distance below radius squared.
        const val LegacyEndpointSearchRadiusBlocks = RadiusSquared
    }
}
