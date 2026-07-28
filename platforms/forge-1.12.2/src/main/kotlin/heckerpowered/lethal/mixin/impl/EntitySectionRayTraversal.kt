/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.math.RayIntersectionContext
import heckerpowered.bridge.math.intersectTime
import java.util.PriorityQueue
import kotlin.math.floor

/**
 * Enumerates Forge 1.12.2 entity storage sections by conservative ray entry time.
 */
internal class EntitySectionRayTraversal(private val context: RayIntersectionContext, private val radius: Double) : Sequence<EntitySectionIntersection> {
    init {
        require(radius.isFinite() && radius >= 0.0) { "Entity section radius must be finite and non-negative" }
    }

    override fun iterator(): Iterator<EntitySectionIntersection> = sequence {
        val pendingSections = PriorityQueue(IntersectionOrder)
        val visitedSections = HashSet<EntitySectionPosition>()

        fun enqueue(chunkX: Int, sectionY: Int, chunkZ: Int) {
            if (sectionY !in 0..LAST_SECTION_INDEX) return

            val position = EntitySectionPosition(chunkX, sectionY, chunkZ)
            if (!visitedSections.add(position)) return

            val lowerBoundTime = sectionLowerBoundTime(position)
            if (lowerBoundTime.isNaN()) return

            pendingSections += EntitySectionIntersection(chunkX, sectionY, chunkZ, lowerBoundTime)
        }

        enqueue(sectionCoordinate(context.originX), sectionCoordinate(context.originY).coerceIn(0, LAST_SECTION_INDEX), sectionCoordinate(context.originZ))

        // Intersections with every ray prefix form a face-connected grid region, so expanding
        // the earliest queued section cannot hide an undiscovered section with an earlier bound.
        while (pendingSections.isNotEmpty()) {
            val section = pendingSections.remove()
            enqueue(section.chunkX - 1, section.sectionY, section.chunkZ)
            enqueue(section.chunkX + 1, section.sectionY, section.chunkZ)
            enqueue(section.chunkX, section.sectionY - 1, section.chunkZ)
            enqueue(section.chunkX, section.sectionY + 1, section.chunkZ)
            enqueue(section.chunkX, section.sectionY, section.chunkZ - 1)
            enqueue(section.chunkX, section.sectionY, section.chunkZ + 1)
            yield(section)
        }
    }.iterator()

    private fun sectionLowerBoundTime(position: EntitySectionPosition): Double {
        val minimumX = position.chunkX * SECTION_SIZE - radius
        val minimumY = if (position.sectionY == 0) Double.NEGATIVE_INFINITY else position.sectionY * SECTION_SIZE - radius
        val minimumZ = position.chunkZ * SECTION_SIZE - radius
        val maximumX = (position.chunkX + 1) * SECTION_SIZE + radius
        val maximumY = if (position.sectionY == LAST_SECTION_INDEX) Double.POSITIVE_INFINITY else (position.sectionY + 1) * SECTION_SIZE + radius
        val maximumZ = (position.chunkZ + 1) * SECTION_SIZE + radius
        return intersectTime(context, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
    }

    private companion object {
        const val SECTION_SIZE = 16.0
        const val LAST_SECTION_INDEX = 15
        val IntersectionOrder = compareBy(EntitySectionIntersection::lowerBoundTime)
            .thenBy(EntitySectionIntersection::chunkX)
            .thenBy(EntitySectionIntersection::sectionY)
            .thenBy(EntitySectionIntersection::chunkZ)

        fun sectionCoordinate(blockCoordinate: Double): Int = floor(blockCoordinate / SECTION_SIZE).toInt()
    }
}

internal data class EntitySectionIntersection(
    val chunkX: Int,
    val sectionY: Int,
    val chunkZ: Int,
    val lowerBoundTime: Double,
)

private data class EntitySectionPosition(
    val chunkX: Int,
    val sectionY: Int,
    val chunkZ: Int,
)
