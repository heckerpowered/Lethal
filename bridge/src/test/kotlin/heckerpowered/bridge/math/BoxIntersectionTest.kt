/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoxIntersectionTest {
    private val box = Geometry.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

    @Test
    fun outsideRayReportsEntryExitPointsTimesAndNormals() {
        val ray = Geometry.ray(Geometry.vector(-2.0, 0.5, 0.5), Geometry.vector(2.0, 0.0, 0.0))

        val intersection = assertNotNull(box.intersect(ray))

        assertFalse(intersection.startsInside)
        assertEquals(expected = 1.0, actual = intersection.enterTime)
        assertEquals(expected = 1.5, actual = intersection.exitTime)
        assertVector(-1.0, 0.0, 0.0, assertNotNull(intersection.enterNormal))
        assertVector(1.0, 0.0, 0.0, intersection.exitNormal)
        assertVector(0.0, 0.5, 0.5, assertNotNull(intersection.enterSurfacePoint))
        assertVector(0.0, 0.5, 0.5, intersection.enterPoint)
        assertVector(1.0, 0.5, 0.5, intersection.exitPoint)
        assertEquals(expected = 1.0, actual = intersection.nearestSurfaceHitTime)
        assertEquals(expected = 1.0, actual = intersection.nearestHitTime)
        assertVector(0.0, 0.5, 0.5, intersection.nearestHitPoint)
        assertVector(0.0, 0.5, 0.5, intersection.nearestSurfaceHitPoint)
        assertVector(-1.0, 0.0, 0.0, intersection.nearestHitNormal)
    }

    @Test
    fun insideRayUsesOriginAsNearestHitAndPreservesSurfaceExit() {
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 0.5), Geometry.vector(0.0, 0.0, 2.0))

        val intersection = assertNotNull(box.intersect(ray, length = 0.1))

        assertTrue(intersection.startsInside)
        assertNull(intersection.enterTime)
        assertNull(intersection.enterNormal)
        assertNull(intersection.enterSurfacePoint)
        assertEquals(expected = 0.25, actual = intersection.exitTime)
        assertVector(0.5, 0.5, 0.5, intersection.enterPoint)
        assertVector(0.5, 0.5, 1.0, intersection.exitPoint)
        assertEquals(expected = 0.25, actual = intersection.nearestSurfaceHitTime)
        assertEquals(expected = 0.0, actual = intersection.nearestHitTime)
        assertVector(0.5, 0.5, 0.5, intersection.nearestHitPoint)
        assertVector(0.5, 0.5, 1.0, intersection.nearestSurfaceHitPoint)
        assertVector(0.0, 0.0, 1.0, intersection.nearestHitNormal)
    }

    @Test
    fun physicalLengthDeterminesWhetherTheRayReachesTheBox() {
        val ray = Geometry.ray(Geometry.vector(-2.0, 0.5, 0.5), Geometry.vector(2.0, 0.0, 0.0))

        assertNull(box.intersect(ray, length = 1.99))
        assertNotNull(box.intersect(ray, length = 2.0))
        assertEquals(expected = 1.0, actual = box.intersectTime(ray, length = 2.0))
    }

    @Test
    fun parallelBehindAndDegenerateRaysDoNotIntersect() {
        val parallel = Geometry.ray(Geometry.vector(-2.0, 2.0, 0.5), Vectors.UnitX)
        val behind = Geometry.ray(Geometry.vector(2.0, 0.5, 0.5), Vectors.UnitX)
        val degenerate = Geometry.ray(Geometry.vector(0.5, 0.5, 0.5), Vectors.Zero)

        assertNull(box.intersect(parallel))
        assertNull(box.intersect(behind))
        assertNull(box.intersect(degenerate))
        assertTrue(box.intersectTime(parallel, length = 10.0).isNaN())
        assertTrue(box.intersectTime(behind, length = 10.0).isNaN())
        assertTrue(box.intersectTime(degenerate, length = 10.0).isNaN())
    }

    @Test
    fun intersectionTimeUsesZeroForOriginsInsideTheBox() {
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 0.5), Vectors.UnitZ)
        val context = requireNotNull(RayIntersectionContext.create(ray, length = 0.1))

        assertEquals(expected = 0.0, actual = box.intersectTime(context))
        assertEquals(
            expected = 0.0,
            actual = intersectTime(0.5, 0.5, 0.5, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.1),
        )
    }

    @Test
    fun everyIntersectionEntryPointUsesTheSameRayParameterSpace() {
        val ray = Geometry.ray(Geometry.vector(-4.0, 0.25, 0.25), Geometry.vector(2.0, 0.1, 0.05))
        val context = requireNotNull(RayIntersectionContext.create(ray, length = 20.0))
        val expected = assertNotNull(box.intersect(ray, length = 20.0))
        val contextIntersection = assertNotNull(box.intersect(context))

        assertEquals(expected = expected.enterTime, actual = contextIntersection.enterTime)
        assertEquals(expected = expected.exitTime, actual = contextIntersection.exitTime)
        assertVector(expected.enterPoint.x, expected.enterPoint.y, expected.enterPoint.z, contextIntersection.enterPoint)
        assertVector(expected.exitPoint.x, expected.exitPoint.y, expected.exitPoint.z, contextIntersection.exitPoint)
        assertEquals(expected = expected.nearestHitTime, actual = box.intersectTime(context))
        assertEquals(expected = expected.nearestHitTime, actual = intersectTime(context, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ))
        assertEquals(
            expected = expected.nearestHitTime,
            actual = intersectTime(
                context.originX,
                context.originY,
                context.originZ,
                context.directionX,
                context.directionY,
                context.directionZ,
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ,
                context.maximumTime,
            ),
        )
    }

    @Test
    fun reverseDirectionSwapsMinimumAndMaximumFaceNormals() {
        val ray = Geometry.ray(Geometry.vector(2.0, 0.5, 0.5), Geometry.vector(-2.0, 0.0, 0.0))

        val intersection = assertNotNull(box.intersect(ray))

        assertEquals(expected = 0.5, actual = intersection.enterTime)
        assertEquals(expected = 1.0, actual = intersection.exitTime)
        assertVector(1.0, 0.0, 0.0, assertNotNull(intersection.enterNormal))
        assertVector(-1.0, 0.0, 0.0, intersection.exitNormal)
    }

    @Test
    fun pointBoxProducesOneSharedEntryAndExitSurfacePoint() {
        val point = Geometry.vector(1.0, 2.0, 3.0)
        val pointBox = point.asPointBox()
        val ray = Geometry.ray(Geometry.vector(-1.0, 2.0, 3.0), Geometry.vector(2.0, 0.0, 0.0))

        val intersection = assertNotNull(pointBox.intersect(ray))

        assertEquals(expected = 1.0, actual = intersection.enterTime)
        assertEquals(expected = 1.0, actual = intersection.exitTime)
        assertVector(1.0, 2.0, 3.0, intersection.enterPoint)
        assertVector(1.0, 2.0, 3.0, intersection.exitPoint)
        assertTrue(pointBox.containsOrOn(point))
        assertFalse(pointBox.contains(point))
        assertEquals(expected = 0.0, actual = pointBox.volume)
    }
}
