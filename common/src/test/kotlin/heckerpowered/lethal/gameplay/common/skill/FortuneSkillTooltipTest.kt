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

        assertEquals(expected = 4, actual = lines.size)
        assertEquals(expected = listOf("lethal.perk"), actual = translationKeys(lines[0].content))

        assertEquals(expected = TooltipColor.Green, actual = lines[1].color)
        assertNull(lines[1].progress)
        assertEquals(
            expected = listOf("lethal.perk.fortune", "lethal.cooldown.ready"),
            actual = translationKeys(lines[1].content),
        )

        assertEquals(expected = TooltipColor.Green, actual = lines[2].progress?.color)
        assertEquals(expected = 0.0, actual = lines[2].progress?.completedFraction)
        assertEquals(expected = 200.0, actual = translationArguments(lines[2].content).single())

        assertEquals(expected = TooltipColor.Green, actual = lines[3].progress?.color)
        assertEquals(expected = 0.0, actual = lines[3].progress?.completedFraction)
        assertEquals(expected = 400.0, actual = translationArguments(lines[3].content).single())
    }

    @Test
    fun activePrimarySkillUsesTheLegacyGoldDecayProgress() {
        val lines = createFortuneSkillTooltipLines(
            FortunePrimarySkillStatus(-30_000L, 30_000L, true),
            FortuneSkillChargeStatus(200.0, 200.0),
            FortuneSkillChargeStatus(400.0, 400.0),
        )

        val primaryLine = lines[1]
        assertNull(primaryLine.color)
        assertEquals(expected = TooltipColor.Gold, actual = primaryLine.progress?.color)
        assertEquals(expected = 0.5, actual = primaryLine.progress?.completedFraction)
        assertEquals(
            expected = listOf("lethal.perk.fortune", "lethal.cooldown.ready"),
            actual = translationKeys(primaryLine.content),
        )

        assertEquals(expected = TooltipColor.Green, actual = lines[2].color)
        assertNull(lines[2].progress)
        assertEquals(expected = TooltipColor.Green, actual = lines[3].color)
        assertNull(lines[3].progress)
    }

    @Test
    fun primaryCooldownIsDisplayedInSecondsWhileTheFullActiveWindowRemainsGold() {
        val lines = createFortuneSkillTooltipLines(
            FortunePrimarySkillStatus(12_500L, 60_000L, true),
            FortuneSkillChargeStatus(100.0, 200.0),
            FortuneSkillChargeStatus(100.0, 400.0),
        )

        val primaryLine = lines[1]
        assertEquals(expected = 1.0, actual = primaryLine.progress?.completedFraction)
        assertEquals(expected = listOf(12.5), actual = translationArguments(primaryLine.content))
        assertEquals(expected = 0.5, actual = lines[2].progress?.completedFraction)
        assertEquals(expected = 0.25, actual = lines[3].progress?.completedFraction)
    }

    private fun translationKeys(content: List<TooltipText>): List<String> {
        return content.filterIsInstance<TooltipText.Translatable>().map(TooltipText.Translatable::key)
    }

    private fun translationArguments(content: List<TooltipText>): List<Any> {
        return content.filterIsInstance<TooltipText.Translatable>().flatMap(TooltipText.Translatable::arguments)
    }
}
