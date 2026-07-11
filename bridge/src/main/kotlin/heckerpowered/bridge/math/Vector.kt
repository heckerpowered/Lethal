/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

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
        return !x.isFinite() ||
                !y.isFinite() ||
                !z.isFinite()
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
        return Geometry.vector(
            x + (target.x - x) * alpha,
            y + (target.y - y) * alpha,
            z + (target.z - z) * alpha
        )
    }
}

operator fun VectorView.plus(other: VectorView): VectorView = Geometry.vector(x + other.x, y + other.y, z + other.z)
operator fun VectorView.minus(other: VectorView): VectorView = Geometry.vector(x - other.x, y - other.y, z - other.z)
operator fun VectorView.unaryMinus(): VectorView = Geometry.vector(-x, -y, -z)
operator fun VectorView.times(scale: Double): VectorView = Geometry.vector(x * scale, y * scale, z * scale)
operator fun Double.times(vector: VectorView): VectorView = vector * this
operator fun VectorView.div(scale: Double): VectorView = Geometry.vector(x / scale, y / scale, z / scale)

/**
 * Calculates the normalized version of vector without checking for zero length
 *
 * @return a normalized version of vector
 * @see safeNormal
 */
fun VectorView.unsafeNormal(): VectorView {
    val scale = 1.0 / sqrt(x * x + y * y + z * z)
    return Geometry.vector(x * scale, y * scale, z * scale)
}

/**
 * Gets a normalized copy of the vector, checking it is safe to do so based on the length.
 * Returns zero vector by default if vector length is too small to safely normalize
 *
 * @param tolerance minimum squared vector length
 * @param resultIfZero return value if unsafe
 * @return a normalized copy if safe, `resultIfZero` otherwise
 */
fun VectorView.safeNormal(tolerance: Double = 1.0e-8, resultIfZero: VectorView = Vectors.Zero): VectorView {
    val squareSum = x * x + y * y + z * z

    if (squareSum == 1.0) return this
    else if (squareSum < tolerance) return resultIfZero

    val scale = 1.0 / sqrt(squareSum)
    return Geometry.vector(x * scale, y * scale, z * scale)
}

fun VectorView.normalized(): VectorView {
    return safeNormal()
}

fun VectorView.unsafeNormal2D(): VectorView {
    val scale = 1.0 / sqrt(x * x + z * z)
    return Geometry.vector(x * scale, 0.0, z * scale)
}

/**
 * Gets a normalized copy of the 2D components of the vector, checking it is safe to do so. Y is set to zero.
 * Returns zero vector by default if vector length is too small to normalize
 *
 * @param tolerance minimum squared vector length.
 * @param resultIfZero return value if unsafe
 * @return a normalized copy if safe, `resultIfZero` otherwise
 */
fun VectorView.safeNormal2D(tolerance: Double = 1.0e-8, resultIfZero: VectorView = Vectors.Zero): VectorView {
    val squareSum = x * x + z * z

    // Not sure if it is safe to add tolerance in there. Might introduce too many errors
    if (squareSum == 1.0) return if (y == 0.0) this else Geometry.vector(x, 0.0, z)
    else if (squareSum <= tolerance) return resultIfZero

    val scale = 1.0 / sqrt(squareSum)
    return Geometry.vector(x * scale, 0.0, z * scale)
}

fun VectorView.normalized2D(): VectorView {
    return safeNormal2D()
}

fun VectorView.reciprocal(): VectorView {
    return Geometry.vector(
        1.0 / x,
        1.0 / y,
        1.0 / z
    )
}

fun VectorView.safeReciprocal(
    resultIfZero: VectorView = Geometry.vector(1.0E30, 1.0E30, 1.0E30),
): VectorView {
    fun safeReciprocalComponent(value: Double, resultIfZero: Double): Double {
        if (value == .0) return if (value.toRawBits() < 0L) -resultIfZero else resultIfZero

        val result = 1.0 / value
        if (!result.isFinite()) return if (value < 0.0) -resultIfZero else resultIfZero

        return result.coerceIn(-resultIfZero, resultIfZero)
    }

    return Geometry.vector(
        safeReciprocalComponent(x, resultIfZero.x),
        safeReciprocalComponent(y, resultIfZero.y),
        safeReciprocalComponent(z, resultIfZero.z),
    )
}

fun VectorView.requireReciprocal(): VectorView {
    val x = x
    val y = y
    val z = z
    require(x != 0.0 && y != 0.0 && z != 0.0) {
        "Cannot take reciprocal of vector with zero component: $this"
    }

    // Do not use .reciprocal(), potential TOCTOU
    return Geometry.vector(
        1.0 / x,
        1.0 / y,
        1.0 / z
    )
}

fun VectorView.reciprocalOrNull(): VectorView? {
    val x = x
    val y = y
    val z = z
    if (x == 0.0 || y == 0.0 || z == 0.0) {
        return null
    }

    // Do not use .reciprocal(), potential TOCTOU
    return Geometry.vector(
        1.0 / x,
        1.0 / y,
        1.0 / z
    )
}


fun VectorView.cross(other: VectorView): VectorView {
    return Geometry.vector(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
}

fun VectorView.abs(): VectorView = Geometry.vector(abs(x), abs(y), abs(z))

/**
 * Gets the component-wise min of two vectors
 */
fun VectorView.componentMin(vector: VectorView): VectorView {
    return Geometry.vector(
        minOf(x, vector.x),
        minOf(y, vector.y),
        minOf(z, vector.z)
    )
}

/**
 * Gets the component-wise max of two vectors
 */
fun VectorView.componentMax(vector: VectorView): VectorView {
    return Geometry.vector(
        maxOf(x, vector.x),
        maxOf(y, vector.y),
        maxOf(z, vector.z)
    )
}

fun VectorView.withX(x: Double): VectorView = Geometry.vector(x, y, z)
fun VectorView.withY(y: Double): VectorView = Geometry.vector(x, y, z)
fun VectorView.withZ(z: Double): VectorView = Geometry.vector(x, y, z)

fun VectorView.floor(): VectorView = Geometry.vector(floor(x), floor(y), floor(z))
fun VectorView.ceil(): VectorView = Geometry.vector(ceil(x), ceil(y), ceil(z))
fun VectorView.round(): VectorView = Geometry.vector(round(x), round(y), round(z))

fun VectorView.coerceIn(min: VectorView, max: VectorView): VectorView {
    return Geometry.vector(
        x.coerceIn(min.x, max.x),
        y.coerceIn(min.y, max.y),
        z.coerceIn(min.z, max.z)
    )
}

fun VectorView.coerceComponentsIn(min: Double, max: Double): VectorView = Geometry.vector(
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
    val direction = if (length2D > 1.0E-8) Geometry.vector(x / length2D, 0.0, z / length2D) else Vectors.Zero
    val coercedLength = length2D.coerceIn(min, max)
    return Geometry.vector(direction.x * coercedLength, y, direction.z * coercedLength)
}

fun VectorView.coerceHorizontalLengthAtMost(max: Double): VectorView {
    if (max <= 0.0) return Geometry.vector(0.0, y, 0.0)

    val lengthSquared2D = horizontalLengthSquared
    val maxSquared = max * max

    if (lengthSquared2D <= maxSquared) return this

    val scale = max / sqrt(lengthSquared2D)
    return Geometry.vector(x * scale, y, z * scale)
}

fun VectorView.coerceHorizontalLengthAtLeast(min: Double, resultIfZero: VectorView = Vectors.Zero): VectorView {
    if (min <= 0.0) return this

    val lengthSquared2D = horizontalLengthSquared
    if (lengthSquared2D <= 1.0E-8) return Geometry.vector(resultIfZero.x, y, resultIfZero.z)

    val minSquared = min * min
    if (lengthSquared2D >= minSquared) return this

    val scale = min / sqrt(lengthSquared2D)
    return Geometry.vector(x * scale, y, z * scale)
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

    return Geometry.vector(
        x * cos + cross.x * sin + axis.x * dot * oneMinusCos,
        y * cos + cross.y * sin + axis.y * dot * oneMinusCos,
        z * cos + cross.z * sin + axis.z * dot * oneMinusCos
    )
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

    return Geometry.vector(
        snapToGrid(x, gridSize),
        snapToGrid(y, gridSize),
        snapToGrid(z, gridSize)
    )
}

fun VectorView.sign(): VectorView =
    Geometry.vector(sign(x), sign(y), sign(z))

fun VectorView.horizontalHeadingRadians(): Double {
    return atan2(z, x)
}

fun VectorView.toRotator(): RotatorView {
    val horizontalLength = sqrt(x * x + z * z)
    return Geometry.rotator(
        pitch = Math.toDegrees(atan2(-y, horizontalLength)),
        yaw = Math.toDegrees(-atan2(x, z)),
        roll = 0.0
    )
}

fun VectorView.createPerpendicularBasis(): Basis3dView {
    val forward = normalized()
    val temporaryRight = if (abs(forward.z) > abs(forward.x) && abs(forward.z) > abs(forward.y)) {
        Vectors.UnitX
    } else {
        Vectors.UnitZ
    }

    val right = (temporaryRight - forward * temporaryRight.dot(forward)).normalized()
    val up = forward.cross(right).normalized()

    return Basis3d(right, up, forward)
}

fun VectorView.asPointBox(): BoxView {
    return Geometry.box(this, this)
}

object Vectors {
    val Zero: VectorView = Geometry.vector(0.0, 0.0, 0.0)
    val One: VectorView = Geometry.vector(1.0, 1.0, 1.0)

    val UnitX: VectorView = Geometry.vector(1.0, 0.0, 0.0)
    val UnitY: VectorView = Geometry.vector(0.0, 1.0, 0.0)
    val UnitZ: VectorView = Geometry.vector(0.0, 0.0, 1.0)

    val NegativeUnitX: VectorView = Geometry.vector(-1.0, 0.0, 0.0)
    val NegativeUnitY: VectorView = Geometry.vector(0.0, -1.0, 0.0)
    val NegativeUnitZ: VectorView = Geometry.vector(0.0, 0.0, -1.0)

    fun of(x: Double, y: Double, z: Double): VectorView {
        return Geometry.vector(x, y, z)
    }

    fun min(a: VectorView, b: VectorView): VectorView {
        return Geometry.vector(
            min(a.x, b.x),
            min(a.y, b.y),
            min(a.z, b.z)
        )
    }

    fun max(a: VectorView, b: VectorView): VectorView {
        return Geometry.vector(
            max(a.x, b.x),
            max(a.y, b.y),
            max(a.z, b.z)
        )
    }

    fun distanceSquared(a: VectorView, b: VectorView) = a.distanceSquaredTo(b)
    fun distance(a: VectorView, b: VectorView) = a.distanceTo(b)
    fun dot(a: VectorView, b: VectorView) = a.dot(b)
    fun cross(a: VectorView, b: VectorView) = a.cross(b)
    fun lerp(a: VectorView, b: VectorView, t: Double) = a.interpolate(b, t)
}

object MinecraftDirections {
    /**
     * Up vector: (0, 1, 0).
     */
    val Up: VectorView = Geometry.vector(0.0, 1.0, 0.0)

    /**
     * Down vector: (0, -1, 0).
     */
    val Down: VectorView = Geometry.vector(0.0, -1.0, 0.0)

    /**
     * Minecraft forward vector: (0, 0, 1).
     *
     * This matches Minecraft's view direction when pitch = 0 and yaw = 0.
     */
    val Forward: VectorView = Geometry.vector(0.0, 0.0, 1.0)

    /**
     * Minecraft backward vector: (0, 0, -1).
     */
    val Backward: VectorView = Geometry.vector(0.0, 0.0, -1.0)

    /**
     * Minecraft right vector: (-1, 0, 0).
     *
     * In Minecraft, positive yaw rotates from +Z toward -X.
     */
    val Right: VectorView = Geometry.vector(-1.0, 0.0, 0.0)

    /**
     * Minecraft left vector: (1, 0, 0).
     */
    val Left: VectorView = Geometry.vector(1.0, 0.0, 0.0)
}
