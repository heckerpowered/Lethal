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

fun TooltipLine.asHost(): String = asHost { key, arguments -> I18n.format(key, *arguments.toTypedArray()) }

internal fun TooltipLine.asHost(translate: (key: String, arguments: List<Any>) -> String): String {
    val text = content.joinToString("") { content ->
        when (content) {
            is TooltipText.Literal -> content.value
            is TooltipText.Translatable -> translate(content.key, content.arguments)
        }
    }
    val baseFormatting = color?.asHost()?.toString().orEmpty()
    val progress = progress ?: return baseFormatting + text
    val completedLength = (text.length * progress.completedFraction).roundToInt()
    if (completedLength <= 0) return baseFormatting + text
    if (completedLength >= text.length) return progress.color.asHost().toString() + text

    return buildString {
        append(progress.color.asHost())
        append(text, 0, completedLength)
        append(TextFormatting.RESET)
        append(baseFormatting)
        append(text, completedLength, text.length)
    }
}

private fun TooltipColor.asHost(): TextFormatting {
    return when (this) {
        TooltipColor.Gray -> TextFormatting.GRAY
        TooltipColor.Gold -> TextFormatting.GOLD
        TooltipColor.Green -> TextFormatting.GREEN
    }
}
