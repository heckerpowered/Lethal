/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.math.*

interface VectorView : Interpolatable<VectorView> {
    val x: Double
    val y: Double
    val z: Double

    val lengthSquared
        get() = x * x + y * y + z * z
    val length
        get() = sqrt(lengthSquared)

    val horizontalLengthSquared: Double
        get() = x * x + z * z
    val horizontalLength: Double
        get() = sqrt(horizontalLengthSquared)

    fun dot(other: VectorView): Double {
        return x * other.x + y * other.y + z * other.z
    }

    fun distanceSquaredTo(other: VectorView): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    fun distanceTo(other: VectorView): Double {
        return sqrt(distanceSquaredTo(other))
    }

    fun isZero(): Boolean {
        return x == 0.0 && y == 0.0 && z == 0.0
    }

    fun isNearlyZero(epsilon: Double = 1.0E-6): Boolean {
        return abs(x) <= epsilon &&
                abs(y) <= epsilon &&
                abs(z) <= epsilon
    }

    fun isExactlyNormalized(): Boolean = lengthSquared == 1.0
    fun isNearlyNormalized(epsilon: Double = 1.0E-6): Boolean = abs(1.0 - lengthSquared) <= epsilon
    fun isNormalized(epsilon: Double = 1.0E-6): Boolean = isNearlyNormalized(epsilon)

    fun isFinite(): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
    }

    fun containsNan(): Boolean {
        return x.isNaN() || y.isNaN() || z.isNaN()
    }

    val maxComponent: Double
        get() = maxOf(x, y, z)
    val minComponent: Double
        get() = minOf(x, y, z)
    val absMaxComponent: Double
        get() = maxOf(abs(x), abs(y), abs(z))
    val absMinComponent: Double
        get() = minOf(abs(x), abs(y), abs(z))

    /**
     * Checks whether all components of this vector are the same, within a tolerance
     *
     * @param tolerance error tolerance
     * @return `true` if the vectors are equal within tolerance limits, `false` otherwise
     */
    fun allComponentsEqual(tolerance: Double = 1.0e-4): Boolean {
        return abs(x - y) <= tolerance &&
                abs(x - z) <= tolerance &&
                abs(y - z) <= tolerance
    }

    /**
     * Gets axis by an index, 0 for x, 1 for y, 2 for z
     *
     * @param index index of the axis
     * @return axis value
     * @throws IllegalArgumentException index is not 0, 1 or 2
     */
    operator fun get(index: Int): Double {
        return when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw IllegalArgumentException()
        }
    }

    override fun interpolate(target: VectorView, alpha: Double): VectorView {
        return Vector(
            x + (target.x - x) * alpha,
            y + (target.y - y) * alpha,
            z + (target.z - z) * alpha
        )
    }
}

operator fun VectorView.plus(other: VectorView): VectorView = Vector(x + other.x, y + other.y, z + other.z)
operator fun VectorView.minus(other: VectorView): VectorView = Vector(x - other.x, y - other.y, z - other.z)
operator fun VectorView.unaryMinus(): VectorView = Vector(-x, -y, -z)
operator fun VectorView.times(scale: Double): VectorView = Vector(x * scale, y * scale, z * scale)
operator fun VectorView.times(vector: VectorView): VectorView = Vector(x * vector.x, y * vector.y, z * vector.z)
operator fun Double.times(vector: VectorView): VectorView = vector * this
operator fun VectorView.div(scale: Double): VectorView = Vector(x / scale, y / scale, z / scale)

/**
 * Calculates the normalized version of vector without checking for zero length
 *
 * @return a normalized version of vector
 * @see normalizedOr
 */
fun VectorView.normalizedUnsafe(): VectorView {
    val scale = 1.0 / sqrt(x * x + y * y + z * z)
    return Vector(x * scale, y * scale, z * scale)
}

/**
 * Returns a unit vector, or null for non-finite input, length or output, or squared length
 * at or below [tolerance]. Tolerance must be finite and nonnegative.
 * Each source component is sampled once.
 */
fun VectorView.normalizedOrNull(tolerance: Double = 1.0E-8): VectorView? {
    require(tolerance.isFinite() && tolerance >= 0.0)
    val vector = Vector(x, y, z)
    if (!vector.isFinite()) return null

    // Compare lengths to avoid overflow or underflow when squaring the vector length.
    val length = hypot(hypot(vector.x, vector.y), vector.z)
    val minimumLength = sqrt(tolerance)

    if (!length.isFinite() || length <= minimumLength) {
        return null
    }

    return (vector / length).takeIf { it.isFinite() }
}

/** Uses [fallback] for any normalization failure, including non-finite input or output. */
fun VectorView.normalizedOr(fallback: VectorView, tolerance: Double = 1.0E-8): VectorView =
    normalizedOrNull(tolerance) ?: fallback

/** Returns a unit vector, or throws [IllegalArgumentException] under the failure conditions of [normalizedOrNull]. */
fun VectorView.normalized(tolerance: Double = 1.0E-8): VectorView =
    requireNotNull(normalizedOrNull(tolerance)) { "Cannot normalize a degenerate or non-finite vector" }

fun VectorView.normalized2DUnsafe(): VectorView {
    val scale = 1.0 / sqrt(x * x + z * z)
    return Vector(x * scale, 0.0, z * scale)
}

/**
 * Normalizes the XZ projection, setting Y to zero. Y is neither sampled nor validated.
 * Failure and tolerance follow [normalizedOrNull], using the squared XZ length.
 */
fun VectorView.normalized2DOrNull(tolerance: Double = 1.0E-8): VectorView? {
    require(tolerance.isFinite() && tolerance >= 0.0)
    return Vector(x, 0.0, z).normalizedOrNull(tolerance)
}

/** Uses [fallback] unchanged when [normalized2DOrNull] fails. */
fun VectorView.normalized2DOr(fallback: VectorView, tolerance: Double = 1.0E-8): VectorView =
    normalized2DOrNull(tolerance) ?: fallback

/** Returns a unit XZ vector, or throws [IllegalArgumentException] when [normalized2DOrNull] fails. */
fun VectorView.normalized2D(tolerance: Double = 1.0E-8): VectorView =
    requireNotNull(normalized2DOrNull(tolerance)) { "Cannot normalize a degenerate or non-finite XZ vector" }

fun VectorView.reciprocal(): VectorView {
    return Vector(1.0 / x, 1.0 / y, 1.0 / z)
}

fun VectorView.safeReciprocal(resultIfZero: VectorView = Vector(1.0E30, 1.0E30, 1.0E30)): VectorView {
    fun safeReciprocalComponent(value: Double, resultIfZero: Double): Double {
        if (value == .0) return if (value.toRawBits() < 0L) -resultIfZero else resultIfZero

        val result = 1.0 / value
        if (!result.isFinite()) return if (value < 0.0) -resultIfZero else resultIfZero

        return result.coerceIn(-resultIfZero, resultIfZero)
    }

    return Vector(
        safeReciprocalComponent(x, resultIfZero.x),
        safeReciprocalComponent(y, resultIfZero.y),
        safeReciprocalComponent(z, resultIfZero.z)
    )
}

fun VectorView.requireReciprocal(): VectorView {
    val x = x
    val y = y
    val z = z
    require(x != 0.0 && y != 0.0 && z != 0.0) { "Cannot take reciprocal of vector with zero component: $this" }

    // Do not use .reciprocal(), potential TOCTOU
    return Vector(1.0 / x, 1.0 / y, 1.0 / z)
}

fun VectorView.reciprocalOrNull(): VectorView? {
    val x = x
    val y = y
    val z = z
    if (x == 0.0 || y == 0.0 || z == 0.0) {
        return null
    }

    // Do not use .reciprocal(), potential TOCTOU
    return Vector(1.0 / x, 1.0 / y, 1.0 / z)
}

fun VectorView.cross(other: VectorView): VectorView {
    return Vector(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
}

fun VectorView.abs(): VectorView = Vector(abs(x), abs(y), abs(z))

/**
 * Gets the component-wise min of two vectors
 */
fun VectorView.componentMin(vector: VectorView): VectorView {
    return Vector(minOf(x, vector.x), minOf(y, vector.y), minOf(z, vector.z))
}

/**
 * Gets the component-wise max of two vectors
 */
fun VectorView.componentMax(vector: VectorView): VectorView {
    return Vector(maxOf(x, vector.x), maxOf(y, vector.y), maxOf(z, vector.z))
}

fun VectorView.withX(x: Double): VectorView = Vector(x, y, z)
fun VectorView.withY(y: Double): VectorView = Vector(x, y, z)
fun VectorView.withZ(z: Double): VectorView = Vector(x, y, z)

fun VectorView.floor(): VectorView = Vector(floor(x), floor(y), floor(z))
fun VectorView.ceil(): VectorView = Vector(ceil(x), ceil(y), ceil(z))
fun VectorView.round(): VectorView = Vector(round(x), round(y), round(z))

fun VectorView.coerceIn(min: VectorView, max: VectorView): VectorView {
    return Vector(
        x.coerceIn(min.x, max.x),
        y.coerceIn(min.y, max.y),
        z.coerceIn(min.z, max.z)
    )
}

fun VectorView.coerceComponentsIn(min: Double, max: Double): VectorView =
    Vector(
        x.coerceIn(min, max),
        y.coerceIn(min, max),
        z.coerceIn(min, max)
    )

fun VectorView.coerceComponentsIn(radius: Double): VectorView = coerceComponentsIn(-radius, radius)

fun VectorView.coerceLengthIn(min: Double, max: Double): VectorView {
    var vectorLength = length
    val vectorDirection = if (vectorLength > 1.0e-8) this / vectorLength else Vectors.Zero

    vectorLength = vectorLength.coerceIn(min, max)
    return vectorDirection * vectorLength
}

fun VectorView.coerceLengthAtMost(max: Double): VectorView {
    if (max < 1.0E-4) return Vectors.Zero
    val vectorLengthSquared = lengthSquared
    if (vectorLengthSquared <= max * max) return this

    val scale = max * (1.0 / sqrt(vectorLengthSquared))
    return this * scale
}

fun VectorView.coerceLengthAtLeast(min: Double, resultIfZero: VectorView = Vectors.Zero): VectorView {
    if (min <= 0.0) return this

    val vectorLengthSquared = lengthSquared
    if (vectorLengthSquared <= 1.0E-8) return resultIfZero
    if (vectorLengthSquared >= min * min) return this

    val scale = min * (1.0 / sqrt(vectorLengthSquared))
    return this * scale
}

fun VectorView.coerceHorizontalLengthIn(min: Double, max: Double): VectorView {
    val length2D = horizontalLength
    val direction = if (length2D > 1.0E-8) Vector(x / length2D, 0.0, z / length2D) else Vectors.Zero
    val coercedLength = length2D.coerceIn(min, max)
    return Vector(direction.x * coercedLength, y, direction.z * coercedLength)
}

fun VectorView.coerceHorizontalLengthAtMost(max: Double): VectorView {
    if (max <= 0.0) return Vector(0.0, y, 0.0)

    val lengthSquared2D = horizontalLengthSquared
    val maxSquared = max * max

    if (lengthSquared2D <= maxSquared) return this

    val scale = max / sqrt(lengthSquared2D)
    return Vector(x * scale, y, z * scale)
}

fun VectorView.coerceHorizontalLengthAtLeast(min: Double, resultIfZero: VectorView = Vectors.Zero): VectorView {
    if (min <= 0.0) return this

    val lengthSquared2D = horizontalLengthSquared
    if (lengthSquared2D <= 1.0E-8) return Vector(resultIfZero.x, y, resultIfZero.z)

    val minSquared = min * min
    if (lengthSquared2D >= minSquared) return this

    val scale = min / sqrt(lengthSquared2D)
    return Vector(x * scale, y, z * scale)
}

/**
 * Projects this vector onto another vector.
 *
 * The result is the part of this vector that points in the same direction as [axis].
 * For example, projecting a velocity onto a look direction gives the forward part
 * of that velocity.
 *
 * [axis] does not need to be normalized.
 *
 * @param axis The direction to project this vector onto.
 * @return The component of this vector along [axis].
 */
fun VectorView.projectOnto(axis: VectorView): VectorView {
    return axis * ((dot(axis) / axis.dot(axis)))
}

/**
 * Gets a copy of this vector projected onto the input vector, which is assumed to be unit length
 *
 * @param normal vector to project onto (assumed to be unit length)
 * @return projected vector
 */
fun VectorView.projectOntoNormal(normal: VectorView): VectorView {
    return normal * dot(normal)
}

/**
 * Calculate the projection of a vector on the plane defined by planeNormal.
 *
 * @param planeNormal normal of the plane (assumed to be unit length).
 * @return projection of vector onto plane.
 */
fun VectorView.projectOntoPlane(planeNormal: VectorView): VectorView {
    return this - projectOntoNormal(planeNormal)
}

fun VectorView.rotateAroundAxisRadians(angleRadians: Double, axis: VectorView): VectorView {
    val sin = sin(angleRadians)
    val cos = cos(angleRadians)
    val oneMinusCos = 1.0 - cos

    val dot = dot(axis)
    val cross = axis.cross(this)
    val rotatedX = x * cos + cross.x * sin + axis.x * dot * oneMinusCos
    val rotatedY = y * cos + cross.y * sin + axis.y * dot * oneMinusCos
    val rotatedZ = z * cos + cross.z * sin + axis.z * dot * oneMinusCos

    return Vector(rotatedX, rotatedY, rotatedZ)
}

/**
 * Rotates around Axis (assumes axis.size() == 1)
 *
 * @param angleDegrees angle to rotate (in degrees)
 * @param axis axis to rotate around
 * @return rotated vector
 */
fun VectorView.rotateAngleAxis(angleDegrees: Double, axis: VectorView): VectorView {
    return rotateAroundAxisRadians(Math.toRadians(angleDegrees), axis)
}

/**
 * Mirror a vector about a normal vector
 *
 * @param mirrorNormal the normal vector to mirror about
 * @return mirrored vector
 */
fun VectorView.mirrorByVector(mirrorNormal: VectorView): VectorView {
    return this - mirrorNormal * ((dot(mirrorNormal)) * 2.0)
}

/**
 * Gets a copy of this vector snapped to a grid
 *
 * @param gridSize grid dimension
 * @return a copy of this vector snapped to a grid
 */
fun VectorView.gridSnap(gridSize: Double): VectorView {
    fun snapToGrid(value: Double, grid: Double): Double {
        if (grid == 0.0) return value
        return round(value / grid) * grid
    }

    return Vector(
        snapToGrid(x, gridSize),
        snapToGrid(y, gridSize),
        snapToGrid(z, gridSize)
    )
}

fun VectorView.sign(): VectorView =
    Vector(sign(x), sign(y), sign(z))

fun VectorView.horizontalHeadingRadians(): Double {
    return atan2(z, x)
}

fun VectorView.createPerpendicularBasis(): BasisView {
    val forward = normalized()
    val temporaryRight = if (abs(forward.z) > abs(forward.x) && abs(forward.z) > abs(forward.y)) Vectors.UnitX else Vectors.UnitZ

    val right = (temporaryRight - forward * temporaryRight.dot(forward))
        .normalized()
    val up = forward
        .cross(right)
        .normalized()

    return Basis(right, up, forward)
}

fun VectorView.asPointBox(): BoxView {
    return Box(this, this)
}

object Vectors {
    val Zero: VectorView = Vector(0.0, 0.0, 0.0)
    val One: VectorView = Vector(1.0, 1.0, 1.0)

    val UnitX: VectorView = Vector(1.0, 0.0, 0.0)
    val UnitY: VectorView = Vector(0.0, 1.0, 0.0)
    val UnitZ: VectorView = Vector(0.0, 0.0, 1.0)

    val NegativeUnitX: VectorView = Vector(-1.0, 0.0, 0.0)
    val NegativeUnitY: VectorView = Vector(0.0, -1.0, 0.0)
    val NegativeUnitZ: VectorView = Vector(0.0, 0.0, -1.0)

    fun of(x: Double, y: Double, z: Double): VectorView {
        return Vector(x, y, z)
    }

    fun min(a: VectorView, b: VectorView): VectorView {
        return Vector(min(a.x, b.x), min(a.y, b.y), min(a.z, b.z))
    }

    fun max(a: VectorView, b: VectorView): VectorView {
        return Vector(max(a.x, b.x), max(a.y, b.y), max(a.z, b.z))
    }

    fun distanceSquared(a: VectorView, b: VectorView) = a.distanceSquaredTo(b)
    fun distance(a: VectorView, b: VectorView) = a.distanceTo(b)
    fun dot(a: VectorView, b: VectorView) = a.dot(b)
    fun cross(a: VectorView, b: VectorView) = a.cross(b)
    fun lerp(a: VectorView, b: VectorView, t: Double) = a.interpolate(b, t)
}