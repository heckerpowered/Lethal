/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * An oriented plane dividing space into two half-spaces.
 *
 * Points on the plane satisfy `normal · point = w`, where [x], [y] and [z]
 * form the normal. The normal points toward the positive half-space.
 * For example, `(0, 2, 0, 6)` describes `y = 3`, with positive values above it.
 * [w] is a distance from the origin only when the normal has unit length.
 *
 * Coefficients may be live values. [Planes.copyOf] samples each coefficient once;
 * sampling does not provide synchronization with concurrent changes.
 */
interface PlaneView : Interpolatable<PlaneView> {
    val x: Double
    val y: Double
    val z: Double
    val w: Double

    val normal: VectorView
        get() = Geometry.vector(x, y, z)

    val normalLengthSquared: Double
        get() = x * x + y * y + z * z

    val normalLength: Double
        get() = hypot(hypot(x, y), z)

    /** Closest point on the plane to the origin. Requires a finite, nonzero normal and finite offset. */
    val origin: VectorView
        get() {
            val plane = requireNotNull(normalizedOrNull(0.0)) { "Cannot find the origin of an invalid plane" }
            return plane.normal * plane.w
        }

    /** Evaluates `normal · point - w`. This is a signed distance only for a unit normal. */
    fun evaluate(point: VectorView): Double = x * point.x + y * point.y + z * point.z - w

    /** Signed perpendicular distance, positive in the direction of the normal. Does not require a unit normal. */
    fun signedDistanceTo(point: VectorView): Double {
        val plane = requireNotNull(normalizedOrNull(0.0)) { "Cannot measure distance to an invalid plane" }
        return plane.evaluate(point)
    }

    fun distanceTo(point: VectorView): Double = abs(signedDistanceTo(point))

    /** Tolerance is the minimum squared normal length, finite and nonnegative. */
    fun isValid(tolerance: Double = 1.0E-8): Boolean {
        require(tolerance.isFinite() && tolerance >= 0.0)
        val plane = Planes.copyOf(this)
        // Compare lengths to avoid overflow or underflow when squaring the normal length.
        val length = plane.normalLength
        val minimumLength = sqrt(tolerance)

        return plane.isFinite() &&
                length.isFinite() &&
                length > minimumLength
    }

    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()
    fun containsNaN(): Boolean = x.isNaN() || y.isNaN() || z.isNaN() || w.isNaN()

    fun isNormalized(epsilon: Double = 1.0E-6): Boolean {
        require(epsilon.isFinite() && epsilon >= 0.0)
        return abs(normalLength - 1.0) <= epsilon
    }

    /** Compares coefficients; proportional coefficients can describe the same plane without passing this test. */
    fun isNearlyEqual(other: PlaneView, epsilon: Double = 1.0E-6): Boolean {
        require(epsilon.isFinite() && epsilon >= 0.0)
        return abs(x - other.x) <= epsilon &&
                abs(y - other.y) <= epsilon &&
                abs(z - other.z) <= epsilon &&
                abs(w - other.w) <= epsilon
    }

    /** Compares geometric planes, ignoring coefficient scale and orientation. Invalid planes never match. */
    fun isSamePlane(other: PlaneView, epsilon: Double = 1.0E-6): Boolean {
        require(epsilon.isFinite() && epsilon >= 0.0)
        val first = normalizedOrNull(0.0) ?: return false
        val second = other.normalizedOrNull(0.0) ?: return false
        return first.isNearlyEqual(second, epsilon) || first.isNearlyEqual(-second, epsilon)
    }

    fun coefficientDot(other: PlaneView): Double = x * other.x + y * other.y + z * other.z + w * other.w

    operator fun get(index: Int): Double = when (index) {
        0 -> x
        1 -> y
        2 -> z
        3 -> w
        else -> throw IllegalArgumentException("Plane component index must be between 0 and 3: $index")
    }

    /** Linear coefficient interpolation; may produce a degenerate plane. Alpha is not clamped. */
    override fun interpolate(target: PlaneView, alpha: Double): PlaneView = Geometry.plane(
        x + (target.x - x) * alpha,
        y + (target.y - y) * alpha,
        z + (target.z - z) * alpha,
        w + (target.w - w) * alpha,
    )
}

/** Coefficient addition; does not represent a geometric union of planes. */
operator fun PlaneView.plus(other: PlaneView): PlaneView = Geometry.plane(x + other.x, y + other.y, z + other.z, w + other.w)
operator fun PlaneView.minus(other: PlaneView): PlaneView = Geometry.plane(x - other.x, y - other.y, z - other.z, w - other.w)

/** Reverses orientation while preserving the geometric plane. */
operator fun PlaneView.unaryMinus(): PlaneView = Geometry.plane(-x, -y, -z, -w)

/** Scales coefficients, preserving the geometric plane for finite nonzero scale; negative scale reverses orientation. */
operator fun PlaneView.times(scale: Double): PlaneView = Geometry.plane(x * scale, y * scale, z * scale, w * scale)
operator fun Double.times(plane: PlaneView): PlaneView = plane * this
operator fun PlaneView.div(scale: Double): PlaneView = Geometry.plane(x / scale, y / scale, z / scale, w / scale)

fun PlaneView.flipped(): PlaneView = -this

/** Normalizes all four coefficients without checking degeneracy or non-finite values. */
fun PlaneView.normalizedUnsafe(): PlaneView {
    val plane = Planes.copyOf(this)
    return plane / plane.normalLength
}

/**
 * Returns coefficients with a unit normal, preserving the plane and its orientation.
 * Returns null for non-finite input, length or output, or normal length squared at or below [tolerance].
 * Tolerance must be finite and nonnegative. Each source coefficient is sampled once.
 */
fun PlaneView.normalizedOrNull(tolerance: Double = 1.0E-8): PlaneView? {
    require(tolerance.isFinite() && tolerance >= 0.0)
    val plane = Planes.copyOf(this)
    if (!plane.isFinite()) return null

    // Compare lengths to avoid overflow or underflow when squaring the normal length.
    val length = plane.normalLength
    val minimumLength = sqrt(tolerance)

    if (!length.isFinite() ||
        length <= minimumLength
    ) {
        return null
    }

    return (plane / length).takeIf { it.isFinite() }
}

/** Uses [fallback] for any plane that cannot be safely normalized, including non-finite input. */
fun PlaneView.normalizedOr(fallback: PlaneView, tolerance: Double = 1.0E-8): PlaneView =
    normalizedOrNull(tolerance) ?: fallback

/** Returns a unit-normal plane, or throws [IllegalArgumentException] under the failure conditions of [normalizedOrNull]. */
fun PlaneView.normalized(tolerance: Double = 1.0E-8): PlaneView =
    requireNotNull(normalizedOrNull(tolerance)) { "Cannot normalize a degenerate or non-finite plane" }

/** Moves the plane by [offset], preserving its normal and coefficient scale. */
fun PlaneView.translated(offset: VectorView): PlaneView {
    val plane = Planes.copyOf(this)
    return Geometry.plane(plane.x, plane.y, plane.z, plane.w + plane.normal.dot(offset))
}

/** Orthogonal projection onto a finite plane with a nonzero normal. */
fun PlaneView.projectPosition(position: VectorView): VectorView {
    val plane = requireNotNull(normalizedOrNull(0.0)) { "Cannot project onto an invalid plane" }
    val point = Geometry.vector(position.x, position.y, position.z)
    return point - plane.normal * plane.evaluate(point)
}

/** Reflection across a finite plane with a nonzero normal. */
fun PlaneView.mirrorPosition(position: VectorView): VectorView {
    val plane = requireNotNull(normalizedOrNull(0.0)) { "Cannot reflect across an invalid plane" }
    val point = Geometry.vector(position.x, position.y, position.z)
    return point - plane.normal * (2.0 * plane.evaluate(point))
}

/**
 * Applies an affine transformation using the inverse transpose of the plane equation.
 * Supports scale, shear and reflection without requiring or producing a unit normal.
 * Both the plane and matrix are sampled once. Projective matrices are rejected.
 * Returns null for invalid planes, non-finite results or matrices rejected by [inverseOrNull].
 * [tolerance] has the same pivot threshold meaning as in [MatrixView.inverseOrNull].
 */
fun PlaneView.transformedByOrNull(matrix: MatrixView, tolerance: Double = 0.0): PlaneView? {
    require(tolerance.isFinite() && tolerance >= 0.0)

    val plane = Planes.copyOf(this)
    val transform = Matrices4.copyOf(matrix)

    require(transform.isAffine()) { "Plane transformation requires an affine matrix" }
    if (!plane.isValid(0.0)) return null

    val inverse = transform.inverseOrNull(tolerance) ?: return null

    // Plane covectors transform by the inverse transpose.
    return Geometry.plane(
        inverse.m00 * plane.x + inverse.m10 * plane.y + inverse.m20 * plane.z - inverse.m30 * plane.w,
        inverse.m01 * plane.x + inverse.m11 * plane.y + inverse.m21 * plane.z - inverse.m31 * plane.w,
        inverse.m02 * plane.x + inverse.m12 * plane.y + inverse.m22 * plane.z - inverse.m32 * plane.w,
        plane.w * inverse.m33 - plane.x * inverse.m03 - plane.y * inverse.m13 - plane.z * inverse.m23,
    ).takeIf { it.isValid(0.0) }
}

/** Like [transformedByOrNull], but throws [IllegalArgumentException] when no finite plane can be produced. */
fun PlaneView.transformedBy(matrix: MatrixView, tolerance: Double = 0.0): PlaneView =
    requireNotNull(transformedByOrNull(matrix, tolerance)) { "Cannot transform plane with an invalid or singular transform" }

object Planes {
    /** Degenerate coefficients, useful as a sentinel; does not describe a geometric plane. */
    val Zero: PlaneView = Geometry.plane(0.0, 0.0, 0.0, 0.0)

    fun of(x: Double, y: Double, z: Double, w: Double): PlaneView = Geometry.plane(x, y, z, w)
    fun fromNormal(normal: VectorView, w: Double): PlaneView = of(normal.x, normal.y, normal.z, w)
    fun copyOf(plane: PlaneView): PlaneView = of(plane.x, plane.y, plane.z, plane.w)

    /** Normalizes both the normal and offset, preserving the plane. Failure follows [PlaneView.normalized]. */
    fun normalized(
        x: Double,
        y: Double,
        z: Double,
        offset: Double,
    ): PlaneView = Geometry.plane(x, y, z, offset).normalized()

    /** Constructs a plane through [point], preserving the supplied normal's length. */
    fun fromPointAndNormal(point: VectorView, normal: VectorView): PlaneView {
        val direction = Geometry.vector(normal.x, normal.y, normal.z)
        return fromNormal(direction, direction.dot(point))
    }

    /**
     * Constructs a unit-normal plane through three points, oriented by `(second - first) × (third - first)`.
     * Returns null for degenerate or non-finite input or output.
     * [tolerance] is the minimum squared length of that cross product, finite and nonnegative.
     */
    fun fromPointsOrNull(first: VectorView, second: VectorView, third: VectorView, tolerance: Double = 1.0E-8): PlaneView? {
        require(tolerance.isFinite() && tolerance >= 0.0)
        val firstPoint = Geometry.vector(first.x, first.y, first.z)
        val unnormalizedNormal = (second - firstPoint).cross(third - firstPoint)
        val unitNormal = fromNormal(unnormalizedNormal, 0.0).normalizedOrNull(tolerance)?.normal ?: return null
        return fromPointAndNormal(firstPoint, unitNormal).takeIf { it.isFinite() }
    }

    fun fromPoints(first: VectorView, second: VectorView, third: VectorView, tolerance: Double = 1.0E-8): PlaneView =
        requireNotNull(fromPointsOrNull(first, second, third, tolerance)) { "Cannot construct a plane from degenerate or non-finite points" }
}
