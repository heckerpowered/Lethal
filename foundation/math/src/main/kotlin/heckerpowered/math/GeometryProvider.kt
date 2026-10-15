/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import java.util.*

/**
 * Selects the host or freestanding representation used by the ordinary geometry constructors.
 *
 * Scalar factories must return stable values for the supplied components. Composite factories
 * retain their supplied child references; with stable children the resulting value must also be
 * stable. They do not implicitly snapshot live children or normalize supplied coefficients.
 */
interface GeometryProvider {
    companion object {
        val Freestanding: GeometryProvider
            get() = FreestandingGeometryProvider
        val Hosting: GeometryProvider?
            get() = ServiceLoader
                .load(GeometryProvider::class.java, GeometryProvider::class.java.classLoader)
                .firstOrNull()
        val Auto
            get() = Hosting ?: Freestanding
    }

    fun vector(x: Double, y: Double, z: Double): VectorView = Freestanding.vector(x, y, z)
    fun vector4(x: Double, y: Double, z: Double, w: Double): Vector4View = Freestanding.vector4(x, y, z, w)
    fun box(min: VectorView, max: VectorView): BoxView = box(min.x, min.y, min.z, max.x, max.y, max.z)
    fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): BoxView = Freestanding.box(minX, minY, minZ, maxX, maxY, maxZ)
    fun rotator(pitch: Double, yaw: Double, roll: Double = 0.0): RotatorView = Freestanding.rotator(pitch, yaw, roll)
    fun ray(origin: VectorView, direction: VectorView): RayView = Freestanding.ray(origin, direction)
    fun quaternion(x: Double, y: Double, z: Double, w: Double): QuaternionView = Freestanding.quaternion(x, y, z, w)
    fun plane(x: Double, y: Double, z: Double, w: Double): PlaneView = Freestanding.plane(x, y, z, w)
    fun matrix(m00: Double, m01: Double, m02: Double, m03: Double, m10: Double, m11: Double, m12: Double, m13: Double, m20: Double, m21: Double, m22: Double, m23: Double, m30: Double, m31: Double, m32: Double, m33: Double): MatrixView = Freestanding.matrix(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33)
    fun transform(translation: VectorView, rotation: QuaternionView, scale: VectorView): TransformView = Freestanding.transform(translation, rotation, scale)
    fun affineTransform(axisX: VectorView, axisY: VectorView, axisZ: VectorView, translation: VectorView): AffineTransformView = Freestanding.affineTransform(axisX, axisY, axisZ, translation)
    fun basis(right: VectorView, up: VectorView, forward: VectorView): BasisView = Freestanding.basis(right, up, forward)
}

object FreestandingGeometryProvider : GeometryProvider {
    override fun vector(x: Double, y: Double, z: Double): VectorView = FreestandingVector(x, y, z)
    override fun vector4(x: Double, y: Double, z: Double, w: Double): Vector4View = FreestandingVector4(x, y, z, w)
    override fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): BoxView = FreestandingBox(minX, minY, minZ, maxX, maxY, maxZ)
    override fun rotator(pitch: Double, yaw: Double, roll: Double): RotatorView = FreestandingRotator(pitch, yaw, roll)
    override fun ray(origin: VectorView, direction: VectorView): RayView = FreestandingRay(origin, direction)
    override fun quaternion(x: Double, y: Double, z: Double, w: Double): QuaternionView = FreestandingQuaternion(x, y, z, w)
    override fun plane(x: Double, y: Double, z: Double, w: Double): PlaneView = FreestandingPlane(x, y, z, w)
    override fun matrix(m00: Double, m01: Double, m02: Double, m03: Double, m10: Double, m11: Double, m12: Double, m13: Double, m20: Double, m21: Double, m22: Double, m23: Double, m30: Double, m31: Double, m32: Double, m33: Double): MatrixView = FreestandingMatrix(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33)
    override fun transform(translation: VectorView, rotation: QuaternionView, scale: VectorView): TransformView = FreestandingTransform(translation, rotation, scale)
    override fun affineTransform(axisX: VectorView, axisY: VectorView, axisZ: VectorView, translation: VectorView): AffineTransformView = FreestandingAffineTransform(axisX, axisY, axisZ, translation)
    override fun basis(right: VectorView, up: VectorView, forward: VectorView): BasisView = FreestandingBasis(right, up, forward)
}

object Geometry {
    val Provider = GeometryProvider.Auto

    @JvmStatic
    fun vector(x: Double, y: Double, z: Double) = Provider.vector(x, y, z)
    fun vector4(x: Double, y: Double, z: Double, w: Double): Vector4View = Provider.vector4(x, y, z, w)
    fun box(min: VectorView, max: VectorView) = Provider.box(min, max)
    fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) = Provider.box(minX, minY, minZ, maxX, maxY, maxZ)
    fun rotator(pitch: Double, yaw: Double, roll: Double = 0.0) = Provider.rotator(pitch, yaw, roll)
    fun ray(origin: VectorView, direction: VectorView) = Provider.ray(origin, direction)
    fun quaternion(x: Double, y: Double, z: Double, w: Double) = Provider.quaternion(x, y, z, w)
    fun plane(x: Double, y: Double, z: Double, w: Double) = Provider.plane(x, y, z, w)
    fun matrix(m00: Double, m01: Double, m02: Double, m03: Double, m10: Double, m11: Double, m12: Double, m13: Double, m20: Double, m21: Double, m22: Double, m23: Double, m30: Double, m31: Double, m32: Double, m33: Double): MatrixView = Provider.matrix(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33)
    fun transform(translation: VectorView, rotation: QuaternionView, scale: VectorView) = Provider.transform(translation, rotation, scale)
    fun affineTransform(axisX: VectorView, axisY: VectorView, axisZ: VectorView, translation: VectorView) = Provider.affineTransform(axisX, axisY, axisZ, translation)
    fun basis(right: VectorView, up: VectorView, forward: VectorView): BasisView = Provider.basis(right, up, forward)
}

object Vector {
    operator fun invoke(x: Double, y: Double, z: Double): VectorView = Geometry.vector(x, y, z)
    operator fun invoke(value: Double): VectorView = Geometry.vector(value, value, value)
    operator fun invoke(): VectorView = Geometry.vector(0.0, 0.0, 0.0)
    operator fun invoke(vector: VectorView): VectorView = Geometry.vector(vector.x, vector.y, vector.z)
}

object Vector4 {
    operator fun invoke(x: Double, y: Double, z: Double, w: Double): Vector4View = Geometry.vector4(x, y, z, w)
    operator fun invoke(value: Double): Vector4View = Geometry.vector4(value, value, value, value)
    operator fun invoke(): Vector4View = Geometry.vector4(0.0, 0.0, 0.0, 0.0)
    operator fun invoke(vector: Vector4View): Vector4View = Geometry.vector4(vector.x, vector.y, vector.z, vector.w)
}

object Box {
    operator fun invoke(min: VectorView, max: VectorView): BoxView = Geometry.box(min, max)
    operator fun invoke(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): BoxView = Geometry.box(minX, minY, minZ, maxX, maxY, maxZ)
    operator fun invoke(): BoxView = Geometry.box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    operator fun invoke(point: VectorView): BoxView = Geometry.box(point, point)
    operator fun invoke(x: Double, y: Double, z: Double): BoxView = Geometry.box(x, y, z, x, y, z)
    operator fun invoke(box: BoxView): BoxView = Geometry.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
}

object Rotator {
    operator fun invoke(pitch: Double, yaw: Double, roll: Double = 0.0): RotatorView = Geometry.rotator(pitch, yaw, roll)
    operator fun invoke(): RotatorView = Geometry.rotator(0.0, 0.0, 0.0)
    operator fun invoke(rotator: RotatorView): RotatorView = Geometry.rotator(rotator.pitch, rotator.yaw, rotator.roll)
}

object Ray {
    operator fun invoke(origin: VectorView, direction: VectorView): RayView = Geometry.ray(origin, direction)
    operator fun invoke(ray: RayView): RayView = Geometry.ray(Vector(ray.origin), Vector(ray.direction))
}

object Quaternion {
    operator fun invoke(x: Double, y: Double, z: Double, w: Double): QuaternionView = Geometry.quaternion(x, y, z, w)
    operator fun invoke(): QuaternionView = Geometry.quaternion(0.0, 0.0, 0.0, 1.0)
    operator fun invoke(quaternion: QuaternionView): QuaternionView = Geometry.quaternion(quaternion.x, quaternion.y, quaternion.z, quaternion.w)
}

object Plane {
    operator fun invoke(x: Double, y: Double, z: Double, w: Double): PlaneView = Geometry.plane(x, y, z, w)
    operator fun invoke(plane: PlaneView): PlaneView = Planes.copyOf(plane)
    operator fun invoke(normal: VectorView, w: Double): PlaneView = Planes.fromNormal(normal, w)
}

object Matrix {
    operator fun invoke(
        m00: Double, m01: Double, m02: Double, m03: Double,
        m10: Double, m11: Double, m12: Double, m13: Double,
        m20: Double, m21: Double, m22: Double, m23: Double,
        m30: Double, m31: Double, m32: Double, m33: Double,
    ): MatrixView = Geometry.matrix(
        m00, m01, m02, m03,
        m10, m11, m12, m13,
        m20, m21, m22, m23,
        m30, m31, m32, m33,
    )

    operator fun invoke(): MatrixView = Geometry.matrix(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0,
    )

    operator fun invoke(matrix: MatrixView): MatrixView = Matrices.copyOf(matrix)
}

object Transform {
    operator fun invoke(translation: VectorView, rotation: QuaternionView, scale: VectorView): TransformView = Geometry.transform(translation, rotation, scale)
    operator fun invoke(): TransformView = Geometry.transform(Vector(), Quaternion(), Vector(1.0))
    operator fun invoke(transform: TransformView): TransformView = Geometry.transform(Vector(transform.translation), Quaternion(transform.rotation), Vector(transform.scale))
}

object AffineTransform {
    operator fun invoke(axisX: VectorView, axisY: VectorView, axisZ: VectorView, translation: VectorView): AffineTransformView = Geometry.affineTransform(axisX, axisY, axisZ, translation)
    operator fun invoke(): AffineTransformView = Geometry.affineTransform(
        Vector(1.0, 0.0, 0.0),
        Vector(0.0, 1.0, 0.0),
        Vector(0.0, 0.0, 1.0),
        Vector(),
    )
    operator fun invoke(transform: AffineTransformView): AffineTransformView = AffineTransforms.copyOf(transform)
}

object Basis {
    operator fun invoke(right: VectorView, up: VectorView, forward: VectorView): BasisView = Geometry.basis(right, up, forward)
    operator fun invoke(): BasisView = Geometry.basis(
        Vector(1.0, 0.0, 0.0),
        Vector(0.0, 1.0, 0.0),
        Vector(0.0, 0.0, 1.0),
    )
    operator fun invoke(basis: BasisView): BasisView = Geometry.basis(Vector(basis.right), Vector(basis.up), Vector(basis.forward))
}

private data class FreestandingVector(
    override val x: Double,
    override val y: Double,
    override val z: Double,
) : VectorView {
    override fun snapshot(): VectorView = this
}

private data class FreestandingVector4(
    override val x: Double,
    override val y: Double,
    override val z: Double,
    override val w: Double,
) : Vector4View {
    override fun snapshot(): Vector4View = this
}

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

    override fun snapshot(): BoxView = this
}

private data class FreestandingRotator(
    override val pitch: Double,
    override val yaw: Double,
    override val roll: Double = 0.0,
) : RotatorView {
    override fun snapshot(): RotatorView = this
}

private data class FreestandingRay(
    override val origin: VectorView,
    override val direction: VectorView,
) : RayView {
    override fun snapshot(): RayView {
        val sourceOrigin = this.origin
        val sourceDirection = this.direction
        val origin = sourceOrigin.snapshot()
        val direction = sourceDirection.snapshot()
        if (origin === sourceOrigin && direction === sourceDirection) return this
        return Ray(origin, direction)
    }
}

private data class FreestandingQuaternion(
    override val x: Double,
    override val y: Double,
    override val z: Double,
    override val w: Double,
) : QuaternionView {
    override fun snapshot(): QuaternionView = this
}

private data class FreestandingPlane(
    override val x: Double,
    override val y: Double,
    override val z: Double,
    override val w: Double,
) : PlaneView {
    override fun snapshot(): PlaneView = this
}

private data class FreestandingTransform(
    override val translation: VectorView,
    override val rotation: QuaternionView,
    override val scale: VectorView,
) : TransformView {
    override fun snapshot(): TransformView {
        val sourceTranslation = this.translation
        val sourceRotation = this.rotation
        val sourceScale = this.scale
        val translation = sourceTranslation.snapshot()
        val rotation = sourceRotation.snapshot()
        val scale = sourceScale.snapshot()
        if (translation === sourceTranslation && rotation === sourceRotation && scale === sourceScale) return this
        return Transform(translation, rotation, scale)
    }
}

private data class FreestandingAffineTransform(
    override val axisX: VectorView,
    override val axisY: VectorView,
    override val axisZ: VectorView,
    override val translation: VectorView,
) : AffineTransformView {
    override fun snapshot(): AffineTransformView {
        val sourceAxisX = this.axisX
        val sourceAxisY = this.axisY
        val sourceAxisZ = this.axisZ
        val sourceTranslation = this.translation
        val axisX = sourceAxisX.snapshot()
        val axisY = sourceAxisY.snapshot()
        val axisZ = sourceAxisZ.snapshot()
        val translation = sourceTranslation.snapshot()
        if (axisX === sourceAxisX && axisY === sourceAxisY && axisZ === sourceAxisZ && translation === sourceTranslation) return this
        return AffineTransform(axisX, axisY, axisZ, translation)
    }
}

private data class FreestandingBasis(
    override val right: VectorView,
    override val up: VectorView,
    override val forward: VectorView,
) : BasisView {
    override fun snapshot(): BasisView {
        val sourceRight = this.right
        val sourceUp = this.up
        val sourceForward = this.forward
        val right = sourceRight.snapshot()
        val up = sourceUp.snapshot()
        val forward = sourceForward.snapshot()
        if (right === sourceRight && up === sourceUp && forward === sourceForward) return this
        return Basis(right, up, forward)
    }
}

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
) : MatrixView {
    override fun snapshot(): MatrixView = this
}
