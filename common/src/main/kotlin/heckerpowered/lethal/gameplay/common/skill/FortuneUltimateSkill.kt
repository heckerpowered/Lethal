/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackInterop
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.math.Geometry
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitEffect
import heckerpowered.lethal.gameplay.common.sound.ModSounds

internal val fortuneUltimateSkill = FortuneUltimateSkill(
    maximumChargePoints = 1_200.0,
    kind = StarJudgementKind.Standard,
)

internal val enhancedFortuneUltimateSkill = FortuneUltimateSkill(
    maximumChargePoints = 2_000.0,
    kind = StarJudgementKind.Enhanced,
)

internal class FortuneUltimateSkill(
    private val maximumChargePoints: Double,
    private val kind: StarJudgementKind,
    private val spawner: StarJudgementSpawner = HostingStarJudgementSpawner,
) : WeaponSkill, EntityHitEffect {
    override fun apply(result: EntityDamageResult) {
        if (result.livingTarget == null) return

        val persistentData = ItemStackInterop.persistentData(result.weaponStack) ?: return
        val currentCharge = persistentData.getDouble(ChargeKey) ?: 0.0
        persistentData.setDouble(
            ChargeKey,
            (currentCharge + result.actualDamagePoints).coerceIn(MinimumChargePoints, maximumChargePoints),
        )
    }

    override fun activate(player: PlayerAccess, weaponStack: ItemStackAccess) {
        val persistentData = ItemStackInterop.persistentData(weaponStack) ?: return
        val currentCharge = persistentData.getDouble(ChargeKey) ?: 0.0
        if (currentCharge < ActivationChargePoints) return

        val world = player.world
        val ray = Geometry.ray(player.eyePosition, player.viewVector)
        val blockHit = world
            .raycastBlockHits(ray, TargetingRangeBlocks, BlockRaycastShape.Outline)
            .firstOrNull()
            ?: return

        persistentData.setDouble(ChargeKey, currentCharge - ActivationChargePoints)
        spawner.spawn(world, player, blockHit.point, kind)
        world.playSound(blockHit.point, ModSounds.FortunePerkUltimate)
    }

    internal fun currentCharge(weaponStack: ItemStackAccess): Double {
        return ItemStackInterop.persistentData(weaponStack)?.getDouble(ChargeKey) ?: 0.0
    }

    internal fun chargeStatus(weaponStack: ItemStackAccess): FortuneSkillChargeStatus {
        return FortuneSkillChargeStatus(currentCharge(weaponStack), ActivationChargePoints)
    }

    private companion object {
        val ChargeKey = Constants.identifier("fortune_ultimate_charge")

        const val MinimumChargePoints = 0.0
        const val ActivationChargePoints = 400.0
        const val TargetingRangeBlocks = 512.0
    }
}
