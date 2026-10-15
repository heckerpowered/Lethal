/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.MatrixView

/**
 * Maps positions from view space into the renderer's clip space.
 *
 * View space is right-handed with:
 *
 * - +X pointing right
 * - +Y pointing up
 * - +Z pointing forward
 *
 * The resulting clip space follows the RHI convention:
 *
 * ```
 * -w <= x <= w
 * -w <= y <= w
 *  0 <= z <= w
 * ```
 *
 * NDC Y increases downward, so projections invert the view-space Y axis.
 */
interface ProjectionView {
    val nearPlane: Double
    val farPlane: Double

    /** Transforms view-space homogeneous coordinates into clip space. */
    val matrix: MatrixView

    /**
     * Returns stable plane distances and matrix values, reusing immutable implementations.
     * Sampling is sequential and does not synchronize concurrent source changes.
     * Delegating wrappers that override values must also override this method to sample the wrapper.
     */
    fun snapshot(): ProjectionView = Projection(this)
}
