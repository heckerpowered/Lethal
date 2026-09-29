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
}

object FreestandingGeometryProvider : GeometryProvider {
    override fun vector(x: Double, y: Double, z: Double): VectorView = FreestandingVector(x, y, z)
    override fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): BoxView = FreestandingBox(minX, minY, minZ, maxX, maxY, maxZ)
    override fun rotator(pitch: Double, yaw: Double, roll: Double): RotatorView = FreestandingRotator(pitch, yaw, roll)
    override fun ray(origin: VectorView, direction: VectorView): RayView = FreestandingRay(origin, direction)
    override fun quaternion(x: Double, y: Double, z: Double, w: Double): QuaternionView = FreestandingQuaternion(x, y, z, w)
}

object Geometry {
    val Provider = GeometryProvider.Auto

    @JvmStatic
    fun vector(x: Double, y: Double, z: Double) = Provider.vector(x, y, z)

    fun box(min: VectorView, max: VectorView) = Provider.box(min, max)

    fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) = Provider.box(minX, minY, minZ, maxX, maxY, maxZ)

    fun rotator(pitch: Double, yaw: Double, roll: Double = 0.0) = Provider.rotator(pitch, yaw, roll)

    fun ray(origin: VectorView, direction: VectorView) = Provider.ray(origin, direction)
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