/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.item.TooltipColor
import heckerpowered.bridge.adapter.item.TooltipText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FortuneSkillTooltipTest {
    @Test
    fun inactiveAndUnchargedSkillsExposeTheLegacyStatusLines() {
        val lines = createFortuneSkillTooltipLines(
            FortunePrimarySkillStatus.Inactive,
            FortuneSkillChargeStatus(0.0, 200.0),
            FortuneSkillChargeStatus(0.0, 400.0),
        )

        assertEquals(4, lines.size)
        assertEquals(listOf("lethal.perk"), translationKeys(lines[0].content))

        assertEquals(TooltipColor.Green, lines[1].color)
        assertNull(lines[1].progress)
        assertEquals(
            listOf("lethal.perk.fortune", "lethal.cooldown.ready"),
            translationKeys(lines[1].content),
        )

        assertEquals(TooltipColor.Green, lines[2].progress?.color)
        assertEquals(0.0, lines[2].progress?.completedFraction)
        assertEquals(200.0, translationArguments(lines[2].content).single())

        assertEquals(TooltipColor.Green, lines[3].progress?.color)
        assertEquals(0.0, lines[3].progress?.completedFraction)
        assertEquals(400.0, translationArguments(lines[3].content).single())
    }

    @Test
    fun activePrimarySkillUsesTheLegacyGoldDecayProgress() {
        val lines = createFortuneSkillTooltipLines(
            FortunePrimarySkillStatus(
                cooldownRemainingMilliseconds = -30_000L,
                activeRemainingMilliseconds = 30_000L,
                isActive = true,
            ),
            FortuneSkillChargeStatus(200.0, 200.0),
            FortuneSkillChargeStatus(400.0, 400.0),
        )

        val primaryLine = lines[1]
        assertNull(primaryLine.color)
        assertEquals(TooltipColor.Gold, primaryLine.progress?.color)
        assertEquals(0.5, primaryLine.progress?.completedFraction)
        assertEquals(
            listOf("lethal.perk.fortune", "lethal.cooldown.ready"),
            translationKeys(primaryLine.content),
        )

        assertEquals(TooltipColor.Green, lines[2].color)
        assertNull(lines[2].progress)
        assertEquals(TooltipColor.Green, lines[3].color)
        assertNull(lines[3].progress)
    }

    @Test
    fun primaryCooldownIsDisplayedInSecondsWhileTheFullActiveWindowRemainsGold() {
        val lines = createFortuneSkillTooltipLines(
            FortunePrimarySkillStatus(
                cooldownRemainingMilliseconds = 12_500L,
                activeRemainingMilliseconds = 60_000L,
                isActive = true,
            ),
            FortuneSkillChargeStatus(100.0, 200.0),
            FortuneSkillChargeStatus(100.0, 400.0),
        )

        val primaryLine = lines[1]
        assertEquals(1.0, primaryLine.progress?.completedFraction)
        assertEquals(listOf(12.5), translationArguments(primaryLine.content))
        assertEquals(0.5, lines[2].progress?.completedFraction)
        assertEquals(0.25, lines[3].progress?.completedFraction)
    }

    private fun translationKeys(content: List<TooltipText>): List<String> {
        return content.filterIsInstance<TooltipText.Translatable>().map { text -> text.key }
    }

    private fun translationArguments(content: List<TooltipText>): List<Any> {
        return content.filterIsInstance<TooltipText.Translatable>().flatMap { text -> text.arguments }
    }
}
