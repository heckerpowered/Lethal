/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.color

/**
 * Four floating-point components in red, green, blue, and alpha order.
 *
 * [Color] stores component values only; it carries no color-encoding metadata and does not state
 * whether RGB has been premultiplied by alpha. An unwrapped [Color] conventionally represents
 * linear RGB. When non-linear values must remain distinguishable in CPU code, wrap the value in an
 * encoding-specific type rather than passing it as a bare [Color].
 */
data class Color(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
) {
    companion object {
        val TransparentBlack = Color(0.0F, 0.0F, 0.0F, 0.0F)
    }
}