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