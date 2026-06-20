package heckerpowered.lethal.bridge.math

import heckerpowered.lethal.bridge.platform.Services
import heckerpowered.lethal.bridge.platform.loadOrNull

interface GeometryProvider {
    companion object {
        val Freestanding: GeometryProvider = FreestandingGeometryProvider
        val Hosting: GeometryProvider?
            get() = Services.loadOrNull<GeometryProvider>()
        val Auto
            get() = Hosting ?: Freestanding
    }

    fun vector(x: Double, y: Double, z: Double): VectorView = Freestanding.vector(x, y, z)
    fun box(min: VectorView, max: VectorView): BoxView = Freestanding.box(min, max)
    fun rotator(pitch: Double, yaw: Double, roll: Double = 0.0): RotatorView = Freestanding.rotator(pitch, yaw, roll)
    fun ray(origin: VectorView, direction: VectorView): RayView = Freestanding.ray(origin, direction)
}

object FreestandingGeometryProvider : GeometryProvider {
    override fun vector(x: Double, y: Double, z: Double): VectorView = FreestandingVector(x, y, z)
    override fun box(min: VectorView, max: VectorView): BoxView = FreestandingBox(min, max)
    override fun rotator(pitch: Double, yaw: Double, roll: Double): RotatorView = FreestandingRotator(pitch, yaw, roll)
    override fun ray(origin: VectorView, direction: VectorView): RayView = FreestandingRay(origin, direction)
}

object Geometry {
    var Provider = GeometryProvider.Auto

    @JvmStatic
    fun vector(x: Double, y: Double, z: Double) = Provider.vector(x, y, z)

    @JvmStatic
    fun box(min: VectorView, max: VectorView) = Provider.box(min, max)

    @JvmStatic
    fun rotator(pitch: Double, yaw: Double, roll: Double = 0.0) = Provider.rotator(pitch, yaw, roll)

    @JvmStatic
    fun ray(origin: VectorView, direction: VectorView) = Provider.ray(origin, direction)
}

private data class FreestandingVector(
    override val x: Double,
    override val y: Double,
    override val z: Double,
) : VectorView

private data class FreestandingBox(
    override val min: VectorView,
    override val max: VectorView,
) : BoxView

private data class FreestandingRotator(
    override val pitch: Double,
    override val yaw: Double,
    override val roll: Double = 0.0,
) : RotatorView

private data class FreestandingRay(
    override val origin: VectorView,
    override val direction: VectorView,
) : RayView