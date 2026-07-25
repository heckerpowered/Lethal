/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ItemTooltipTest {
    @Test
    fun lineContentAndTranslationArgumentsAreCopied() {
        val arguments = mutableListOf<Any>(12.5)
        val translated = TooltipText.Translatable("test.remaining", arguments)
        val content = mutableListOf<TooltipText>(translated)
        val line = TooltipLine(content)

        arguments += 20.0
        content += TooltipText.Literal("changed")

        assertEquals(listOf(12.5), translated.arguments)
        assertEquals(listOf(translated), line.content)
    }

    @Test
    fun invalidLineTextAndProgressAreRejected() {
        assertFailsWith<IllegalArgumentException> { TooltipLine(emptyList()) }
        assertFailsWith<IllegalArgumentException> { TooltipText.Translatable(" ") }
        assertFailsWith<IllegalArgumentException> { TooltipText.Translatable("test", listOf(Any())) }
        assertFailsWith<IllegalArgumentException> { TooltipProgress(-0.1, TooltipColor.Green) }
        assertFailsWith<IllegalArgumentException> { TooltipProgress(1.1, TooltipColor.Green) }
        assertFailsWith<IllegalArgumentException> { TooltipProgress(Double.NaN, TooltipColor.Green) }
    }
}
