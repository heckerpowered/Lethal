/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.*
import kotlin.math.abs

/**
 * A camera position and orthonormal basis in world space.
 *
 * View coordinates measure displacement from [origin] along [right], [up] and
 * [forward]: +X is right, +Y is up and +Z is forward. The basis is right-handed,
 * with `right × up = forward` within the construction tolerance.
 *
 * Construction samples the inputs before validation and retains independent values.
 * Sampling does not synchronize concurrent changes. The inverse uses the transpose
 * of the basis, so deviations accepted by the tolerance also affect the inverse.
 */
class ViewFrame private constructor(
    val origin: VectorView,
    val right: VectorView,
    val up: VectorView,
    val forward: VectorView,
) {
    val worldToView: AffineTransformView = Geometry.affineTransform(
        axisX = Geometry.vector(right.x, up.x, forward.x),
        axisY = Geometry.vector(right.y, up.y, forward.y),
        axisZ = Geometry.vector(right.z, up.z, forward.z),
        translation = Geometry.vector(
            -right.dot(origin),
            -up.dot(origin),
            -forward.dot(origin),
        ),
    )

    companion object {
        /**
         * Requires finite inputs, unit lengths and pairwise perpendicular axes.
         * [epsilon] bounds squared-length error, pairwise dot products and each
         * component of `right × up - forward`; it must be finite and nonnegative.
         * Opposite handedness is rejected regardless of the tolerance.
         */
        fun of(origin: VectorView, right: VectorView, up: VectorView, forward: VectorView, epsilon: Double = 1.0E-6): ViewFrame {
            require(epsilon.isFinite() && epsilon >= 0.0) { "View frame tolerance must be finite and nonnegative" }

            val position = Vectors.of(origin.x, origin.y, origin.z)
            val rightAxis = Vectors.of(right.x, right.y, right.z)
            val upAxis = Vectors.of(up.x, up.y, up.z)
            val forwardAxis = Vectors.of(forward.x, forward.y, forward.z)

            require(position.isFinite()) { "View origin must be finite" }
            require(rightAxis.isFinite() && upAxis.isFinite() && forwardAxis.isFinite()) { "View axes must be finite" }
            require(rightAxis.isNearlyNormalized(epsilon)) { "View right axis must have unit length" }
            require(upAxis.isNearlyNormalized(epsilon)) { "View up axis must have unit length" }
            require(forwardAxis.isNearlyNormalized(epsilon)) { "View forward axis must have unit length" }

            require(abs(rightAxis.dot(upAxis)) <= epsilon) { "View right and up axes must be perpendicular" }
            require(abs(rightAxis.dot(forwardAxis)) <= epsilon) { "View right and forward axes must be perpendicular" }
            require(abs(upAxis.dot(forwardAxis)) <= epsilon) { "View up and forward axes must be perpendicular" }

            val expectedForward = rightAxis.cross(upAxis)
            require(
                expectedForward.dot(forwardAxis) > 0.0 &&
                        (expectedForward - forwardAxis).isNearlyZero(epsilon)
            ) {
                "View basis must be right-handed: right × up = forward"
            }

            return ViewFrame(position, rightAxis, upAxis, forwardAxis)
        }
    }
}
