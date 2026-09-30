/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.math.*

interface QuaternionView : Interpolatable<QuaternionView> {
    val x: Double
    val y: Double
    val z: Double
    val w: Double

    val lengthSquared: Double
        get() = x * x + y * y + z * z + w * w

    val length: Double
        get() = sqrt(lengthSquared)

    fun dot(other: QuaternionView): Double = x * other.x + y * other.y + z * other.z + w * other.w

    fun isZero(): Boolean = x == 0.0 && y == 0.0 && z == 0.0 && w == 0.0

    fun isNearlyZero(epsilon: Double = 1.0E-6): Boolean =
        abs(x) <= epsilon && abs(y) <= epsilon && abs(z) <= epsilon && abs(w) <= epsilon

    fun isExactlyNormalized(): Boolean = lengthSquared == 1.0
    fun isNearlyNormalized(epsilon: Double = 1.0E-6): Boolean = abs(1.0 - lengthSquared) <= epsilon
    fun isNormalized(epsilon: Double = 1.0E-6): Boolean = isNearlyNormalized(epsilon)

    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()
    fun containsNan(): Boolean = x.isNaN() || y.isNaN() || z.isNaN() || w.isNaN()

    fun isNearlyEqual(other: QuaternionView, epsilon: Double = 1.0E-6): Boolean =
        abs(x - other.x) <= epsilon && abs(y - other.y) <= epsilon &&
                abs(z - other.z) <= epsilon && abs(w - other.w) <= epsilon

    fun isSameRotation(other: QuaternionView, epsilon: Double = 1.0E-6): Boolean =
        isNearlyEqual(other, epsilon) ||
                (abs(x + other.x) <= epsilon && abs(y + other.y) <= epsilon &&
                        abs(z + other.z) <= epsilon && abs(w + other.w) <= epsilon)

    fun isIdentity(epsilon: Double = 1.0E-6): Boolean = isSameRotation(Quaternions.Identity, epsilon)

    operator fun get(index: Int): Double = when (index) {
        0 -> x
        1 -> y
        2 -> z
        3 -> w
        else -> throw IllegalArgumentException("Quaternion component index must be between 0 and 3: $index")
    }

    override fun interpolate(target: QuaternionView, alpha: Double): QuaternionView = sphericalInterpolate(target, alpha)
}

operator fun QuaternionView.plus(other: QuaternionView): QuaternionView =
    Geometry.quaternion(x + other.x, y + other.y, z + other.z, w + other.w)

operator fun QuaternionView.minus(other: QuaternionView): QuaternionView =
    Geometry.quaternion(x - other.x, y - other.y, z - other.z, w - other.w)

operator fun QuaternionView.unaryMinus(): QuaternionView = Geometry.quaternion(-x, -y, -z, -w)
operator fun QuaternionView.times(scale: Double): QuaternionView = Geometry.quaternion(x * scale, y * scale, z * scale, w * scale)
operator fun Double.times(quaternion: QuaternionView): QuaternionView = quaternion * this
operator fun QuaternionView.div(scale: Double): QuaternionView = Geometry.quaternion(x / scale, y / scale, z / scale, w / scale)

/** Composes rotations: `(first * second)` applies second, then first. */
operator fun QuaternionView.times(other: QuaternionView): QuaternionView = Geometry.quaternion(
    w * other.x + x * other.w + y * other.z - z * other.y,
    w * other.y - x * other.z + y * other.w + z * other.x,
    w * other.z + x * other.y - y * other.x + z * other.w,
    w * other.w - x * other.x - y * other.y - z * other.z
)

fun QuaternionView.withX(x: Double): QuaternionView = Geometry.quaternion(x, y, z, w)
fun QuaternionView.withY(y: Double): QuaternionView = Geometry.quaternion(x, y, z, w)
fun QuaternionView.withZ(z: Double): QuaternionView = Geometry.quaternion(x, y, z, w)
fun QuaternionView.withW(w: Double): QuaternionView = Geometry.quaternion(x, y, z, w)

/** Normalizes without checking for zero length or non-finite components. */
fun QuaternionView.normalizedUnsafe(): QuaternionView = this / length

/**
 * Returns a unit quaternion, or null for non-finite input, length or output, or squared length
 * at or below [tolerance]. Tolerance must be finite and nonnegative.
 * Each source component is sampled once.
 */
fun QuaternionView.normalizedOrNull(tolerance: Double = 1.0E-8): QuaternionView? {
    require(tolerance.isFinite() && tolerance >= 0.0)
    val quaternion = Geometry.quaternion(x, y, z, w)
    if (!quaternion.isFinite()) return null

    // Compare lengths to avoid overflow or underflow when squaring the quaternion length.
    val length = hypot(hypot(quaternion.x, quaternion.y), hypot(quaternion.z, quaternion.w))
    val minimumLength = sqrt(tolerance)

    if (!length.isFinite() || length <= minimumLength) {
        return null
    }

    return (quaternion / length).takeIf { it.isFinite() }
}

/** Uses [fallback] for any normalization failure, including non-finite input or output. */
fun QuaternionView.normalizedOr(fallback: QuaternionView, tolerance: Double = 1.0E-8): QuaternionView =
    normalizedOrNull(tolerance) ?: fallback

/** Returns a unit quaternion, or throws [IllegalArgumentException] under the failure conditions of [normalizedOrNull]. */
fun QuaternionView.normalized(tolerance: Double = 1.0E-8): QuaternionView =
    requireNotNull(normalizedOrNull(tolerance)) { "Cannot normalize a degenerate or non-finite quaternion" }

/** For a unit quaternion, conjugation reverses the rotation. */
fun QuaternionView.conjugate(): QuaternionView = Geometry.quaternion(-x, -y, -z, w)

/** Algebraic inverse, including non-unit quaternions. Zero has no inverse and produces NaN. */
fun QuaternionView.inverse(): QuaternionView = conjugate() / lengthSquared

fun QuaternionView.inverseOrNull(): QuaternionView? {
    val x = x
    val y = y
    val z = z
    val w = w
    val squareSum = x * x + y * y + z * z + w * w
    if (squareSum == 0.0 || !squareSum.isFinite()) return null
    return Geometry.quaternion(-x / squareSum, -y / squareSum, -z / squareSum, w / squareSum)
}

fun QuaternionView.requireInverse(): QuaternionView =
    requireNotNull(inverseOrNull()) { "Quaternion must have a finite, positive squared length to invert" }

/** Rotates a vector by this unit quaternion, preserving its length. */
fun QuaternionView.rotateVector(vector: VectorView): VectorView {
    val imaginary = Geometry.vector(x, y, z)
    val twiceCross = imaginary.cross(vector) * 2.0
    return vector + twiceCross * w + imaginary.cross(twiceCross)
}

/** Reverses the rotation of this unit quaternion. */
fun QuaternionView.unrotateVector(vector: VectorView): VectorView = conjugate().rotateVector(vector)

operator fun QuaternionView.times(vector: VectorView): VectorView = rotateVector(vector)

fun QuaternionView.axisX(): VectorView = rotateVector(Vectors.UnitX)
fun QuaternionView.axisY(): VectorView = rotateVector(Vectors.UnitY)
fun QuaternionView.axisZ(): VectorView = rotateVector(Vectors.UnitZ)

/** For a unit quaternion, returns rotated +X, +Y and +Z as right, up and forward. */
fun QuaternionView.toBasis(): Basis3dView = Basis3d(axisX(), axisY(), axisZ())

/** Shortest separation in [0, PI] radians. Both quaternions must be normalized. */
fun QuaternionView.angularDistanceRadians(other: QuaternionView): Double =
    2.0 * acos(abs(dot(other)).coerceIn(0.0, 1.0))

fun QuaternionView.angularDistanceDegrees(other: QuaternionView): Double = Math.toDegrees(angularDistanceRadians(other))

/** Shortest angle from identity in [0, PI]. This quaternion must be normalized. */
fun QuaternionView.angleRadians(): Double = 2.0 * acos(abs(w).coerceIn(0.0, 1.0))
fun QuaternionView.angleDegrees(): Double = Math.toDegrees(angleRadians())

/** Axis of the shortest rotation. Identity has no unique axis and returns [resultIfIdentity]. */
fun QuaternionView.rotationAxis(resultIfIdentity: VectorView = Vectors.UnitX): VectorView {
    val imaginary = Geometry.vector(x, y, z)
    val squareSum = imaginary.lengthSquared
    if (squareSum == 0.0) return resultIfIdentity
    val direction = if (w < 0.0) -imaginary else imaginary
    return direction / sqrt(squareSum)
}

/** Component-wise interpolation; does not normalize or choose a rotation path. */
fun QuaternionView.lerp(target: QuaternionView, alpha: Double): QuaternionView = Geometry.quaternion(
    x + (target.x - x) * alpha,
    y + (target.y - y) * alpha,
    z + (target.z - z) * alpha,
    w + (target.w - w) * alpha
)

/**
 * Interpolates between unit quaternions using normalized linear interpolation.
 *
 * The shortest rotational path is chosen by aligning equivalent quaternion
 * representations before interpolation. Unlike [sphericalInterpolate], the angular velocity
 * is not constant.
 *
 * [alpha] is not clamped and may be outside the range `[0, 1]`.
 */
fun QuaternionView.normalizedInterpolate(target: QuaternionView, alpha: Double): QuaternionView {
    val alignedTarget = if (dot(target) < 0.0) -target else target
    return lerp(alignedTarget, alpha).normalized()
}

/**
 * Interpolates between unit quaternions along the shortest spherical path.
 *
 * The interpolation proceeds at constant angular velocity, so [alpha]
 * represents the fraction of the rotation arc traversed.
 *
 * [alpha] is not clamped and may be outside the range `[0, 1]`.
 */
fun QuaternionView.sphericalInterpolate(target: QuaternionView, alpha: Double): QuaternionView {
    val cosine = dot(target)
    val alignedTarget = if (cosine < 0.0) -target else target
    val alignedCosine = abs(cosine).coerceIn(0.0, 1.0)
    if (alignedCosine > 0.9995) return lerp(alignedTarget, alpha).normalized()

    val angle = acos(alignedCosine)
    val sine = sin(angle)
    return (this * (sin((1.0 - alpha) * angle) / sine) +
            alignedTarget * (sin(alpha * angle) / sine)).normalized()
}

object Quaternions {
    val Zero: QuaternionView = Geometry.quaternion(0.0, 0.0, 0.0, 0.0)
    val Identity: QuaternionView = Geometry.quaternion(0.0, 0.0, 0.0, 1.0)

    fun of(x: Double, y: Double, z: Double, w: Double): QuaternionView = Geometry.quaternion(x, y, z, w)

    /** Axis must be normalized; positive angles follow [VectorView.rotateAroundAxisRadians]. */
    fun fromAxisAngleRadians(axis: VectorView, angleRadians: Double): QuaternionView {
        val halfAngle = angleRadians * 0.5
        val scale = sin(halfAngle)
        return Geometry.quaternion(axis.x * scale, axis.y * scale, axis.z * scale, cos(halfAngle))
    }

    fun fromAxisAngleDegrees(axis: VectorView, angleDegrees: Double): QuaternionView =
        fromAxisAngleRadians(axis, Math.toRadians(angleDegrees))

    /**
     * Shortest rotation between nonzero directions, which need not be normalized.
     * Returns identity if either direction cannot be normalized with the default tolerance. Opposite directions
     * use a perpendicular axis; that axis is not unique.
     */
    fun fromTo(from: VectorView, to: VectorView): QuaternionView {
        val start = from.normalizedOrNull() ?: return Identity
        val end = to.normalizedOrNull() ?: return Identity
        val cosine = start.dot(end).coerceIn(-1.0, 1.0)
        val cross = start.cross(end)
        // atan2 retains the small deviation from PI when 1 + dot rounds to zero.
        if (cross.lengthSquared > 0.0) {
            return fromAxisAngleRadians(cross.normalizedUnsafe(), atan2(cross.length, cosine))
        }
        if (cosine >= 0.0) return Identity
        val reference = if (abs(start.x) < abs(start.z)) Vectors.UnitX else Vectors.UnitZ
        return fromAxisAngleRadians(start.cross(reference).normalizedUnsafe(), PI)
    }

    /**
     * Converts a right-handed orthonormal basis whose columns are rotated +X, +Y and +Z.
     * Scale, shear and reflections must be removed by the caller.
     */
    fun fromBasis(basis: Basis3dView): QuaternionView {
        val right = basis.right
        val up = basis.up
        val forward = basis.forward
        val trace = right.x + up.y + forward.z
        val quaternion = when {
            trace > 0.0 -> {
                val scale = 2.0 * sqrt(trace + 1.0)
                Geometry.quaternion((up.z - forward.y) / scale, (forward.x - right.z) / scale, (right.y - up.x) / scale, scale * 0.25)
            }

            right.x > up.y && right.x > forward.z -> {
                val scale = 2.0 * sqrt(1.0 + right.x - up.y - forward.z)
                Geometry.quaternion(scale * 0.25, (up.x + right.y) / scale, (forward.x + right.z) / scale, (up.z - forward.y) / scale)
            }

            up.y > forward.z -> {
                val scale = 2.0 * sqrt(1.0 + up.y - right.x - forward.z)
                Geometry.quaternion((up.x + right.y) / scale, scale * 0.25, (forward.y + up.z) / scale, (forward.x - right.z) / scale)
            }

            else -> {
                val scale = 2.0 * sqrt(1.0 + forward.z - right.x - up.y)
                Geometry.quaternion((forward.x + right.z) / scale, (forward.y + up.z) / scale, scale * 0.25, (right.y - up.x) / scale)
            }
        }
        return quaternion.normalized()
    }

    fun dot(a: QuaternionView, b: QuaternionView): Double = a.dot(b)
    fun lerp(a: QuaternionView, b: QuaternionView, t: Double): QuaternionView = a.lerp(b, t)
    fun normalizedInterpolate(a: QuaternionView, b: QuaternionView, t: Double): QuaternionView = a.normalizedInterpolate(b, t)
    fun sphericalInterpolate(a: QuaternionView, b: QuaternionView, t: Double): QuaternionView = a.sphericalInterpolate(b, t)
}
