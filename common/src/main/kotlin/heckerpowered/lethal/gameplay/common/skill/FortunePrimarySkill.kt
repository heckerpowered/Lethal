/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.block.BlockCategory
import heckerpowered.bridge.adapter.block.BlockClassificationAccess
import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.bridge.adapter.block.BlockStateInterop
import heckerpowered.bridge.adapter.entity.EntityInterop
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackInterop
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.math.expandedBy
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.Constants

internal val fortunePrimarySkill = FortunePrimarySkill()

internal class FortunePrimarySkill(
    private val currentTimeMilliseconds: () -> Long = System::currentTimeMillis,
) : WeaponSkill {
    override fun activate(player: PlayerAccess, weaponStack: ItemStackAccess) {
        val persistentData = ItemStackInterop.persistentData(weaponStack) ?: return
        val currentTime = currentTimeMilliseconds()
        val cooldownDeadline = persistentData.getLong(CooldownDeadlineKey) ?: 0L
        if (cooldownDeadline > currentTime) return

        persistentData.setLong(CooldownDeadlineKey, currentTime + ReactivationDelayMilliseconds)
        val glowingArea = player.boundingBox.expandedBy(GlowingRadiusBlocks)
        for (entity in player.world.getEntities(glowingArea)) {
            if (entity.id == player.id) continue
            EntityInterop.glowing(entity)?.glowFor(GlowingDurationTicks)
        }
    }

    fun isActive(weaponStack: ItemStackAccess): Boolean {
        return status(weaponStack).isActive
    }

    internal fun status(weaponStack: ItemStackAccess): FortunePrimarySkillStatus {
        val cooldownDeadline = ItemStackInterop.persistentData(weaponStack)?.getLong(CooldownDeadlineKey)
            ?: return FortunePrimarySkillStatus.Inactive
        val currentTime = currentTimeMilliseconds()
        val cooldownRemainingMilliseconds = cooldownDeadline - currentTime
        val activeRemainingMilliseconds = PenetrationAfterCooldownMilliseconds -
                (currentTime - cooldownDeadline).coerceAtLeast(0L)
        return FortunePrimarySkillStatus(
            cooldownRemainingMilliseconds = cooldownRemainingMilliseconds,
            activeRemainingMilliseconds = activeRemainingMilliseconds.coerceAtLeast(0L),
            isActive = cooldownRemainingMilliseconds >= -PenetrationAfterCooldownMilliseconds,
        )
    }

    fun isRayBlockedBy(player: PlayerAccess, weaponStack: ItemStackAccess, blockHit: BlockHitResult): Boolean {
        if (!isActive(weaponStack)) return true

        val blockState = blockHit.blockState
        val classification = BlockStateInterop.classification(blockState)
        if (classification?.isIn(BlockCategory.SugarCane) == true || classification?.isReplaceable == true) {
            player.world.destroyBlock(blockHit.blockPosition, dropItems = false)
            return false
        }

        if (blockState.isBlock(DirtIdentifier)) return false
        if (classification == null) return true

        return !classification.canPassThroughWithoutDestruction()
    }

    private fun BlockClassificationAccess.canPassThroughWithoutDestruction(): Boolean {
        return isIn(BlockCategory.Logs) ||
                isIn(BlockCategory.Planks) ||
                isIn(BlockCategory.Snow) ||
                isIn(BlockCategory.Sand)
    }

    private fun BlockStateAccess.isBlock(identifier: Identifier): Boolean {
        val blockIdentifier = block.identifier
        return blockIdentifier.namespace == identifier.namespace && blockIdentifier.path == identifier.path
    }

    private companion object {
        val CooldownDeadlineKey = Constants.identifier("fortune_primary_cooldown_deadline")
        val DirtIdentifier = Identifier.create("minecraft", "dirt")

        /*
         * Legacy stores a 30-second reactivation deadline, then treats that deadline as active
         * for another 60 seconds. Keep the durations separate to preserve its 90-second window.
         */
        const val ReactivationDelayMilliseconds = 30_000L
        const val PenetrationAfterCooldownMilliseconds = 60_000L
        const val GlowingDurationTicks = 20 * 60
        const val GlowingRadiusBlocks = 64.0
    }
}

internal data class FortunePrimarySkillStatus(
    val cooldownRemainingMilliseconds: Long,
    val activeRemainingMilliseconds: Long,
    val isActive: Boolean,
) {
    companion object {
        val Inactive = FortunePrimarySkillStatus(
            cooldownRemainingMilliseconds = 0L,
            activeRemainingMilliseconds = 0L,
            isActive = false,
        )
    }
}
