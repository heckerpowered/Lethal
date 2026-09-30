/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.Matrices
import heckerpowered.math.MatrixView
import kotlin.math.PI
import kotlin.math.tan

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
interface Projection {
    val nearPlane: Double
    val farPlane: Double

    /**
     * Transforms view-space homogeneous coordinates into clip space.
     */
    val matrix: MatrixView
}

/**
 * Symmetric perspective projection with +Z as the forward direction.
 *
 * [verticalFieldOfViewRadians] is the complete vertical field of view rather
 * than the half-angle.
 */
class PerspectiveProjection(
    val verticalFieldOfViewRadians: Double,
    val aspectRatio: Double,
    override val nearPlane: Double,
    override val farPlane: Double,
) : Projection {
    init {
        require(verticalFieldOfViewRadians.isFinite()) { "Vertical field of view must be finite" }
        require(verticalFieldOfViewRadians > 0.0 && verticalFieldOfViewRadians < PI) { "Vertical field of view must be between zero and PI radians" }
        require(aspectRatio.isFinite() && aspectRatio > 0.0) { "Aspect ratio must be finite and positive" }
        require(nearPlane.isFinite() && nearPlane > 0.0) { "Perspective near plane must be finite and positive" }
        require(farPlane.isFinite() && farPlane > nearPlane) { "Perspective far plane must be finite and greater than the near plane" }
    }

    override val matrix: MatrixView

    init {
        val verticalScale = 1.0 / tan(verticalFieldOfViewRadians * 0.5)
        val horizontalScale = verticalScale / aspectRatio

        val inverseDepthRange = 1.0 / (farPlane - nearPlane)
        val depthScale = farPlane * inverseDepthRange
        val depthTranslation = -farPlane * nearPlane * inverseDepthRange

        matrix = Matrices.of(
            horizontalScale, 0.0, 0.0, 0.0,
            0.0, -verticalScale, 0.0, 0.0,
            0.0, 0.0, depthScale, depthTranslation,
            0.0, 0.0, 1.0, 0.0,
        )
    }

    companion object {
        fun degrees(verticalFieldOfViewDegrees: Double, aspectRatio: Double, nearPlane: Double, farPlane: Double): PerspectiveProjection =
            PerspectiveProjection(
                verticalFieldOfViewRadians = Math.toRadians(verticalFieldOfViewDegrees),
                aspectRatio = aspectRatio,
                nearPlane = nearPlane,
                farPlane = farPlane,
            )
    }
}

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