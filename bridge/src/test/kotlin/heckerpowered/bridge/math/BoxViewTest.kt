/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoxViewTest {
    @Test
    fun boundsExposeDerivedPositionAndSizeProperties() {
        val box = Geometry.box(-2.0, -1.0, 0.0, 4.0, 5.0, 8.0)

        assertVector(-2.0, -1.0, 0.0, box.min)
        assertVector(4.0, 5.0, 8.0, box.max)
        assertVector(1.0, 2.0, 4.0, box.center)
        assertVector(6.0, 6.0, 8.0, box.size)
        assertVector(3.0, 3.0, 4.0, box.extent)
        assertEquals(expected = 288.0, actual = box.volume)
    }

    @Test
    fun expansionSupportsSymmetricAndAsymmetricAmounts() {
        val box = Geometry.box(0.0, 1.0, 2.0, 4.0, 5.0, 6.0)

        assertBox(-1.0, 0.0, 1.0, 5.0, 6.0, 7.0, box.expandedBy(1.0))
        assertBox(-1.0, -1.0, -1.0, 5.0, 7.0, 9.0, box.expandedBy(Geometry.vector(1.0, 2.0, 3.0)))
        assertBox(-1.0, -1.0, -1.0, 8.0, 10.0, 12.0, box.expandedBy(Geometry.vector(1.0, 2.0, 3.0), Geometry.vector(4.0, 5.0, 6.0)))
    }

    @Test
    fun translationAndMoveToPreserveBoxSize() {
        val box = Geometry.box(0.0, 1.0, 2.0, 4.0, 5.0, 6.0)

        assertBox(1.0, 0.0, 4.0, 5.0, 4.0, 8.0, box.translatedBy(Geometry.vector(1.0, -1.0, 2.0)))
        assertBox(8.0, 18.0, 28.0, 12.0, 22.0, 32.0, box.movedTo(Geometry.vector(10.0, 20.0, 30.0)))
    }

    @Test
    fun unionAndClosestPointExpandOrClampEachAxis() {
        val box = Geometry.box(0.0, 0.0, 0.0, 2.0, 2.0, 2.0)

        assertBox(-1.0, 0.0, 0.0, 2.0, 3.0, 4.0, box.union(Geometry.vector(-1.0, 3.0, 4.0)))
        assertBox(-2.0, -1.0, 0.0, 3.0, 2.0, 5.0, box.union(Geometry.box(-2.0, -1.0, 1.0, 3.0, 1.0, 5.0)))
        assertVector(0.0, 1.0, 2.0, box.closestPointTo(Geometry.vector(-3.0, 1.0, 4.0)))
        assertEquals(expected = 13.0, actual = box.distanceSquaredTo(Geometry.vector(-3.0, 1.0, 4.0)))
        assertEquals(expected = 0.0, actual = box.distanceSquaredTo(Geometry.vector(1.0, 1.0, 1.0)))
    }

    @Test
    fun strictContainmentExcludesBoundariesWhileInclusiveContainmentKeepsThem() {
        val box = Geometry.box(0.0, 0.0, 0.0, 10.0, 10.0, 10.0)
        val interior = Geometry.vector(5.0, 5.0, 5.0)
        val boundary = Geometry.vector(0.0, 5.0, 10.0)
        val innerBox = Geometry.box(1.0, 1.0, 1.0, 9.0, 9.0, 9.0)
        val touchingBox = Geometry.box(0.0, 1.0, 1.0, 9.0, 9.0, 9.0)

        assertTrue(box.contains(interior))
        assertTrue(box.containsOrOn(interior))
        assertFalse(box.contains(boundary))
        assertTrue(box.containsOrOn(boundary))
        assertTrue(box.pointBoxIntersection(boundary))
        assertTrue(box.contains(innerBox))
        assertTrue(box.containsOrOn(innerBox))
        assertFalse(box.contains(touchingBox))
        assertTrue(box.containsOrOn(touchingBox))
    }

    @Test
    fun horizontalContainmentIgnoresVerticalCoordinates() {
        val box = Geometry.box(0.0, 0.0, 0.0, 10.0, 10.0, 10.0)
        val interior = Geometry.vector(5.0, 100.0, 5.0)
        val boundary = Geometry.vector(0.0, -100.0, 10.0)
        val innerBox = Geometry.box(1.0, -100.0, 1.0, 9.0, 100.0, 9.0)
        val touchingBox = Geometry.box(0.0, -100.0, 1.0, 9.0, 100.0, 9.0)

        assertTrue(box.containsHorizontal(interior))
        assertFalse(box.containsHorizontal(boundary))
        assertTrue(box.containsOrOnHorizontal(boundary))
        assertTrue(box.containsHorizontal(innerBox))
        assertFalse(box.containsHorizontal(touchingBox))
        assertTrue(box.containsOrOnHorizontal(touchingBox))
    }

    @Test
    fun intersectionIncludesTouchingFacesAndOverlapReturnsTheirSharedBounds() {
        val box = Geometry.box(0.0, 0.0, 0.0, 2.0, 2.0, 2.0)
        val overlap = Geometry.box(1.0, -1.0, 0.5, 3.0, 1.0, 4.0)
        val touching = Geometry.box(2.0, 0.5, 0.5, 3.0, 1.5, 1.5)
        val separated = Geometry.box(3.0, 3.0, 3.0, 4.0, 4.0, 4.0)

        assertTrue(box.intersects(overlap))
        assertTrue(box.intersects(touching))
        assertFalse(box.intersects(separated))
        assertBox(1.0, 0.0, 0.5, 2.0, 1.0, 2.0, box.overlap(overlap))
        assertBox(2.0, 0.5, 0.5, 2.0, 1.5, 1.5, box.overlap(touching))
        assertBox(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, box.overlap(separated))
    }

    @Test
    fun horizontalIntersectionIgnoresVerticalSeparation() {
        val box = Geometry.box(0.0, 0.0, 0.0, 2.0, 2.0, 2.0)
        val verticallySeparated = Geometry.box(1.0, 10.0, 1.0, 3.0, 12.0, 3.0)
        val horizontallySeparated = Geometry.box(3.0, 1.0, 3.0, 4.0, 2.0, 4.0)

        assertTrue(box.intersectsHorizontal(verticallySeparated))
        assertFalse(box.intersects(verticallySeparated))
        assertFalse(box.intersectsHorizontal(horizontallySeparated))
    }

    @Test
    fun verticesContainEveryCombinationOfMinimumAndMaximumCoordinates() {
        val box = Geometry.box(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
        val expected = setOf(
            Triple(1.0, 2.0, 3.0),
            Triple(1.0, 2.0, 6.0),
            Triple(1.0, 5.0, 3.0),
            Triple(1.0, 5.0, 6.0),
            Triple(4.0, 2.0, 3.0),
            Triple(4.0, 2.0, 6.0),
            Triple(4.0, 5.0, 3.0),
            Triple(4.0, 5.0, 6.0),
        )

        assertEquals(expected = expected, actual = box.vertices().map(VectorView::coordinates).toSet())
    }

    @Test
    fun sphereIntersectionIncludesTheTangentBoundary() {
        val box = Geometry.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        val tangentCenter = Geometry.vector(2.0, 0.5, 0.5)

        assertTrue(box.intersectsSphere(tangentCenter, 1.0))
        assertTrue(box.intersectsSphereSquared(tangentCenter, 1.0))
        assertFalse(box.intersectsSphere(tangentCenter, 0.99))
        assertTrue(box.intersectsSphere(Geometry.vector(0.5, 0.5, 0.5), 0.0))
    }
}
