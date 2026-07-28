/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.math.RayIntersectionContext
import heckerpowered.bridge.math.intersectTime
import java.util.PriorityQueue
import kotlin.math.floor
import kotlin.math.max

/**
 * Enumerates Minecraft 1.21.1 entity storage sections by conservative ray entry time.
 */
internal class EntitySectionRayTraversal(private val context: RayIntersectionContext, maximumEntityRadius: Double) : Sequence<EntitySectionIntersection> {
    init {
        require(maximumEntityRadius.isFinite() && maximumEntityRadius >= 0.0) { "Maximum entity radius must be finite and non-negative" }
    }

    private val padding = EntitySectionPadding(max(maximumEntityRadius, VANILLA_HORIZONTAL_PADDING), maximumEntityRadius, max(maximumEntityRadius, VANILLA_UPWARD_PADDING))

    override fun iterator(): Iterator<EntitySectionIntersection> = sequence {
        val pendingSections = PriorityQueue(IntersectionOrder)
        val visitedSections = HashSet<EntitySectionPosition>()

        fun enqueue(sectionX: Int, sectionY: Int, sectionZ: Int) {
            val position = EntitySectionPosition(sectionX, sectionY, sectionZ)
            if (!visitedSections.add(position)) return

            val lowerBoundTime = sectionLowerBoundTime(sectionX, sectionY, sectionZ)
            if (lowerBoundTime.isNaN()) return

            pendingSections += EntitySectionIntersection(sectionX, sectionY, sectionZ, lowerBoundTime)
        }

        enqueue(sectionCoordinate(context.originX), sectionCoordinate(context.originY), sectionCoordinate(context.originZ))

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
        val minimumY = sectionY * SECTION_SIZE - padding.downwardBlocks
        val minimumZ = sectionZ * SECTION_SIZE - padding.horizontalBlocks
        val maximumX = (sectionX + 1) * SECTION_SIZE + padding.horizontalBlocks
        val maximumY = (sectionY + 1) * SECTION_SIZE + padding.upwardBlocks
        val maximumZ = (sectionZ + 1) * SECTION_SIZE + padding.horizontalBlocks
        return intersectTime(context, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
    }

    private companion object {
        const val SECTION_SIZE = 16.0
        const val VANILLA_HORIZONTAL_PADDING = 2.0
        const val VANILLA_UPWARD_PADDING = 4.0
        val IntersectionOrder = compareBy(EntitySectionIntersection::lowerBoundTime)
            .thenBy(EntitySectionIntersection::sectionX)
            .thenBy(EntitySectionIntersection::sectionY)
            .thenBy(EntitySectionIntersection::sectionZ)

        fun sectionCoordinate(blockCoordinate: Double): Int = floor(blockCoordinate / SECTION_SIZE).toInt()
    }
}

internal data class EntitySectionIntersection(
    val sectionX: Int,
    val sectionY: Int,
    val sectionZ: Int,
    val lowerBoundTime: Double,
)

private data class EntitySectionPosition(
    val sectionX: Int,
    val sectionY: Int,
    val sectionZ: Int,
)

private data class EntitySectionPadding(
    val horizontalBlocks: Double,
    val downwardBlocks: Double,
    val upwardBlocks: Double,
)
