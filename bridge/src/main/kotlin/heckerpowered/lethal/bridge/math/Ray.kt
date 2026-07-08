/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.math

import kotlin.math.sqrt

interface RayView {
    val origin: VectorView
    val direction: VectorView

    fun pointAt(time: Double): VectorView {
        return origin + direction * time
    }

    fun RayView.closestPointTo(point: VectorView): VectorView {
        val directionLengthSquared = direction.lengthSquared
        if (directionLengthSquared <= 1.0E-8) return origin

        val time = (point - origin).dot(direction) / directionLengthSquared

        return if (time <= 0.0) origin else pointAt(time)
    }

    fun distanceSquaredTo(point: VectorView): Double {
        return closestPointTo(point).distanceSquaredTo(point)
    }

    fun distanceTo(point: VectorView): Double {
        return sqrt(distanceSquaredTo(point))
    }
}

/**
 * Caches ray values reused by repeated ray-box intersection tests.
 */
class RayIntersectionContext private constructor(val ray: RayView, directionLength: Double, length: Double?) {
    val maximumTime: Double = length?.let { it / directionLength } ?: Double.POSITIVE_INFINITY

    val originX: Double
    val originY: Double
    val originZ: Double

    val directionX: Double
    val directionY: Double
    val directionZ: Double

    val directionXIsNearlyZero: Boolean
    val directionYIsNearlyZero: Boolean
    val directionZIsNearlyZero: Boolean

    val inverseDirectionX: Double
    val inverseDirectionY: Double
    val inverseDirectionZ: Double

    init {
        val origin = ray.origin
        originX = origin.x
        originY = origin.y
        originZ = origin.z

        val direction = ray.direction
        directionX = direction.x
        directionY = direction.y
        directionZ = direction.z

        directionXIsNearlyZero = directionX.isNearlyZero()
        directionYIsNearlyZero = directionY.isNearlyZero()
        directionZIsNearlyZero = directionZ.isNearlyZero()

        inverseDirectionX = if (directionXIsNearlyZero) 0.0 else 1.0 / directionX
        inverseDirectionY = if (directionYIsNearlyZero) 0.0 else 1.0 / directionY
        inverseDirectionZ = if (directionZIsNearlyZero) 0.0 else 1.0 / directionZ
    }

    companion object {
        fun create(ray: RayView, length: Double? = null): RayIntersectionContext? {
            val directionLength = ray.direction.length
            if (directionLength.isNearlyZero()) return null

            return RayIntersectionContext(ray, directionLength, length)
        }
    }
}
