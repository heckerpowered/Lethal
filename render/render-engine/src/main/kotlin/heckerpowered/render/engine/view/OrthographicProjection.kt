/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.Matrices
import heckerpowered.math.MatrixView

/**
 * Symmetric orthographic projection centered on the view-space origin.
 *
 * [width] and [height] describe the visible extent in view-space units.
 */
class OrthographicProjection(
    val width: Double,
    val height: Double,
    override val nearPlane: Double,
    override val farPlane: Double,
) : Projection {
    init {
        require(width.isFinite() && width > 0.0) { "Orthographic width must be finite and positive" }
        require(height.isFinite() && height > 0.0) { "Orthographic height must be finite and positive" }
        require(nearPlane.isFinite()) { "Orthographic near plane must be finite" }
        require(farPlane.isFinite() && farPlane > nearPlane) { "Orthographic far plane must be finite and greater than the near plane" }
    }

    override val matrix: MatrixView

    init {
        val inverseDepthRange = 1.0 / (farPlane - nearPlane)

        matrix = Matrices.of(
            2.0 / width, 0.0, 0.0, 0.0,
            0.0, -2.0 / height, 0.0, 0.0,
            0.0, 0.0, inverseDepthRange, -nearPlane * inverseDepthRange,
            0.0, 0.0, 0.0, 1.0,
        )
    }
}
