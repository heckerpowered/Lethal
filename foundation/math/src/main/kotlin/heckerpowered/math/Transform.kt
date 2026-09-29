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