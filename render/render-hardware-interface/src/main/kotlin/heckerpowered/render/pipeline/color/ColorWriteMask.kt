/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.color

/**
 * Selects which color-target components may be modified by fragment output writes.
 *
 * The mask is applied after blending. A disabled component preserves the value already stored in
 * the color target, while enabled components receive the corresponding blend result or unblended
 * fragment output.
 *
 * Disabling a component does not remove it from blend-factor calculation. For example, source alpha
 * may still control RGB blending when alpha writes are disabled, and destination alpha may still be
 * read by [BlendFactor.DestinationAlpha].
 *
 * This state applies only to fragment output writes performed through the render pipeline. It does
 * not affect attachment clears, load or store operations, resolves, texture copies, or other
 * transfer operations.
 *
 * Enabling a component that is absent from [ColorTargetState.format] has no effect. This allows
 * [All] to remain the normal default for formats containing fewer than four components.
 */
data class ColorWriteMask(
    val red: Boolean,
    val green: Boolean,
    val blue: Boolean,
    val alpha: Boolean,
) {
    companion object {
        /**
         * Disables all fragment color writes.
         *
         * This is useful when a draw should update only depth or stencil while a color target remains
         * active, such as a depth prepass performed within an existing rendering pass or a draw that
         * builds a stencil mask.
         *
         * This mask does not make the draw a no-op: rasterization, fragment processing, and
         * depth/stencil operations are not disabled. It only prevents the resulting color components
         * from being stored. If rendering does not use a color target at all, prefer a pipeline
         * configuration without one.
         */
        val None = ColorWriteMask(red = false, green = false, blue = false, alpha = false)

        /**
         * Writes RGB while preserving the destination alpha.
         *
         * This is useful for color-only effects such as additive lighting, bloom, or glow, and when
         * the target alpha stores coverage, a mask, or other data owned by another pass. Disabling the
         * alpha write prevents the effect from accidentally accumulating or replacing that data.
         *
         * Source and destination alpha may still participate in RGB blending. This mask only prevents
         * the final alpha result from being stored.
         */
        val Rgb = ColorWriteMask(red = true, green = true, blue = true, alpha = false)

        val All = ColorWriteMask(red = true, green = true, blue = true, alpha = true)
    }
}