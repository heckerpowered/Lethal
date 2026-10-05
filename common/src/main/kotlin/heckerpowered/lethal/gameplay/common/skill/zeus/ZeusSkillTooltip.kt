/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill.zeus

import heckerpowered.bridge.adapter.item.*
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.gameplay.common.item.Zeus
import heckerpowered.lethal.gameplay.common.skill.SkillSlot
import heckerpowered.lethal.gameplay.common.skill.damageChargeTooltipLine

internal class ZeusSkillTooltip(private val weapon: Zeus) : ItemTooltip {
    override fun getTooltipLines(stack: ItemStackAccess): List<TooltipLinePresentation> = buildList {
        val currentEnergyPoints = weapon.energy.currentEnergyPoints(stack)
        when {
            weapon.getSkill(SkillSlot.Secondary) === ZeusMissileSkill -> add(damageChargeTooltipLine("lethal.perk.zeus.missile", currentEnergyPoints, ZeusMissileSkill.ENERGY_COST_POINTS))
            weapon.getSkill(SkillSlot.Ultimate) === ZeusJudgementSkill -> add(damageChargeTooltipLine("lethal.perk.zeus.judgement", currentEnergyPoints, ZeusJudgementSkill.ENERGY_COST_POINTS))
            weapon.getSkill(SkillSlot.Auxiliary) === ZeusBerserk -> add(berserkTooltipLine(ZeusBerserk.status(stack)))
            isNotEmpty() -> add(0, TooltipLine(listOf(TooltipText.Translatable("lethal.perk"))))
        }
    }
}

private fun berserkTooltipLine(status: ZeusBerserkStatus): TooltipLinePresentation {
    val state = if (status.cooldownRemainingMilliseconds > 0L) {
        TooltipText.Translatable("lethal.cooldown.seconds", listOf(status.cooldownRemainingMilliseconds / 1_000.0))
    } else {
        TooltipText.Translatable("lethal.cooldown.ready")
    }
    val content = listOf(TooltipText.Translatable("lethal.perk.zeus.berserk"), TooltipText.Literal(" - "), state)
    if (status.isActive) {
        val progress = (status.activeRemainingMilliseconds / ZeusBerserk.ACTIVE_DURATION_MILLISECONDS.toDouble()).coerceIn(0.0, 1.0)
        return TooltipProgress(TooltipLine(content), progress, TooltipColor.Gold)
    }

    return TooltipLine(content, if (status.cooldownRemainingMilliseconds <= 0L) TooltipColor.Green else TooltipColor.Gray)
}
