/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoxCompositionTest {
    @Test
    fun translatingTheBoxAndRayTogetherPreservesIntersectionParametersAndNormals() {
        val box = Geometry.box(0.0, 0.0, 0.0, 2.0, 3.0, 4.0)
        val ray = Geometry.ray(Geometry.vector(-4.0, 1.0, 1.0), Geometry.vector(2.0, 0.5, 0.25))
        val offset = Geometry.vector(10.0, -7.0, 3.0)

        val original = assertNotNull(box.intersect(ray))
        val translated = assertNotNull(box.translatedBy(offset).intersect(Geometry.ray(ray.origin + offset, ray.direction)))
        val originalEnterNormal = assertNotNull(original.enterNormal)
        val translatedEnterNormal = assertNotNull(translated.enterNormal)

        assertEquals(expected = original.enterTime, actual = translated.enterTime)
        assertEquals(expected = original.exitTime, actual = translated.exitTime)
        assertVector(originalEnterNormal.x, originalEnterNormal.y, originalEnterNormal.z, translatedEnterNormal)
        assertVector(original.exitNormal.x, original.exitNormal.y, original.exitNormal.z, translated.exitNormal)
        assertVector(original.enterPoint.x + offset.x, original.enterPoint.y + offset.y, original.enterPoint.z + offset.z, translated.enterPoint)
        assertVector(original.exitPoint.x + offset.x, original.exitPoint.y + offset.y, original.exitPoint.z + offset.z, translated.exitPoint)
    }

    @Test
    fun directionScalingChangesTimesWithoutChangingPhysicalReachOrHitPoints() {
        val box = Geometry.box(0.0, 0.0, 0.0, 2.0, 3.0, 4.0)
        val origin = Geometry.vector(-4.0, 1.0, 1.0)
        val direction = Geometry.vector(2.0, 0.5, 0.25)
        val scaledDirection = direction * 3.0
        val entryPoint = Geometry.vector(0.0, 2.0, 1.5)
        val physicalEntryDistance = origin.distanceTo(entryPoint)

        val original = assertNotNull(box.intersect(Geometry.ray(origin, direction), length = physicalEntryDistance + 1.0E-12))
        val scaled = assertNotNull(box.intersect(Geometry.ray(origin, scaledDirection), length = physicalEntryDistance + 1.0E-12))
        val originalEnterTime = assertNotNull(original.enterTime)
        val scaledEnterTime = assertNotNull(scaled.enterTime)

        assertEquals(expected = originalEnterTime / 3.0, actual = scaledEnterTime, absoluteTolerance = 1.0E-12)
        assertVector(entryPoint.x, entryPoint.y, entryPoint.z, original.enterPoint, 1.0E-12)
        assertVector(entryPoint.x, entryPoint.y, entryPoint.z, scaled.enterPoint, 1.0E-12)
        assertNull(box.intersect(Geometry.ray(origin, direction), length = physicalEntryDistance - 1.0E-9))
        assertNull(box.intersect(Geometry.ray(origin, scaledDirection), length = physicalEntryDistance - 1.0E-9))
    }

    @Test
    fun pointExpansionTranslationAndUnionComposeIntoExpectedBounds() {
        val point = Geometry.vector(1.0, 2.0, 3.0)

        val box = point.asPointBox()
            .expandedBy(Geometry.vector(1.0, 2.0, 3.0))
            .translatedBy(Geometry.vector(4.0, -1.0, 2.0))
            .union(Geometry.vector(10.0, 10.0, 10.0))

        assertBox(4.0, -1.0, 2.0, 10.0, 10.0, 10.0, box)
        assertVector(7.0, 4.5, 6.0, box.center)
        assertTrue(box.containsOrOn(Geometry.vector(4.0, -1.0, 2.0)))
        assertTrue(box.containsOrOn(Geometry.vector(10.0, 10.0, 10.0)))
    }

    @Test
    fun overlapIsContainedByBothInputsWhileUnionContainsBothInputs() {
        val first = Geometry.box(-3.0, -2.0, -1.0, 4.0, 5.0, 6.0)
        val second = Geometry.box(1.0, -4.0, 2.0, 8.0, 3.0, 10.0)

        val overlap = first.overlap(second)
        val union = first.union(second)

        assertTrue(first.containsOrOn(overlap))
        assertTrue(second.containsOrOn(overlap))
        assertTrue(union.containsOrOn(first))
        assertTrue(union.containsOrOn(second))
        assertBox(1.0, -2.0, 2.0, 4.0, 3.0, 6.0, overlap)
        assertBox(-3.0, -4.0, -1.0, 8.0, 5.0, 10.0, union)
    }
}
