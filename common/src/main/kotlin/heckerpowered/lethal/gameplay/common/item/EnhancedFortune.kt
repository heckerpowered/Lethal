/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.ItemGlint
import heckerpowered.bridge.adapter.item.ItemTooltip
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitDamage
import heckerpowered.lethal.gameplay.common.item.firearm.HeadshotExecutionEffect
import heckerpowered.lethal.gameplay.common.item.firearm.RayTraceGun
import heckerpowered.lethal.gameplay.common.sound.ModSounds
import heckerpowered.lethal.gameplay.common.skill.FortunePrimary
import heckerpowered.lethal.gameplay.common.skill.FortuneSkillTooltip
import heckerpowered.lethal.gameplay.common.skill.FortuneSonicBoom
import heckerpowered.lethal.gameplay.common.skill.FortuneUltimateSkill
import heckerpowered.lethal.gameplay.common.skill.SkillSlot
import heckerpowered.lethal.gameplay.common.skill.SkillWeapon
import heckerpowered.lethal.gameplay.common.skill.StarJudgementKind
import heckerpowered.lethal.gameplay.common.skill.WeaponSkill

object EnhancedFortune : RayTraceGun(), SkillWeapon, ItemTooltip, ItemGlint {
    private val Ultimate = FortuneUltimateSkill(2_000.0, StarJudgementKind.Enhanced)
    private val Tooltip = FortuneSkillTooltip(FortunePrimary, FortuneSonicBoom, Ultimate)
    private val HitDamage = EntityHitDamage(VanillaDamageType.FellOutOfWorld, 42_000.0, HeadshotExecutionEffect.Legendary, FortuneSonicBoom, Ultimate)

    override val identifier: Identifier
        get() = Constants.identifier("enhanced_fortune")

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
        return Frequency.perMinute(840)
    }

    override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
        return 100.0
    }

    override fun getSkill(slot: SkillSlot): WeaponSkill? {
        return when (slot) {
            SkillSlot.Primary -> FortunePrimary
            SkillSlot.Secondary -> FortuneSonicBoom
            SkillSlot.Ultimate -> Ultimate
        }
    }

    override fun getTooltipLines(stack: ItemStackAccess) = Tooltip.getTooltipLines(stack)

    override fun isRayBlockedBy(player: PlayerAccess, weaponStack: ItemStackAccess, blockHit: BlockHitResult): Boolean {
        return FortunePrimary.isRayBlockedBy(player, weaponStack, blockHit)
    }

    override fun hasGlint(stack: ItemStackAccess): Boolean {
        return FortunePrimary.isActive(stack)
    }

    override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
        player.world.playSound(player.eyePosition, ModSounds.FortuneFire)
        HitDamage.apply(player, weaponStack, shotCount, entityHits)
    }
}
