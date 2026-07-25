/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.item.ItemTooltip
import heckerpowered.bridge.adapter.item.TooltipColor
import heckerpowered.bridge.adapter.item.TooltipLine
import heckerpowered.bridge.adapter.item.TooltipProgress
import heckerpowered.bridge.adapter.item.TooltipText
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

internal val fortuneSkillTooltip = FortuneSkillTooltip(
    fortunePrimarySkill,
    fortuneSonicBoomSkill,
    fortuneUltimateSkill,
)

internal val enhancedFortuneSkillTooltip = FortuneSkillTooltip(
    fortunePrimarySkill,
    fortuneSonicBoomSkill,
    enhancedFortuneUltimateSkill,
)

internal class FortuneSkillTooltip(
    private val primarySkill: FortunePrimarySkill,
    private val sonicBoomSkill: FortuneSonicBoomSkill,
    private val ultimateSkill: FortuneUltimateSkill,
) : ItemTooltip {
    override fun getTooltipLines(stack: ItemStackAccess): List<TooltipLine> {
        return createFortuneSkillTooltipLines(
            primarySkill.status(stack),
            sonicBoomSkill.chargeStatus(stack),
            ultimateSkill.chargeStatus(stack),
        )
    }
}

internal data class FortuneSkillChargeStatus(
    val accumulatedDamagePoints: Double,
    val requiredDamagePoints: Double,
) {
    init {
        require(accumulatedDamagePoints.isFinite()) { "Accumulated skill damage must be finite" }
        require(requiredDamagePoints.isFinite() && requiredDamagePoints > 0.0) {
            "Required skill damage must be finite and positive"
        }
    }

    val remainingDamagePoints: Double
        get() = (requiredDamagePoints - accumulatedDamagePoints).coerceAtLeast(0.0)

    val progress: Double
        get() = (accumulatedDamagePoints / requiredDamagePoints).coerceIn(0.0, 1.0)

    val isReady: Boolean
        get() = remainingDamagePoints <= 0.0
}

internal fun createFortuneSkillTooltipLines(
    primaryStatus: FortunePrimarySkillStatus,
    sonicBoomStatus: FortuneSkillChargeStatus,
    ultimateStatus: FortuneSkillChargeStatus,
): List<TooltipLine> {
    return listOf(
        TooltipLine(listOf(translatable("lethal.perk"))),
        primaryTooltipLine(primaryStatus),
        damageChargeTooltipLine("lethal.perk.fortune.sonic_boom", sonicBoomStatus),
        damageChargeTooltipLine("lethal.perk.fortune.ultimate", ultimateStatus),
    )
}

private fun primaryTooltipLine(status: FortunePrimarySkillStatus): TooltipLine {
    val stateText = if (status.cooldownRemainingMilliseconds > 0L) {
        translatable("lethal.cooldown.seconds", status.cooldownRemainingMilliseconds / MillisecondsPerSecond)
    } else {
        translatable("lethal.cooldown.ready")
    }
    val content = skillStatusContent("lethal.perk.fortune", stateText)
    if (status.isActive) {
        val activeProgress = (status.activeRemainingMilliseconds / PrimaryActiveDurationMilliseconds)
            .coerceIn(0.0, 1.0)
        return TooltipLine(content, progress = TooltipProgress(activeProgress, TooltipColor.Gold))
    }

    val color = if (status.cooldownRemainingMilliseconds <= 0L) TooltipColor.Green else TooltipColor.Gray
    return TooltipLine(content, color = color)
}

private fun damageChargeTooltipLine(skillTranslationKey: String, status: FortuneSkillChargeStatus): TooltipLine {
    if (status.isReady) {
        return TooltipLine(
            skillStatusContent(skillTranslationKey, translatable("lethal.cooldown.ready")),
            color = TooltipColor.Green,
        )
    }

    return TooltipLine(
        skillStatusContent(
            skillTranslationKey,
            translatable("lethal.precondition.damage", status.remainingDamagePoints),
        ),
        progress = TooltipProgress(status.progress, TooltipColor.Green),
    )
}

private fun skillStatusContent(skillTranslationKey: String, status: TooltipText): List<TooltipText> {
    return listOf(translatable(skillTranslationKey), TooltipText.Literal(" - "), status)
}

private fun translatable(key: String, vararg arguments: Any): TooltipText.Translatable {
    return TooltipText.Translatable(key, arguments.toList())
}

private const val MillisecondsPerSecond = 1_000.0
private const val PrimaryActiveDurationMilliseconds = 60_000.0
