/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

/**
 * Optional item capability that describes host-neutral tooltip lines for a stack.
 */
interface ItemTooltip {
    /**
     * Returns tooltip-line presentations in display order for [stack].
     */
    fun getTooltipLines(stack: ItemStackAccess): List<TooltipLinePresentation>
}

/**
 * Presentation of one tooltip line.
 */
sealed interface TooltipLinePresentation

/**
 * One tooltip line assembled from [content].
 *
 * [color] applies to the complete line.
 */
class TooltipLine(content: List<TooltipText>, val color: TooltipColor? = null) : TooltipLinePresentation {
    val content = content.toList()

    init {
        require(content.isNotEmpty()) { "Tooltip line content must not be empty" }
    }
}

/**
 * Host-neutral text that a tooltip host can render literally or through its translation system.
 */
sealed class TooltipText {
    data class Literal(val value: String) : TooltipText()

    /**
     * Translatable text with primitive formatting [arguments] understood by the host.
     */
    class Translatable(val key: String, arguments: List<Any> = emptyList()) : TooltipText() {
        val arguments = arguments.toList()

        init {
            require(key.isNotBlank()) { "Tooltip translation key must not be blank" }
            require(arguments.all(Any::isTooltipTranslationArgument)) { "Tooltip translation arguments must be strings, numbers, booleans, or characters" }
        }
    }
}

/**
 * Presents [line] with [color] applied to the completed prefix.
 */
data class TooltipProgress(
    val line: TooltipLine,
    val completedFraction: Double,
    val color: TooltipColor,
) : TooltipLinePresentation {
    init {
        require(completedFraction.isFinite() && completedFraction in 0.0..1.0) { "Tooltip progress must be finite and between zero and one" }
    }
}

/**
 * Named colors shared by the supported hosts' text systems.
 */
enum class TooltipColor {
    Gray,
    Gold,
    Green,
}

private fun Any.isTooltipTranslationArgument(): Boolean {
    return this is String || this is Number || this is Boolean || this is Char
}
