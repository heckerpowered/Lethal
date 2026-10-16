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
 * Construction samples the inputs before validation and retains stable values.
 * Sampling does not synchronize concurrent changes. The inverse uses the transpose
 * of the basis, so deviations accepted by the tolerance also affect the inverse.
 *
 * Inputs must be finite, with unit lengths and pairwise perpendicular axes.
 * [epsilon] bounds squared-length error, pairwise dot products and each component of
 * `right × up - forward`; it must be finite and nonnegative. Opposite handedness is
 * rejected regardless of the tolerance.
 */
class ViewFrame(
    origin: VectorView,
    right: VectorView,
    up: VectorView,
    forward: VectorView,
    epsilon: Double = 1.0E-6,
) {
    val origin: VectorView = origin.snapshot()
    val right: VectorView = right.snapshot()
    val up: VectorView = up.snapshot()
    val forward: VectorView = forward.snapshot()

    init {
        require(epsilon.isFinite() && epsilon >= 0.0) { "View frame tolerance must be finite and nonnegative" }

        require(this.origin.isFinite()) { "View origin must be finite" }
        require(this.right.isNearlyNormalized(epsilon)) { "View right axis must have unit length" }
        require(this.up.isNearlyNormalized(epsilon)) { "View up axis must have unit length" }
        require(this.forward.isNearlyNormalized(epsilon)) { "View forward axis must have unit length" }

        require(abs(this.right.dot(this.up)) <= epsilon) { "View right and up axes must be perpendicular" }
        require(abs(this.right.dot(this.forward)) <= epsilon) { "View right and forward axes must be perpendicular" }
        require(abs(this.up.dot(this.forward)) <= epsilon) { "View up and forward axes must be perpendicular" }

        val expectedForward = this.right.cross(this.up)
        val isRightHanded = expectedForward.dot(this.forward) > 0.0 && (expectedForward - this.forward).isNearlyZero(epsilon)
        require(isRightHanded) { "View basis must be right-handed: right × up = forward" }
    }

    val worldToView: AffineTransformView = AffineTransform(
        axisX = Vector(this.right.x, this.up.x, this.forward.x),
        axisY = Vector(this.right.y, this.up.y, this.forward.y),
        axisZ = Vector(this.right.z, this.up.z, this.forward.z),
        translation = Vector(
            -this.right.dot(this.origin),
            -this.up.dot(this.origin),
            -this.forward.dot(this.origin),
        ),
    )
}
