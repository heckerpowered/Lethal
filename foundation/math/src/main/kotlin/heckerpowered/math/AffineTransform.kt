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
            axisX.interpolate(target.axisX, alpha),
            axisY.interpolate(target.axisY, alpha),
            axisZ.interpolate(target.axisZ, alpha),
            translation.interpolate(target.translation, alpha)
        )
}

/**
 * Composes two affine transformations.
 *
 * `(first * second)` applies [other] first, then this transform.
 */
operator fun AffineTransformView.times(other: AffineTransformView): AffineTransformView =
    Geometry.affineTransform(
        transformVector(other.axisX),
        transformVector(other.axisY),
        transformVector(other.axisZ),
        transformPosition(other.translation)
    )

/**
 * Converts this TRS transform into an equivalent affine transformation.
 *
 * The conversion is exact.
 */
fun TransformView.toAffine(): AffineTransformView =
    Geometry.affineTransform(
        rotation.axisX() * scale.x,
        rotation.axisY() * scale.y,
        rotation.axisZ() * scale.z,
        translation
    )

fun AffineTransforms.copyOf(transform: AffineTransformView): AffineTransformView =
    Geometry.affineTransform(
        Vectors.of(transform.axisX.x, transform.axisX.y, transform.axisX.z),
        Vectors.of(transform.axisY.x, transform.axisY.y, transform.axisY.z),
        Vectors.of(transform.axisZ.x, transform.axisZ.y, transform.axisZ.z),
        Vectors.of(transform.translation.x, transform.translation.y, transform.translation.z),
    )

object AffineTransforms {
    val Identity: AffineTransformView = Geometry.affineTransform(
        axisX = Vectors.UnitX,
        axisY = Vectors.UnitY,
        axisZ = Vectors.UnitZ,
        translation = Vectors.Zero,
    )

    fun of(axisX: VectorView = Vectors.UnitX, axisY: VectorView = Vectors.UnitY, axisZ: VectorView = Vectors.UnitZ, translation: VectorView = Vectors.Zero): AffineTransformView =
        Geometry.affineTransform(axisX, axisY, axisZ, translation)

    fun fromTranslation(translation: VectorView): AffineTransformView =
        of(translation = translation)

    fun fromTransform(transform: TransformView): AffineTransformView =
        transform.toAffine()
}