/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.math.abs

/**
 * A read-only 4 x 4 matrix acting on column vectors. The first component index is
 * the row, the second is the column; translation occupies m03, m13 and m23.
 * `(first * second)` applies second, then first, matching QuaternionView and TransformView.
 * This interface does not prescribe the backing storage order.
 */
interface MatrixView : Interpolatable<MatrixView>, Iterable<Double> {
    val m00: Double
    val m01: Double
    val m02: Double
    val m03: Double

    val m10: Double
    val m11: Double
    val m12: Double
    val m13: Double

    val m20: Double
    val m21: Double
    val m22: Double
    val m23: Double

    val m30: Double
    val m31: Double
    val m32: Double
    val m33: Double

    operator fun get(row: Int, column: Int): Double {
        require(row in 0..3 && column in 0..3) { "Matrix coordinates must be between zero and three" }
        return when (row * 4 + column) {
            0 -> m00
            1 -> m01
            2 -> m02
            3 -> m03
            4 -> m10
            5 -> m11
            6 -> m12
            7 -> m13
            8 -> m20
            9 -> m21
            10 -> m22
            11 -> m23
            12 -> m30
            13 -> m31
            14 -> m32
            15 -> m33
            else -> error("Matrix coordinates must be between zero and three")
        }
    }

    fun isFinite(): Boolean = all { it.isFinite() }
    fun containsNaN(): Boolean = any { it.isNaN() }
    fun isZero(): Boolean = all { it == 0.0 }

    /** Epsilon must be finite and nonnegative. */
    fun isNearlyEqual(other: MatrixView, epsilon: Double = 1.0E-6): Boolean {
        require(epsilon.isFinite() && epsilon >= 0.0)
        return zip(other).all { [left, right] -> abs(left - right) <= epsilon }
    }

    fun isIdentity(epsilon: Double = 1.0E-6): Boolean = isNearlyEqual(Matrices.Identity, epsilon)

    /** Tests the affine last-row constraint. Epsilon must be finite and nonnegative; zero requires an exact match. */
    fun isAffine(epsilon: Double = 0.0): Boolean {
        require(epsilon.isFinite() && epsilon >= 0.0)
        return abs(m30) <= epsilon &&
                abs(m31) <= epsilon &&
                abs(m32) <= epsilon &&
                abs(m33 - 1.0) <= epsilon
    }

    val determinant: Double
        get() {
            val snapshot = Matrices.copyOf(this)
            return (0..3).sumOf { column -> snapshot[0, column] * snapshot.cofactor(0, column) }
        }

    /** Determinant of the linear 3 x 3 part, including scale and shear, computed from one snapshot. */
    val linearDeterminant: Double
        get() = Matrices.copyOf(this).computeLinearDeterminant()

    /** Local +X axis of the linear 3 x 3 part, including scale and shear. */
    val axisX: VectorView
        get() = Geometry.vector(m00, m10, m20)

    /** Local +Y axis of the linear 3 x 3 part, including scale and shear. */
    val axisY: VectorView
        get() = Geometry.vector(m01, m11, m21)

    /** Local +Z axis of the linear 3 x 3 part, including scale and shear. */
    val axisZ: VectorView
        get() = Geometry.vector(m02, m12, m22)

    /** Linear column lengths; these do not uniquely determine signed scale. */
    val axisLengths: VectorView
        get() = Geometry.vector(axisX.length, axisY.length, axisZ.length)

    val translation: VectorView
        get() = Geometry.vector(m03, m13, m23)

    /** Linear interpolation of coefficients; does not preserve rigid rotations. Alpha is not clamped. */
    override fun interpolate(target: MatrixView, alpha: Double): MatrixView = combineComponents(target) { start, end ->
        start + (end - start) * alpha
    }

    /** Visits components in row-major order without taking a snapshot. */
    override fun iterator(): DoubleIterator = object : DoubleIterator() {
        private var index = 0

        override fun hasNext(): Boolean = index < 16

        override fun nextDouble(): Double {
            if (!hasNext()) throw NoSuchElementException()
            val current = index++
            return this@MatrixView[current / 4, current % 4]
        }
    }
}

operator fun MatrixView.plus(other: MatrixView): MatrixView = combineComponents(other) { left, right -> left + right }
operator fun MatrixView.minus(other: MatrixView): MatrixView = combineComponents(other) { left, right -> left - right }
operator fun MatrixView.unaryMinus(): MatrixView = mapComponents { -it }
operator fun MatrixView.times(scale: Double): MatrixView = mapComponents { it * scale }
operator fun MatrixView.div(scale: Double): MatrixView = mapComponents { it / scale }
operator fun Double.times(matrix: MatrixView): MatrixView = matrix * this

/** Composes transformations, applying [other] first. */
operator fun MatrixView.times(other: MatrixView): MatrixView = Matrices.generate { row, column ->
    (0..3).sumOf { index -> this[row, index] * other[index, column] }
}

fun MatrixView.transposed(): MatrixView = Matrices.generate { row, column -> this[column, row] }

fun MatrixView.withComponent(row: Int, column: Int, value: Double): MatrixView {
    require(row in 0..3 && column in 0..3) { "Matrix coordinates must be between zero and three" }
    return Matrices.generate { currentRow, currentColumn ->
        if (currentRow == row && currentColumn == column) value else this[currentRow, currentColumn]
    }
}

/** Returns an independent row-major copy. */
fun MatrixView.toRowMajorArray(): DoubleArray = doubleArrayOf(
    m00, m01, m02, m03,
    m10, m11, m12, m13,
    m20, m21, m22, m23,
    m30, m31, m32, m33
)

/** Returns an independent column-major copy, suitable for conversion to graphics API storage. */
fun MatrixView.toColumnMajorArray(): DoubleArray = doubleArrayOf(
    m00, m10, m20, m30,
    m01, m11, m21, m31,
    m02, m12, m22, m32,
    m03, m13, m23, m33
)

/**
 * Inverts a general matrix with partial pivoting. Returns null for non-finite inputs,
 * pivots of magnitude at most [tolerance], or a non-finite result. Tolerance is an
 * absolute pivot threshold, not a determinant threshold; zero rejects only zero pivots.
 * Components are sampled once before elimination. This is not a condition-number test.
 * Tolerance must be finite and nonnegative.
 */
fun MatrixView.inverseOrNull(tolerance: Double = 0.0): MatrixView? {
    require(tolerance.isFinite() && tolerance >= 0.0)

    val augmented = AugmentedMatrix(this)
    if (!augmented.isFinite()) return null

    for (column in 0..3) {
        val pivotRow = augmented.pivotRowOrNull(column, tolerance) ?: return null

        augmented.swapRows(column, pivotRow)
        augmented.normalizePivotRow(column)
        augmented.eliminateOtherRows(column)
    }

    return augmented.rightHalfOrNull()
}

/** Throws IllegalArgumentException if [inverseOrNull] cannot produce a finite inverse. */
fun MatrixView.inverse(tolerance: Double = 0.0): MatrixView =
    requireNotNull(inverseOrNull(tolerance)) { "Matrix has no finite inverse at the requested pivot tolerance" }

fun MatrixView.safeInverse(tolerance: Double = 0.0, resultIfSingular: MatrixView = Matrices.Identity): MatrixView =
    inverseOrNull(tolerance) ?: resultIfSingular

/**
 * Transforms (x, y, z, 1), returning XYZ without perspective division.
 * For projective matrices, use [projectPosition] to divide by the resulting W.
 */
fun MatrixView.transformPosition(position: VectorView): VectorView = Geometry.vector(
    m00 * position.x + m01 * position.y + m02 * position.z + m03,
    m10 * position.x + m11 * position.y + m12 * position.z + m13,
    m20 * position.x + m21 * position.y + m22 * position.z + m23
)

/** Applies the linear 3 x 3 part, ignoring translation and the homogeneous output W. */
fun MatrixView.transformVector(vector: VectorView): VectorView = Geometry.vector(
    m00 * vector.x + m01 * vector.y + m02 * vector.z,
    m10 * vector.x + m11 * vector.y + m12 * vector.z,
    m20 * vector.x + m21 * vector.y + m22 * vector.z
)

/**
 * Reverses an invertible affine position transform. Rejects projective matrices with IllegalArgumentException;
 * use `inverse().projectPosition(position)` for inverse projection. Validation and inversion use the same snapshot.
 */
fun MatrixView.inverseTransformPosition(position: VectorView): VectorView {
    val snapshot = Matrices.copyOf(this)
    require(snapshot.isAffine()) { "Inverse position transformation requires an affine matrix" }
    return snapshot.inverse().transformPosition(position)
}

/**
 * Reverses an invertible affine vector transform, including scale and shear.
 * Rejects projective matrices with IllegalArgumentException. Validation and inversion use the same snapshot.
 */
fun MatrixView.inverseTransformVector(vector: VectorView): VectorView {
    val snapshot = Matrices.copyOf(this)
    require(snapshot.isAffine()) { "Inverse vector transformation requires an affine matrix" }
    return snapshot.inverse().transformVector(vector)
}

/**
 * Applies the inverse-transpose linear transform to a surface normal, preserving
 * perpendicularity under nonuniform scale and shear. Requires an invertible affine
 * matrix. The result is not normalized. Validation and inversion use the same snapshot.
 */
fun MatrixView.transformNormal(normal: VectorView): VectorView {
    val snapshot = Matrices.copyOf(this)
    require(snapshot.isAffine()) { "Surface normal transformation requires an affine matrix" }
    return snapshot.inverse().transposed().transformVector(normal)
}

/** Perspective-divides the transformed position. Zero W produces IEEE infinity or NaN. */
fun MatrixView.projectPosition(position: VectorView): VectorView {
    val x = position.x
    val y = position.y
    val z = position.z
    val homogeneousW = m30 * x + m31 * y + m32 * z + m33
    return Geometry.vector(
        (m00 * x + m01 * y + m02 * z + m03) / homogeneousW,
        (m10 * x + m11 * y + m12 * z + m13) / homogeneousW,
        (m20 * x + m21 * y + m22 * z + m23) / homogeneousW
    )
}

/** Returns null if projection cannot produce finite coordinates; does not test visibility. */
fun MatrixView.projectPositionOrNull(position: VectorView): VectorView? =
    projectPosition(position).takeIf { it.isFinite() }

/** Local axis of the linear 3 x 3 part, including scale and shear. Index is 0 = X, 1 = Y, 2 = Z. */
fun MatrixView.axis(index: Int): VectorView = when (index) {
    0 -> axisX
    1 -> axisY
    2 -> axisZ
    else -> throw IllegalArgumentException("Axis index must be between zero and two")
}

fun MatrixView.unitAxis(index: Int): VectorView = axis(index).normalized()

/**
 * Normalizes the linear columns; does not remove shear or reflections.
 * Columns with zero squared length or squared length below [tolerance] become zero.
 * Tolerance must be finite and nonnegative.
 */
fun MatrixView.withNormalizedAxes(tolerance: Double = 1.0E-8): MatrixView {
    require(tolerance.isFinite() && tolerance >= 0.0)

    val normalizedX = normalizedAxis(axisX, tolerance)
    val normalizedY = normalizedAxis(axisY, tolerance)
    val normalizedZ = normalizedAxis(axisZ, tolerance)

    return Matrices.of(
        normalizedX.x, normalizedY.x, normalizedZ.x, m03,
        normalizedX.y, normalizedY.y, normalizedZ.y, m13,
        normalizedX.z, normalizedY.z, normalizedZ.z, m23,
        m30, m31, m32, m33
    )
}

fun MatrixView.withTranslation(translation: VectorView): MatrixView = Matrices.of(
    m00, m01, m02, translation.x,
    m10, m11, m12, translation.y,
    m20, m21, m22, translation.z,
    m30, m31, m32, m33
)

fun MatrixView.withoutTranslation(): MatrixView = withTranslation(Vectors.Zero)

/** Adds a world-space translation after this transformation, including for projective matrices. */
fun MatrixView.translatedWorld(translation: VectorView): MatrixView = Matrices.fromTranslation(translation) * this

/** Applies local scale before this transformation. */
fun MatrixView.scaledLocal(scale: VectorView): MatrixView = this * Matrices.fromScale(scale)

/** Returns the linear columns, retaining scale and shear. */
fun MatrixView.toBasis(): BasisView = Basis(axisX, axisY, axisZ)

/** Requires a right-handed orthonormal linear part. Translation is ignored. */
fun MatrixView.toQuaternion(): QuaternionView = Quaternions.fromBasis(toBasis())

/** Rejects a projective last row instead of discarding it. */
fun MatrixView.toAffine(): AffineTransformView {
    val snapshot = Matrices.copyOf(this)
    require(snapshot.isAffine()) { "A projective matrix cannot be represented as an affine transform" }

    return Geometry.affineTransform(
        axisX = snapshot.axisX,
        axisY = snapshot.axisY,
        axisZ = snapshot.axisZ,
        translation = snapshot.translation
    )
}

fun AffineTransformView.toMatrix4(): MatrixView = Matrices.fromAffine(this)
fun TransformView.toMatrix4(): MatrixView = toAffine().toMatrix4()
fun QuaternionView.toMatrix4(): MatrixView = Matrices.fromRotation(this)

object Matrices {
    val Zero: MatrixView = generate { _, _ -> 0.0 }
    val Identity: MatrixView = generate { row, column -> if (row == column) 1.0 else 0.0 }

    /** Evaluates each coordinate once in row-major order and stores the resulting values. */
    fun generate(element: (row: Int, column: Int) -> Double): MatrixView = of(
        element(0, 0), element(0, 1), element(0, 2), element(0, 3),
        element(1, 0), element(1, 1), element(1, 2), element(1, 3),
        element(2, 0), element(2, 1), element(2, 2), element(2, 3),
        element(3, 0), element(3, 1), element(3, 2), element(3, 3)
    )

    fun of(
        m00: Double, m01: Double, m02: Double, m03: Double,
        m10: Double, m11: Double, m12: Double, m13: Double,
        m20: Double, m21: Double, m22: Double, m23: Double,
        m30: Double, m31: Double, m32: Double, m33: Double,
    ): MatrixView = Geometry.matrix(
        m00, m01, m02, m03,
        m10, m11, m12, m13,
        m20, m21, m22, m23,
        m30, m31, m32, m33
    )

    /**
     * Takes an independent snapshot, reading each source component once in row-major order.
     * Sampling is sequential, not atomic with respect to changes to the source view.
     */
    fun copyOf(matrix: MatrixView): MatrixView = of(
        matrix.m00, matrix.m01, matrix.m02, matrix.m03,
        matrix.m10, matrix.m11, matrix.m12, matrix.m13,
        matrix.m20, matrix.m21, matrix.m22, matrix.m23,
        matrix.m30, matrix.m31, matrix.m32, matrix.m33
    )

    /** Copies sixteen values in row-major order. */
    fun fromRowMajor(values: DoubleArray): MatrixView {
        require(values.size == 16) { "A 4 x 4 matrix requires sixteen values" }
        return generate { row, column -> values[row * 4 + column] }
    }

    /** Copies sixteen values in column-major order. */
    fun fromColumnMajor(values: DoubleArray): MatrixView {
        require(values.size == 16) { "A 4 x 4 matrix requires sixteen values" }
        return generate { row, column -> values[column * 4 + row] }
    }

    fun fromTranslation(translation: VectorView): MatrixView = Identity.withTranslation(translation)

    fun fromScale(scale: VectorView): MatrixView = of(
        scale.x, 0.0, 0.0, 0.0,
        0.0, scale.y, 0.0, 0.0,
        0.0, 0.0, scale.z, 0.0,
        0.0, 0.0, 0.0, 1.0
    )

    /** Rotation must be a unit quaternion. */
    fun fromRotation(rotation: QuaternionView): MatrixView = fromBasis(rotation.toBasis())

    /** Basis vectors become the linear columns; scale and shear are retained. */
    fun fromBasis(basis: BasisView, translation: VectorView = Vectors.Zero): MatrixView = of(
        basis.right.x, basis.up.x, basis.forward.x, translation.x,
        basis.right.y, basis.up.y, basis.forward.y, translation.y,
        basis.right.z, basis.up.z, basis.forward.z, translation.z,
        0.0, 0.0, 0.0, 1.0
    )

    fun fromAffine(transform: AffineTransformView): MatrixView = of(
        transform.axisX.x, transform.axisY.x, transform.axisZ.x, transform.translation.x,
        transform.axisX.y, transform.axisY.y, transform.axisZ.y, transform.translation.y,
        transform.axisX.z, transform.axisY.z, transform.axisZ.z, transform.translation.z,
        0.0, 0.0, 0.0, 1.0
    )

    fun fromTransform(transform: TransformView): MatrixView = transform.toMatrix4()

    /** Builds a matrix applying scale, then unit-quaternion rotation, then translation. */
    fun fromTRS(translation: VectorView, rotation: QuaternionView, scale: VectorView): MatrixView =
        fromRotation(rotation).scaledLocal(scale).withTranslation(translation)

    fun lerp(a: MatrixView, b: MatrixView, t: Double): MatrixView = a.interpolate(b, t)
}

private fun normalizedAxis(axis: VectorView, tolerance: Double): VectorView {
    if (axis.lengthSquared == 0.0) return Vectors.Zero
    return axis.normalizedOr(Vectors.Zero, tolerance)
}

private fun MatrixView.mapComponents(transform: (Double) -> Double): MatrixView =
    Matrices.generate { row, column -> transform(this[row, column]) }

private fun MatrixView.combineComponents(other: MatrixView, transform: (Double, Double) -> Double): MatrixView =
    Matrices.generate { row, column -> transform(this[row, column], other[row, column]) }

private fun MatrixView.computeLinearDeterminant(): Double =
    m00 * (m11 * m22 - m12 * m21) -
            m01 * (m10 * m22 - m12 * m20) +
            m02 * (m10 * m21 - m11 * m20)

private fun MatrixView.cofactor(row: Int, column: Int): Double {
    val minorRows = (0..3).filter { it != row }
    val minorColumns = (0..3).filter { it != column }

    fun element(minorRow: Int, minorColumn: Int): Double = this[minorRows[minorRow], minorColumns[minorColumn]]

    val determinant =
        element(0, 0) * (element(1, 1) * element(2, 2) - element(1, 2) * element(2, 1)) -
                element(0, 1) * (element(1, 0) * element(2, 2) - element(1, 2) * element(2, 0)) +
                element(0, 2) * (element(1, 0) * element(2, 1) - element(1, 1) * element(2, 0))

    return if ((row + column) % 2 == 0) determinant else -determinant
}

/**
 * Owns the working copy `[matrix | identity]`. Row operations reduce the left half
 * to identity and leave the inverse in the right half, without rereading the source view.
 */
private class AugmentedMatrix(matrix: MatrixView) {
    private val rows = Array(4) { row -> createRow(matrix, row) }

    fun isFinite(): Boolean = rows.all { row -> row.all { it.isFinite() } }

    fun pivotRowOrNull(column: Int, tolerance: Double): Int? {
        val pivotRow = (column..3).maxBy { row -> abs(rows[row][column]) }
        val pivot = rows[pivotRow][column]
        if (!pivot.isFinite() || abs(pivot) <= tolerance) return null

        return pivotRow
    }

    fun swapRows(first: Int, second: Int) {
        val temporary = rows[first]
        rows[first] = rows[second]
        rows[second] = temporary
    }

    fun normalizePivotRow(index: Int) {
        val row = rows[index]
        val pivot = row[index]

        for (column in row.indices) {
            row[column] /= pivot
        }
    }

    fun eliminateOtherRows(column: Int) {
        for (row in rows.indices) {
            if (row == column) continue
            eliminateRow(row, column)
        }
    }

    fun rightHalfOrNull(): MatrixView? {
        if (!isFinite()) return null

        return Matrices.generate { row, column -> rows[row][column + 4] }
    }

    private fun eliminateRow(rowIndex: Int, pivotIndex: Int) {
        val row = rows[rowIndex]
        val pivotRow = rows[pivotIndex]
        val factor = row[pivotIndex]

        for (column in row.indices) {
            row[column] -= factor * pivotRow[column]
        }
    }

    private fun createRow(matrix: MatrixView, row: Int): DoubleArray {
        val values = DoubleArray(8)
        for (column in 0..3) {
            values[column] = matrix[row, column]
        }
        values[row + 4] = 1.0
        return values
    }
}