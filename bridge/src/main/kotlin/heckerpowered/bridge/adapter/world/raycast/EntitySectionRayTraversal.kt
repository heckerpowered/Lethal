/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.world.raycast

import heckerpowered.math.RayIntersectionContext
import heckerpowered.math.intersectTime
import java.util.*
import kotlin.math.floor

/**
 * Enumerates entity storage sections by conservative ray entry time. The host supplies
 * its section padding and any fixed vertical storage range.
 */
class EntitySectionRayTraversal(private val context: RayIntersectionContext, private val padding: EntitySectionPadding, private val verticalSections: IntRange? = null) : Sequence<EntitySectionIntersection> {
    override fun iterator(): Iterator<EntitySectionIntersection> = sequence {
        val pendingSections = PriorityQueue(IntersectionOrder)
        val visitedSections = HashSet<EntitySectionPosition>()

        fun enqueue(sectionX: Int, sectionY: Int, sectionZ: Int) {
            if (verticalSections != null && sectionY !in verticalSections) return

            val position = EntitySectionPosition(sectionX, sectionY, sectionZ)
            if (!visitedSections.add(position)) return

            val lowerBoundTime = sectionLowerBoundTime(sectionX, sectionY, sectionZ)
            if (lowerBoundTime.isNaN()) return

            pendingSections += EntitySectionIntersection(sectionX, sectionY, sectionZ, lowerBoundTime)
        }

        val originSectionY = sectionCoordinate(context.originY)
        val startSectionY = verticalSections?.let { originSectionY.coerceIn(it) } ?: originSectionY
        enqueue(sectionCoordinate(context.originX), startSectionY, sectionCoordinate(context.originZ))

        // Intersections with every ray prefix form a face-connected grid region, so expanding
        // the earliest queued section cannot hide an undiscovered section with an earlier bound.
        while (pendingSections.isNotEmpty()) {
            val section = pendingSections.remove()
            enqueue(section.sectionX - 1, section.sectionY, section.sectionZ)
            enqueue(section.sectionX + 1, section.sectionY, section.sectionZ)
            enqueue(section.sectionX, section.sectionY - 1, section.sectionZ)
            enqueue(section.sectionX, section.sectionY + 1, section.sectionZ)
            enqueue(section.sectionX, section.sectionY, section.sectionZ - 1)
            enqueue(section.sectionX, section.sectionY, section.sectionZ + 1)
            yield(section)
        }
    }.iterator()

    private fun sectionLowerBoundTime(sectionX: Int, sectionY: Int, sectionZ: Int): Double {
        val minimumX = sectionX * SECTION_SIZE - padding.horizontalBlocks
        val minimumY = if (sectionY == verticalSections?.first) Double.NEGATIVE_INFINITY else sectionY * SECTION_SIZE - padding.downwardBlocks
        val minimumZ = sectionZ * SECTION_SIZE - padding.horizontalBlocks
        val maximumX = (sectionX + 1) * SECTION_SIZE + padding.horizontalBlocks
        val maximumY = if (sectionY == verticalSections?.last) Double.POSITIVE_INFINITY else (sectionY + 1) * SECTION_SIZE + padding.upwardBlocks
        val maximumZ = (sectionZ + 1) * SECTION_SIZE + padding.horizontalBlocks
        return intersectTime(context, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
    }

    /**
     * Defines the section geometry and ordering used by the ray traversal. Candidate
     * intersections are compared by their lower-bound ray parameter.
     */
    private companion object {
        const val SECTION_SIZE = 16.0

        val IntersectionOrder = compareBy(EntitySectionIntersection::lowerBoundTime)
            .thenBy(EntitySectionIntersection::sectionX)
            .thenBy(EntitySectionIntersection::sectionY)
            .thenBy(EntitySectionIntersection::sectionZ)

        fun sectionCoordinate(blockCoordinate: Double): Int = floor(blockCoordinate / SECTION_SIZE).toInt()
    }
}

/**
 * A section encountered during a ray query, paired with a lower bound on its ray parameter. The
 * bound orders candidate sections; it is not a confirmed hit on an entity.
 */
data class EntitySectionIntersection(val sectionX: Int, val sectionY: Int, val sectionZ: Int, val lowerBoundTime: Double)

/**
 * Integer coordinates identifying a spatial entity section along the traversal. These are section
 * indices, not individual block coordinates.
 */
private data class EntitySectionPosition(val sectionX: Int, val sectionY: Int, val sectionZ: Int)


/**
 * Expands the host’s section bounds conservatively for entities spanning section boundaries.
 * Distances are non-negative and expressed in blocks.
 */
data class EntitySectionPadding(val horizontalBlocks: Double, val downwardBlocks: Double, val upwardBlocks: Double) {
    init {
        require(horizontalBlocks.isFinite() && horizontalBlocks >= 0.0 && downwardBlocks.isFinite() && downwardBlocks >= 0.0 && upwardBlocks.isFinite() && upwardBlocks >= 0.0) { "Entity section padding must be finite and non-negative" }
    }
}
