/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

/**
 * Chooses the contents an attachment use starts with before drawing in a logical render pass.
 *
 * Loading continues an earlier image, clearing supplies known starting values, and discarding
 * avoids preserving values the pass will replace. The choice applies to the aspect selected by
 * the pass position, inside the render area and participating layers.
 *
 * The depth and stencil positions choose independently. A combined attachment can retain its
 * depth image while clearing its stencil marks. [AttachmentStoreOperation] separately controls
 * which results remain available after the pass.
 */
sealed interface AttachmentLoadOperation<out T> {
    /**
     * Uses the existing values as the initial contents.
     *
     * Use this for overlays that blend with earlier color, drawing that preserves uncovered
     * pixels, or depth and stencil tests that must continue from previous results. Loading does
     * not initialize storage: every old value the new work depends on must already be defined.
     */
    data object Load : AttachmentLoadOperation<Nothing>

    /**
     * Gives up the previous values so they need not be preserved for this pass.
     *
     * This is useful when drawing establishes every value that can affect its result, such as
     * replacing a color region without destination-dependent blending. It does not clear to zero.
     * The initial values are undefined and must be replaced before they affect blending, depth
     * tests, stencil tests, or any other read.
     */
    data object Discard : AttachmentLoadOperation<Nothing>

    /**
     * Replaces the selected aspect's values in the pass region with [value] before drawing.
     *
     * Typical values are a background color, the far depth value for the chosen depth convention,
     * or zero for an empty stencil mask. Depth clear values must be finite and in `[0, 1]`;
     * choosing conventional or reverse depth determines which end is useful, not the valid range.
     *
     * Clear covers the render area in every participating layer and every stored sample. It is
     * independent of draw-time tests, scissor, and write masks, and does not clear an unselected
     * aspect. Color values follow the selected attachment format's clear conversion semantics.
     */
    data class Clear<T>(val value: T) : AttachmentLoadOperation<T>
}
