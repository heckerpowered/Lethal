/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.math.GeometryProvider
import heckerpowered.bridge.math.RayIntersectionContext
import heckerpowered.bridge.math.intersectTime
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals

class EntitySectionRayTraversalTest {
    private val geometry = GeometryProvider.Freestanding

    @Test
    fun traversalMatchesFiniteSectionScanInEveryRayDirection() {
        val rays = listOf(
            geometry.ray(geometry.vector(8.0, 24.0, 8.0), geometry.vector(1.0, 0.25, 0.5)),
            geometry.ray(geometry.vector(40.0, 40.0, 40.0), geometry.vector(-1.0, -0.5, -0.25)),
            geometry.ray(geometry.vector(16.0, 16.0, 16.0), geometry.vector(-1.0, -1.0, 1.0)),
        )

        for (ray in rays) {
            val context = requireNotNull(RayIntersectionContext.create(ray, 96.0))
            val expected = scanSections(context, 2.0)
            val actual = EntitySectionRayTraversal(context, 2.0).toList()

            assertEquals(expected = expected.toSet(), actual = actual.toSet(), message = "Unexpected sections for $ray")
            assertEquals(expected = actual.map(EntitySectionIntersection::lowerBoundTime).sorted(), actual = actual.map(EntitySectionIntersection::lowerBoundTime), message = "Unordered section bounds for $ray")
            assertEquals(expected = actual.size, actual = actual.distinct().size, message = "Duplicate sections for $ray")
        }
    }

    @Test
    fun traversalPreservesVanillaAsymmetricSectionPadding() {
        val context = requireNotNull(RayIntersectionContext.create(geometry.ray(geometry.vector(8.0, 19.0, 8.0), geometry.vector(1.0, 0.0, 0.0)), 32.0))
        val section = EntitySectionRayTraversal(context, 2.0).first { it.sectionX == 0 && it.sectionY == 0 && it.sectionZ == 0 }

        assertEquals(expected = 0.0, actual = section.lowerBoundTime)
    }

    @Test
    fun maximumEntityRadiusCanExpandBeyondVanillaPadding() {
        val context = requireNotNull(RayIntersectionContext.create(geometry.ray(geometry.vector(23.0, 8.0, 8.0), geometry.vector(0.0, 0.0, 1.0)), 32.0))
        val section = EntitySectionRayTraversal(context, 8.0).first { it.sectionX == 0 && it.sectionY == 0 && it.sectionZ == 0 }

        assertEquals(expected = 0.0, actual = section.lowerBoundTime)
    }

    private fun scanSections(context: RayIntersectionContext, maximumEntityRadius: Double): List<EntitySectionIntersection> {
        val intersections = mutableListOf<EntitySectionIntersection>()
        for (sectionX in -8..8) {
            for (sectionY in -8..8) {
                for (sectionZ in -8..8) {
                    val lowerBoundTime = sectionLowerBoundTime(context, maximumEntityRadius, sectionX, sectionY, sectionZ)
                    if (!lowerBoundTime.isNaN()) intersections += EntitySectionIntersection(sectionX, sectionY, sectionZ, lowerBoundTime)
                }
            }
        }
        return intersections.sortedWith(compareBy(EntitySectionIntersection::lowerBoundTime).thenBy(EntitySectionIntersection::sectionX).thenBy(EntitySectionIntersection::sectionY).thenBy(EntitySectionIntersection::sectionZ))
    }

    private fun sectionLowerBoundTime(context: RayIntersectionContext, maximumEntityRadius: Double, sectionX: Int, sectionY: Int, sectionZ: Int): Double {
        val horizontalPadding = max(maximumEntityRadius, 2.0)
        val upwardPadding = max(maximumEntityRadius, 4.0)
        val minimumX = sectionX * 16.0 - horizontalPadding
        val minimumY = sectionY * 16.0 - maximumEntityRadius
        val minimumZ = sectionZ * 16.0 - horizontalPadding
        val maximumX = (sectionX + 1) * 16.0 + horizontalPadding
        val maximumY = (sectionY + 1) * 16.0 + upwardPadding
        val maximumZ = (sectionZ + 1) * 16.0 + horizontalPadding
        return intersectTime(context, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
    }
}
