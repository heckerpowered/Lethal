/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.Test
import kotlin.test.assertEquals

class RayViewTest {
    @Test
    fun pointAtUsesRayParameterSpace() {
        val ray = Ray(Vector(1.0, 2.0, 3.0), Vector(2.0, -1.0, 4.0))

        assertVector(6.0, -0.5, 13.0, ray.pointAt(2.5))
    }

    @Test
    fun closestPointIsClampedToTheRayOrigin() {
        val ray = Ray(Vectors.Zero, Vectors.UnitX)

        assertVector(3.0, 0.0, 0.0, closestPoint(ray, Vector(3.0, 4.0, 0.0)))
        assertVector(0.0, 0.0, 0.0, closestPoint(ray, Vector(-3.0, 4.0, 0.0)))
        assertEquals(expected = 16.0, actual = ray.distanceSquaredTo(Vector(3.0, 4.0, 0.0)))
        assertEquals(expected = 5.0, actual = ray.distanceTo(Vector(-3.0, 4.0, 0.0)))
    }

    @Test
    fun zeroDirectionTreatsOriginAsTheOnlyRayPoint() {
        val origin = Vector(1.0, 2.0, 3.0)
        val ray = Ray(origin, Vectors.Zero)
        val point = Vector(4.0, 6.0, 3.0)

        assertVector(origin.x, origin.y, origin.z, closestPoint(ray, point))
        assertEquals(expected = 25.0, actual = ray.distanceSquaredTo(point))
        assertEquals(expected = 5.0, actual = ray.distanceTo(point))
    }

    private fun closestPoint(ray: RayView, point: VectorView): VectorView {
        return with(ray) { ray.closestPointTo(point) }
    }
}
