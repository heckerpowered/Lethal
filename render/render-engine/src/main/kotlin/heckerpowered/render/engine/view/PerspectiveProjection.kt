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
