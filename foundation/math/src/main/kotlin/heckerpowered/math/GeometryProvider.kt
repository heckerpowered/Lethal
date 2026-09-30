/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import java.util.*

interface GeometryProvider {
    companion object {
        val Freestanding: GeometryProvider = FreestandingGeometryProvider
        val Hosting: GeometryProvider?
            get() = ServiceLoader
                .load(GeometryProvider::class.java, GeometryProvider::class.java.classLoader)
                .firstOrNull()
        val Auto
            get() = Hosting ?: Freestanding
    }

    fun vector(x: Double, y: Double, z: Double): VectorView = Freestanding.vector(x, y, z)
    fun box(min: VectorView, max: VectorView): BoxView = box(min.x, min.y, min.z, max.x, max.y, max.z)
    fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): BoxView = Freestanding.box(minX, minY, minZ, maxX, maxY, maxZ)
    fun rotator(pitch: Double, yaw: Double, roll: Double = 0.0): RotatorView = Freestanding.rotator(pitch, yaw, roll)
    fun ray(origin: VectorView, direction: VectorView): RayView = Freestanding.ray(origin, direction)
    fun quaternion(x: Double, y: Double, z: Double, w: Double): QuaternionView = Freestanding.quaternion(x, y, z, w)
    fun plane(x: Double, y: Double, z: Double, w: Double): PlaneView = Freestanding.plane(x, y, z, w)
    fun matrix(m00: Double, m01: Double, m02: Double, m03: Double, m10: Double, m11: Double, m12: Double, m13: Double, m20: Double, m21: Double, m22: Double, m23: Double, m30: Double, m31: Double, m32: Double, m33: Double): MatrixView = Freestanding.matrix(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33)
    fun transform(translation: VectorView, rotation: QuaternionView, scale: VectorView): TransformView = Freestanding.transform(translation, rotation, scale)
    fun affineTransform(axisX: VectorView, axisY: VectorView, axisZ: VectorView, translation: VectorView): AffineTransformView = Freestanding.affineTransform(axisX, axisY, axisZ, translation)
}

object FreestandingGeometryProvider : GeometryProvider {
    override fun vector(x: Double, y: Double, z: Double): VectorView = FreestandingVector(x, y, z)
    override fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): BoxView = FreestandingBox(minX, minY, minZ, maxX, maxY, maxZ)
    override fun rotator(pitch: Double, yaw: Double, roll: Double): RotatorView = FreestandingRotator(pitch, yaw, roll)
    override fun ray(origin: VectorView, direction: VectorView): RayView = FreestandingRay(origin, direction)
    override fun quaternion(x: Double, y: Double, z: Double, w: Double): QuaternionView = FreestandingQuaternion(x, y, z, w)
    override fun plane(x: Double, y: Double, z: Double, w: Double): PlaneView = FreestandingPlane(x, y, z, w)
    override fun matrix(m00: Double, m01: Double, m02: Double, m03: Double, m10: Double, m11: Double, m12: Double, m13: Double, m20: Double, m21: Double, m22: Double, m23: Double, m30: Double, m31: Double, m32: Double, m33: Double): MatrixView = FreestandingMatrix(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33)
    override fun transform(translation: VectorView, rotation: QuaternionView, scale: VectorView): TransformView = FreestandingTransform(translation, rotation, scale)
    override fun affineTransform(axisX: VectorView, axisY: VectorView, axisZ: VectorView, translation: VectorView): AffineTransformView = FreestandingAffineTransform(axisX, axisY, axisZ, translation)
}

object Geometry {
    val Provider = GeometryProvider.Auto

    @JvmStatic
    fun vector(x: Double, y: Double, z: Double) = Provider.vector(x, y, z)
    fun box(min: VectorView, max: VectorView) = Provider.box(min, max)
    fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) = Provider.box(minX, minY, minZ, maxX, maxY, maxZ)
    fun rotator(pitch: Double, yaw: Double, roll: Double = 0.0) = Provider.rotator(pitch, yaw, roll)
    fun ray(origin: VectorView, direction: VectorView) = Provider.ray(origin, direction)
    fun quaternion(x: Double, y: Double, z: Double, w: Double) = Provider.quaternion(x, y, z, w)
    fun plane(x: Double, y: Double, z: Double, w: Double) = Provider.plane(x, y, z, w)
    fun matrix(m00: Double, m01: Double, m02: Double, m03: Double, m10: Double, m11: Double, m12: Double, m13: Double, m20: Double, m21: Double, m22: Double, m23: Double, m30: Double, m31: Double, m32: Double, m33: Double): MatrixView = Provider.matrix(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33)
    fun transform(translation: VectorView, rotation: QuaternionView, scale: VectorView) = Provider.transform(translation, rotation, scale)
    fun affineTransform(axisX: VectorView, axisY: VectorView, axisZ: VectorView, translation: VectorView) = Provider.affineTransform(axisX, axisY, axisZ, translation)
}

private data class FreestandingVector(
    override val x: Double,
    override val y: Double,
    override val z: Double,
) : VectorView

private data class FreestandingBox(
    override val minX: Double,
    override val minY: Double,
    override val minZ: Double,

    override val maxX: Double,
    override val maxY: Double,
    override val maxZ: Double,
) : BoxView {
    override val min: VectorView
        get() = FreestandingGeometryProvider.vector(minX, minY, minZ)

    override val max: VectorView
        get() = FreestandingGeometryProvider.vector(maxX, maxY, maxZ)
}

private data class FreestandingRotator(
    override val pitch: Double,
    override val yaw: Double,
    override val roll: Double = 0.0,
) : RotatorView

private data class FreestandingRay(
    override val origin: VectorView,
    override val direction: VectorView,
) : RayView

private data class FreestandingQuaternion(
    override val x: Double,
    override val y: Double,
    override val z: Double,
    override val w: Double,
) : QuaternionView

private data class FreestandingPlane(
    override val x: Double,
    override val y: Double,
    override val z: Double,
    override val w: Double,
) : PlaneView

private data class FreestandingTransform(
    override val translation: VectorView,
    override val rotation: QuaternionView,
    override val scale: VectorView,
) : TransformView

private data class FreestandingAffineTransform(
    override val axisX: VectorView,
    override val axisY: VectorView,
    override val axisZ: VectorView,
    override val translation: VectorView,
) : AffineTransformView

private data class FreestandingMatrix(
    override val m00: Double,
    override val m01: Double,
    override val m02: Double,
    override val m03: Double,
    override val m10: Double,
    override val m11: Double,
    override val m12: Double,
    override val m13: Double,
    override val m20: Double,
    override val m21: Double,
    override val m22: Double,
    override val m23: Double,
    override val m30: Double,
    override val m31: Double,
    override val m32: Double,
    override val m33: Double,
) : MatrixView
