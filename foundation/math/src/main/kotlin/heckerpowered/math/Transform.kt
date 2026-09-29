/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

interface TransformView : Interpolatable<TransformView> {
    val translation: VectorView
    val rotation: QuaternionView
    val scale: VectorView

    fun isIdentity(epsilon: Double = 1.0E-6): Boolean =
        translation.isNearlyZero(epsilon) &&
                rotation.isIdentity(epsilon) &&
                (scale - Vectors.One).isNearlyZero(epsilon)

    fun isFinite(): Boolean =
        translation.isFinite() &&
                rotation.isFinite() &&
                scale.isFinite()

    /**
     * Transforms a position by scale, rotation, then translation.
     */
    fun transformPosition(position: VectorView): VectorView =
        rotation.rotateVector(position * scale) + translation

    /**
     * Transforms a vector by scale and rotation, without translation.
     */
    fun transformVector(vector: VectorView): VectorView =
        rotation.rotateVector(vector * scale)

    /**
     * Transforms a vector by rotation only.
     */
    fun transformVectorNoScale(vector: VectorView): VectorView =
        rotation.rotateVector(vector)

    override fun interpolate(target: TransformView, alpha: Double): TransformView =
        Geometry.transform(
            translation = translation.interpolate(target.translation, alpha),
            rotation = rotation.interpolate(target.rotation, alpha),
            scale = scale.interpolate(target.scale, alpha),
        )
}

/**
 * Composes this transform with [other] exactly.
 *
 * `(first.compose(second))` applies [other] first, then this transform.
 *
 * The result is affine because composing TRS transforms may introduce shear.
 */
fun TransformView.compose(other: TransformView): AffineTransformView =
    toAffine() * other.toAffine()

/**
 * Attempts to compose this transform with [other] while preserving an exact
 * TRS representation.
 *
 * `(first.tryCompose(second))` applies [other] first, then this transform.
 *
 * Returns `null` when the composition may introduce shear that cannot be
 * represented exactly by [TransformView].
 *
 * This check is intentionally conservative: some compositions that are
 * mathematically representable as TRS may still return `null`.
 */
fun TransformView.tryCompose(other: TransformView): TransformView? {
    val scaleCommutesWithOtherRotation = scale.allComponentsEqual(0.0) || other.rotation.isIdentity(0.0)
    if (!scaleCommutesWithOtherRotation) return null

    return Geometry.transform(
        translation = transformPosition(other.translation),
        rotation = rotation * other.rotation,
        scale = scale * other.scale,
    )
}

/**
 * Composes two TRS transforms exactly.
 *
 * `(first * second)` applies second, then first.
 *
 * The result is affine because arbitrary TRS composition may introduce shear.
 */
operator fun TransformView.times(other: TransformView): AffineTransformView =
    compose(other)

object Transforms {
    val Identity: TransformView = Geometry.transform(Vectors.Zero, Quaternions.Identity, Vectors.One)

    fun of(translation: VectorView = Vectors.Zero, rotation: QuaternionView = Quaternions.Identity, scale: VectorView = Vectors.One): TransformView =
        Geometry.transform(translation, rotation, scale)

    fun fromTranslation(translation: VectorView): TransformView =
        of(translation = translation)

    fun fromRotation(rotation: QuaternionView): TransformView =
        of(rotation = rotation)

    fun fromScale(scale: VectorView): TransformView =
        of(scale = scale)
}