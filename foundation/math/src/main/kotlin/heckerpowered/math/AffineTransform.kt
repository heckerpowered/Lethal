/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

/**
 * A general three-dimensional affine transformation.
 *
 * The linear part is represented by [axisX], [axisY], and [axisZ], which are
 * the transformed local +X, +Y, and +Z basis vectors respectively.
 *
 * In matrix form:
 *
 * ```
 * | axisX.x  axisY.x  axisZ.x  translation.x |
 * | axisX.y  axisY.y  axisZ.y  translation.y |
 * | axisX.z  axisY.z  axisZ.z  translation.z |
 * |    0        0        0            1      |
 * ```
 *
 * Unlike [TransformView], an affine transform can represent shear and remains
 * closed under composition.
 */
interface AffineTransformView : Interpolatable<AffineTransformView> {
    val axisX: VectorView
    val axisY: VectorView
    val axisZ: VectorView
    val translation: VectorView

    fun isFinite(): Boolean =
        axisX.isFinite() &&
                axisY.isFinite() &&
                axisZ.isFinite() &&
                translation.isFinite()

    fun isIdentity(epsilon: Double = 1.0E-6): Boolean =
        (axisX - Vectors.UnitX).isNearlyZero(epsilon) &&
                (axisY - Vectors.UnitY).isNearlyZero(epsilon) &&
                (axisZ - Vectors.UnitZ).isNearlyZero(epsilon) &&
                translation.isNearlyZero(epsilon)

    /**
     * Applies the complete affine transformation to a position.
     */
    fun transformPosition(position: VectorView): VectorView =
        transformVector(position) + translation

    /**
     * Applies only the linear part of this affine transformation to a vector.
     *
     * Translation is ignored.
     */
    fun transformVector(vector: VectorView): VectorView =
        axisX * vector.x +
                axisY * vector.y +
                axisZ * vector.z

    /**
     * Linearly interpolates every coefficient of the affine transformation.
     *
     * The result remains affine, but does not generally preserve properties such
     * as orthogonality, uniform scale, or rigidity between the endpoints.
     */
    override fun interpolate(target: AffineTransformView, alpha: Double): AffineTransformView =
        Geometry.affineTransform(
            axisX = axisX.interpolate(target.axisX, alpha),
            axisY = axisY.interpolate(target.axisY, alpha),
            axisZ = axisZ.interpolate(target.axisZ, alpha),
            translation = translation.interpolate(target.translation, alpha),
        )
}

/**
 * Composes two affine transformations.
 *
 * `(first * second)` applies [other] first, then this transform.
 */
operator fun AffineTransformView.times(other: AffineTransformView): AffineTransformView =
    Geometry.affineTransform(
        axisX = transformVector(other.axisX),
        axisY = transformVector(other.axisY),
        axisZ = transformVector(other.axisZ),
        translation = transformPosition(other.translation),
    )

/**
 * Converts this TRS transform into an equivalent affine transformation.
 *
 * The conversion is exact.
 */
fun TransformView.toAffine(): AffineTransformView =
    Geometry.affineTransform(
        axisX = rotation.axisX() * scale.x,
        axisY = rotation.axisY() * scale.y,
        axisZ = rotation.axisZ() * scale.z,
        translation = translation,
    )

object AffineTransforms {
    val Identity: AffineTransformView = Geometry.affineTransform(
        axisX = Vectors.UnitX,
        axisY = Vectors.UnitY,
        axisZ = Vectors.UnitZ,
        translation = Vectors.Zero,
    )

    fun of(axisX: VectorView = Vectors.UnitX, axisY: VectorView = Vectors.UnitY, axisZ: VectorView = Vectors.UnitZ, translation: VectorView = Vectors.Zero): AffineTransformView =
        Geometry.affineTransform(
            axisX = axisX,
            axisY = axisY,
            axisZ = axisZ,
            translation = translation,
        )

    fun fromTranslation(translation: VectorView): AffineTransformView =
        of(translation = translation)

    fun fromTransform(transform: TransformView): AffineTransformView =
        transform.toAffine()
}