/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.depthstencil

/**
 * Configures stencil testing and updates for an 8-bit stencil attachment.
 *
 * Front-facing polygons use [frontFace], while back-facing polygons use [backFace]. Primitives
 * without a face orientation, such as points and lines, use [frontFace]. Face classification
 * follows the configured front-face winding, and culling may prevent one of these states from
 * being used.
 *
 * [readMask] and [writeMask] are shared by both face orientations. The current stencil reference
 * value is also shared, but is supplied separately as dynamic draw state so the same pipeline can
 * test or write different labels without being recreated.
 *
 * This state does not create, clear, or otherwise initialize stencil storage. Before a comparison
 * depends on existing stencil values, the render pass must either clear the stencil aspect to a
 * known value or preserve previously defined contents.
 */
data class StencilState(
    /**
     * Controls stencil testing and updates for front-facing polygons, points, and lines.
     */
    val frontFace: StencilFaceState,

    /**
     * Controls stencil testing and updates for back-facing polygons.
     *
     * Most stencil effects treat both orientations identically, so this defaults to [frontFace].
     * It has no effect when back-facing polygons are culled.
     */
    val backFace: StencilFaceState = frontFace,

    /**
     * Selects the bits that participate in stencil comparisons.
     *
     * The mask is applied independently to the current reference value and the stored stencil
     * value before [StencilFaceState.compareFunction] is evaluated. It does not affect the
     * operation selected afterward.
     *
     * A value of zero does not disable stencil testing. Instead, both masked values become zero,
     * so the outcome depends entirely on the comparison function.
     */
    val readMask: UByte = UByte.MAX_VALUE,

    /**
     * Selects the bits that stencil operations may modify.
     *
     * The operation result is calculated from the complete 8-bit stencil value before this mask
     * is applied. Consequently, this mask preserves unselected bits but does not turn arithmetic
     * operations into independently clamped arithmetic over an arbitrary bit field.
     *
     * A value of zero makes stencil read-only for drawing performed with this state.
     */
    val writeMask: UByte = UByte.MAX_VALUE,
)