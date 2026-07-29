/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.math.Geometry
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.EntityDamageResult
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitEffect
import heckerpowered.lethal.gameplay.common.sound.ModSounds

internal class FortuneUltimateSkill(private val maximumChargePoints: Double, private val kind: StarJudgementKind, private val spawner: StarJudgementSpawner = HostingStarJudgementSpawner) : WeaponSkill, EntityHitEffect {
    override fun apply(result: EntityDamageResult) {
        if (result.livingTarget == null) return

        val persistentData = result.weaponStack as? PersistentDataAccess ?: return
        val currentCharge = persistentData.getDouble(ChargeKey) ?: 0.0
        persistentData.setDouble(ChargeKey, (currentCharge + result.actualDamagePoints).coerceIn(MINIMUM_CHARGE_POINTS, maximumChargePoints))
    }

    override fun activate(player: PlayerAccess, weaponStack: ItemStackAccess) {
        val persistentData = weaponStack as? PersistentDataAccess ?: return
        val currentCharge = persistentData.getDouble(ChargeKey) ?: 0.0
        if (currentCharge < ACTIVATION_CHARGE_POINTS) return

        val world = player.world
        val ray = Geometry.ray(player.eyePosition, player.viewVector)
        val blockHit = world
            .raycastBlockHits(ray, TARGETING_RANGE_BLOCKS, BlockRaycastShape.Outline)
            .firstOrNull() ?: return

        persistentData.setDouble(ChargeKey, currentCharge - ACTIVATION_CHARGE_POINTS)
        spawner.spawn(world, player, blockHit.point, kind)
        world.playSound(blockHit.point, ModSounds.FortunePerkUltimate)
    }

    internal fun currentCharge(weaponStack: ItemStackAccess): Double {
        return (weaponStack as? PersistentDataAccess)?.getDouble(ChargeKey) ?: 0.0
    }

    internal fun chargeStatus(weaponStack: ItemStackAccess): FortuneSkillChargeStatus {
        return FortuneSkillChargeStatus(currentCharge(weaponStack), ACTIVATION_CHARGE_POINTS)
    }

    private companion object {
        val ChargeKey = Constants.identifier("fortune_ultimate_charge")

        const val MINIMUM_CHARGE_POINTS = 0.0
        const val ACTIVATION_CHARGE_POINTS = 400.0
        const val TARGETING_RANGE_BLOCKS = 512.0
    }
}
