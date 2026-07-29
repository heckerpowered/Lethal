/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.item.TooltipColor
import heckerpowered.bridge.adapter.item.TooltipLine
import heckerpowered.bridge.adapter.item.TooltipProgress
import heckerpowered.bridge.adapter.item.TooltipText
import net.minecraft.util.text.TextFormatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.roundToInt

class TooltipConversionTest {
    @Test
    fun translatedSegmentsAreJoinedBeforeProgressColoring() {
        val line = TooltipLine(
            listOf(
                TooltipText.Translatable("skill"),
                TooltipText.Literal(" - "),
                TooltipText.Translatable("remaining", listOf(100.0)),
            ),
            null,
            TooltipProgress(0.5, TooltipColor.Green),
        )

        val text = line.asHost { key, arguments ->
            when (key) {
                "skill" -> "Sonic Boom"
                "remaining" -> "Need ${arguments.single()}"
                else -> error("Unexpected translation key: $key")
            }
        }

        val plainText = "Sonic Boom - Need 100.0"
        val completedLength = (plainText.length * 0.5).roundToInt()
        assertEquals(
            expected = TextFormatting.GREEN.toString() + plainText.substring(0, completedLength) +
                    TextFormatting.RESET + plainText.substring(completedLength),
            actual = text,
        )
    }

    @Test
    fun completeLineColorDoesNotInsertProgressFormatting() {
        val line = TooltipLine(listOf(TooltipText.Literal("Ready")), TooltipColor.Green)

        assertEquals(
            expected = TextFormatting.GREEN.toString() + "Ready",
            actual = line.asHost { _, _ -> error("No translation expected") },
        )
    }
}
