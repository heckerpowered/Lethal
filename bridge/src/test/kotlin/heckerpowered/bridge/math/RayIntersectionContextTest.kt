/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RayIntersectionContextTest {
    @Test
    fun zeroAndNearlyZeroDirectionsCannotCreateAContext() {
        assertNull(RayIntersectionContext.create(Geometry.ray(Vectors.Zero, Vectors.Zero)))
        assertNull(RayIntersectionContext.create(Geometry.ray(Vectors.Zero, Geometry.vector(1.0E-9, 0.0, 0.0))))
    }

    @Test
    fun contextCachesCoordinatesAndInverseDirections() {
        val ray = Geometry.ray(Geometry.vector(1.0, 2.0, 3.0), Geometry.vector(3.0, 0.0, 4.0))
        val context = requireNotNull(RayIntersectionContext.create(ray, 20.0))

        assertSame(expected = ray, actual = context.ray)
        assertEquals(expected = 4.0, actual = context.maximumTime)
        assertEquals(expected = 1.0, actual = context.originX)
        assertEquals(expected = 2.0, actual = context.originY)
        assertEquals(expected = 3.0, actual = context.originZ)
        assertEquals(expected = 3.0, actual = context.directionX)
        assertEquals(expected = 0.0, actual = context.directionY)
        assertEquals(expected = 4.0, actual = context.directionZ)
        assertFalse(context.directionXIsNearlyZero)
        assertTrue(context.directionYIsNearlyZero)
        assertFalse(context.directionZIsNearlyZero)
        assertEquals(expected = 1.0 / 3.0, actual = context.inverseDirectionX)
        assertEquals(expected = 0.0, actual = context.inverseDirectionY)
        assertEquals(expected = 0.25, actual = context.inverseDirectionZ)
    }

    @Test
    fun omittedLengthCreatesAnUnboundedContext() {
        val context = requireNotNull(RayIntersectionContext.create(Geometry.ray(Vectors.Zero, Vectors.UnitZ)))

        assertEquals(expected = Double.POSITIVE_INFINITY, actual = context.maximumTime)
    }
}
