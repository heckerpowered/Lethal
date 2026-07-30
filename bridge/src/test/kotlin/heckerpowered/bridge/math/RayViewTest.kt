/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals

class RayViewTest {
    @Test
    fun pointAtUsesRayParameterSpace() {
        val ray = Geometry.ray(Geometry.vector(1.0, 2.0, 3.0), Geometry.vector(2.0, -1.0, 4.0))

        assertVector(6.0, -0.5, 13.0, ray.pointAt(2.5))
    }

    @Test
    fun closestPointIsClampedToTheRayOrigin() {
        val ray = Geometry.ray(Vectors.Zero, Vectors.UnitX)

        assertVector(3.0, 0.0, 0.0, closestPoint(ray, Geometry.vector(3.0, 4.0, 0.0)))
        assertVector(0.0, 0.0, 0.0, closestPoint(ray, Geometry.vector(-3.0, 4.0, 0.0)))
        assertEquals(expected = 16.0, actual = ray.distanceSquaredTo(Geometry.vector(3.0, 4.0, 0.0)))
        assertEquals(expected = 5.0, actual = ray.distanceTo(Geometry.vector(-3.0, 4.0, 0.0)))
    }

    @Test
    fun zeroDirectionTreatsOriginAsTheOnlyRayPoint() {
        val origin = Geometry.vector(1.0, 2.0, 3.0)
        val ray = Geometry.ray(origin, Vectors.Zero)
        val point = Geometry.vector(4.0, 6.0, 3.0)

        assertVector(origin.x, origin.y, origin.z, closestPoint(ray, point))
        assertEquals(expected = 25.0, actual = ray.distanceSquaredTo(point))
        assertEquals(expected = 5.0, actual = ray.distanceTo(point))
    }

    private fun closestPoint(ray: RayView, point: VectorView): VectorView {
        return with(ray) { ray.closestPointTo(point) }
    }
}
