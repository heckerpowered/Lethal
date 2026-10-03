/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.*

class BoxCompositionTest {
    @Test
    fun translatingTheBoxAndRayTogetherPreservesIntersectionParametersAndNormals() {
        val box = Box(0.0, 0.0, 0.0, 2.0, 3.0, 4.0)
        val ray = Ray(Vector(-4.0, 1.0, 1.0), Vector(2.0, 0.5, 0.25))
        val offset = Vector(10.0, -7.0, 3.0)

        val original = assertNotNull(box.intersect(ray))
        val translated = assertNotNull(box.translatedBy(offset).intersect(Ray(ray.origin + offset, ray.direction)))
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
        val box = Box(0.0, 0.0, 0.0, 2.0, 3.0, 4.0)
        val origin = Vector(-4.0, 1.0, 1.0)
        val direction = Vector(2.0, 0.5, 0.25)
        val scaledDirection = direction * 3.0
        val entryPoint = Vector(0.0, 2.0, 1.5)
        val physicalEntryDistance = origin.distanceTo(entryPoint)

        val original = assertNotNull(box.intersect(Ray(origin, direction), length = physicalEntryDistance + 1.0E-12))
        val scaled = assertNotNull(box.intersect(Ray(origin, scaledDirection), length = physicalEntryDistance + 1.0E-12))
        val originalEnterTime = assertNotNull(original.enterTime)
        val scaledEnterTime = assertNotNull(scaled.enterTime)

        assertEquals(expected = originalEnterTime / 3.0, actual = scaledEnterTime, absoluteTolerance = 1.0E-12)
        assertVector(entryPoint.x, entryPoint.y, entryPoint.z, original.enterPoint, 1.0E-12)
        assertVector(entryPoint.x, entryPoint.y, entryPoint.z, scaled.enterPoint, 1.0E-12)
        assertNull(box.intersect(Ray(origin, direction), length = physicalEntryDistance - 1.0E-9))
        assertNull(box.intersect(Ray(origin, scaledDirection), length = physicalEntryDistance - 1.0E-9))
    }

    @Test
    fun pointExpansionTranslationAndUnionComposeIntoExpectedBounds() {
        val point = Vector(1.0, 2.0, 3.0)

        val box = point.asPointBox()
            .expandedBy(Vector(1.0, 2.0, 3.0))
            .translatedBy(Vector(4.0, -1.0, 2.0))
            .union(Vector(10.0, 10.0, 10.0))

        assertBox(4.0, -1.0, 2.0, 10.0, 10.0, 10.0, box)
        assertVector(7.0, 4.5, 6.0, box.center)
        assertTrue(box.containsOrOn(Vector(4.0, -1.0, 2.0)))
        assertTrue(box.containsOrOn(Vector(10.0, 10.0, 10.0)))
    }

    @Test
    fun overlapIsContainedByBothInputsWhileUnionContainsBothInputs() {
        val first = Box(-3.0, -2.0, -1.0, 4.0, 5.0, 6.0)
        val second = Box(1.0, -4.0, 2.0, 8.0, 3.0, 10.0)

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
