/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.item.TooltipColor
import heckerpowered.bridge.adapter.item.TooltipLine
import heckerpowered.bridge.adapter.item.TooltipText
import net.minecraft.client.resources.I18n
import net.minecraft.util.text.TextFormatting
import kotlin.math.roundToInt

object TooltipInterop {
    @JvmStatic
    fun text(line: TooltipLine): String {
        return text(line) { key, arguments -> I18n.format(key, *arguments.toTypedArray()) }
    }

    internal fun text(
        line: TooltipLine,
        translate: (key: String, arguments: List<Any>) -> String,
    ): String {
        val text = line.content.joinToString(separator = "") { content ->
            when (content) {
                is TooltipText.Literal -> content.value
                is TooltipText.Translatable -> translate(content.key, content.arguments)
            }
        }
        val baseFormatting = line.color?.formatting()?.toString().orEmpty()
        val progress = line.progress ?: return baseFormatting + text
        val completedLength = (text.length * progress.completedFraction).roundToInt()
        if (completedLength <= 0) return baseFormatting + text
        if (completedLength >= text.length) return progress.color.formatting().toString() + text

        return buildString {
            append(progress.color.formatting())
            append(text, 0, completedLength)
            append(TextFormatting.RESET)
            append(baseFormatting)
            append(text, completedLength, text.length)
        }
    }

    private fun TooltipColor.formatting(): TextFormatting {
        return when (this) {
            TooltipColor.Gray -> TextFormatting.GRAY
            TooltipColor.Gold -> TextFormatting.GOLD
            TooltipColor.Green -> TextFormatting.GREEN
        }
    }
}

fun TooltipLine.tooltipText(): String {
    return TooltipInterop.text(this)
}
