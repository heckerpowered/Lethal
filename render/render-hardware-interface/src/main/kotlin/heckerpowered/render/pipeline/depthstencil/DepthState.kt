/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.depthstencil

/**
 * Controls which surfaces remain visible when several fragments cover the same pixel.
 *
 * A depth attachment records the projected depth of the surface currently occupying each pixel,
 * or each sample when multisampling is used. New fragments are compared with that stored value so
 * surfaces hidden behind previously drawn geometry can be discarded.
 *
 * Depth values describe visibility ordering rather than a linear distance in world units. Which
 * numeric direction represents “nearer” is determined by [compareFunction].
 *
 * Typical uses include:
 *
 * - Opaque 3D geometry normally tests depth and writes the depth of each visible surface.
 * - Transparent geometry normally tests against opaque depth without writing new depth. This
 *   prevents hidden transparent surfaces from appearing through opaque geometry, but transparent
 *   surfaces may still need to be sorted relative to one another.
 * - A depth-only pass writes depth while color writes are disabled. Later passes can reuse that
 *   depth to avoid drawing hidden fragments or to render the same visible surfaces again.
 * - Reverse-depth rendering uses a greater-style comparison and initializes the attachment to its
 *   smallest depth value, improving useful precision for distant geometry.
 *
 * The attachment must be initialized consistently with the comparison. A less-style comparison
 * normally starts from the largest depth value, while a greater-style comparison normally starts
 * from the smallest. Using the wrong clear value can cause every fragment to fail.
 *
 * Exact equality comparisons are reliable only when both passes produce the same depth values.
 * Slightly different vertex transformations or depth calculations can cause visually identical
 * surfaces to fail an [CompareFunction.Equal] test.
 *
 * A failed depth test prevents color and depth output, but does not guarantee that the fragment
 * shader was never executed. It may also select a stencil depth-failure operation when stencil
 * testing is enabled.
 */
data class DepthState(
    /**
     * Determines whether the incoming fragment depth passes against the stored depth.
     *
     * [CompareFunction.Less] is the usual choice when nearer values are smaller.
     * [CompareFunction.Greater] provides the corresponding comparison for reverse-depth rendering.
     */
    val compareFunction: CompareFunction = CompareFunction.Less,

    /**
     * Determines whether a passing fragment replaces the stored depth value.
     *
     * Disabling writes does not disable depth testing. Read-only depth testing is commonly used for
     * transparent or overlay geometry that should be hidden by existing opaque surfaces without
     * preventing later geometry from being drawn.
     *
     * Disabling depth writes does not solve ordering between transparent surfaces; those surfaces
     * may still require sorting or another transparency technique.
     */
    val writeEnabled: Boolean = true,
) {
    companion object {
        /**
         * Conventional depth state for opaque geometry where smaller depth values represent nearer
         * surfaces.
         *
         * Incoming depth must be less than the stored value, and passing fragments replace the stored
         * depth. The attachment is normally cleared to its largest depth value, typically `1.0`.
         *
         * This state is not appropriate for reverse-depth projection, which normally uses a
         * greater-style comparison and clears depth to `0.0`.
         */
        val Default = DepthState()
    }
}