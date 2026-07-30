/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.item.*
import net.minecraft.client.resources.I18n
import net.minecraft.util.text.TextFormatting
import kotlin.math.roundToInt

fun TooltipLinePresentation.asHost(): String = asHost { key, arguments -> I18n.format(key, *arguments.toTypedArray()) }

internal fun TooltipLinePresentation.asHost(translate: (key: String, arguments: List<Any>) -> String): String {
    return when (this) {
        is TooltipLine -> render(translate)
        is TooltipProgress -> render(translate)
    }
}

private fun TooltipLine.render(translate: (key: String, arguments: List<Any>) -> String): String {
    return color?.asHost()?.toString().orEmpty() + renderText(translate)
}

private fun TooltipProgress.render(translate: (key: String, arguments: List<Any>) -> String): String {
    val text = line.renderText(translate)
    val baseFormatting = line.color?.asHost()?.toString().orEmpty()
    val completedLength = (text.length * completedFraction).roundToInt()
    if (completedLength <= 0) return baseFormatting + text
    if (completedLength >= text.length) return color.asHost().toString() + text

    return buildString {
        append(color.asHost())
        append(text, 0, completedLength)
        append(TextFormatting.RESET)
        append(baseFormatting)
        append(text, completedLength, text.length)
    }
}

private fun TooltipLine.renderText(translate: (key: String, arguments: List<Any>) -> String): String {
    return content.joinToString("") { content ->
        when (content) {
            is TooltipText.Literal -> content.value
            is TooltipText.Translatable -> translate(content.key, content.arguments)
        }
    }
}

private fun TooltipColor.asHost(): TextFormatting {
    return when (this) {
        TooltipColor.Gray -> TextFormatting.GRAY
        TooltipColor.Gold -> TextFormatting.GOLD
        TooltipColor.Green -> TextFormatting.GREEN
    }
}
