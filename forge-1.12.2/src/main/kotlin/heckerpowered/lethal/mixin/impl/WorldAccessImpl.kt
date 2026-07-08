/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.lethal.bridge.math.*
import heckerpowered.lethal.platform.interop.box
import heckerpowered.lethal.platform.interop.entity
import net.minecraft.entity.Entity
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object WorldAccessImpl {
    private const val SECTION_SIZE = 16.0

    interface ChunkAccess {
        fun isChunkLoadedForEntitySearch(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean
    }

    @JvmStatic
    fun loadedEntityCount(world: World): Int {
        return world.loadedEntityList.size
    }

    @JvmStatic
    fun entities(world: World): Sequence<EntityAccess> {
        return world.loadedEntityList.asSequence().map { it.entity() }
    }

    @JvmStatic
    fun getEntities(world: World, searchBox: BoxView): Sequence<EntityAccess> {
        val nativeSearchBox = searchBox.box()
        return getEntities(world, nativeSearchBox).map { it.entity() }
    }

    private fun getEntities(world: World, searchBox: AxisAlignedBB): Sequence<Entity> {
        return getChunks(world, searchBox).flatMap { getEntities(it, searchBox) }
    }

    private fun getChunks(world: World, searchBox: AxisAlignedBB): Sequence<Chunk> = sequence {
        val chunkAccess = world as? ChunkAccess ?: error("World does not expose chunk access")
        /*
         * Matches vanilla 1.12.2 broad-phase entity chunk range.
         * Vanilla uses floor(max + MAX_ENTITY_RADIUS) and an inclusive loop, which can scan one extra chunk on exact 16-block boundaries.
         * Keep this conservative range for behavioral parity; precise AABB filtering below prevents false positives.
         */
        val minimumChunkX = sectionCoordinate(searchBox.minX - World.MAX_ENTITY_RADIUS)
        val maximumChunkX = sectionCoordinate(searchBox.maxX + World.MAX_ENTITY_RADIUS)
        val minimumChunkZ = sectionCoordinate(searchBox.minZ - World.MAX_ENTITY_RADIUS)
        val maximumChunkZ = sectionCoordinate(searchBox.maxZ + World.MAX_ENTITY_RADIUS)

        for (chunkX in minimumChunkX..maximumChunkX) {
            for (chunkZ in minimumChunkZ..maximumChunkZ) {
                if (chunkAccess.isChunkLoadedForEntitySearch(chunkX, chunkZ, true)) {
                    yield(world.getChunk(chunkX, chunkZ))
                }
            }
        }
    }

    private fun getEntities(chunk: Chunk, searchBox: AxisAlignedBB): Sequence<Entity> = sequence {
        val entitySections = chunk.entityLists
        val minimumSectionIndex = sectionCoordinate(searchBox.minY - World.MAX_ENTITY_RADIUS).coerceIn(0, entitySections.lastIndex)
        val maximumSectionIndex = sectionCoordinate(searchBox.maxY + World.MAX_ENTITY_RADIUS).coerceIn(0, entitySections.lastIndex)

        for (sectionIndex in minimumSectionIndex..maximumSectionIndex) {
            val entitySection = entitySections[sectionIndex]
            if (entitySection.isEmpty()) continue

            for (entity in entitySection) {
                if (!entity.entityBoundingBox.intersects(searchBox)) continue

                yield(entity)

                val parts = entity.parts ?: continue
                for (part in parts) {
                    if (part.entityBoundingBox.intersects(searchBox)) {
                        yield(part)
                    }
                }
            }
        }
    }

    private fun sectionCoordinate(blockCoordinate: Double): Int {
        return floor(blockCoordinate / SECTION_SIZE).toInt()
    }

    @JvmStatic
    fun getEntityRayBuckets(world: World, ray: RayView, length: Double): Sequence<EntityRayBucket> {
        val directionLength = ray.direction.length
        if (directionLength.isNearlyZero()) return emptySequence()

        val maxTime = length / directionLength
        val searchBox = getRaySearchBox(ray, maxTime)

        val buckets = ArrayList<EntityRayBucket>()

        for (chunk in getChunks(world, searchBox)) {
            addChunkBuckets(buckets, chunk, ray, length, searchBox)
        }

        buckets.sortBy { it.lowerBoundTime }
        return buckets.asSequence()
    }

    private fun getRaySearchBox(ray: RayView, maxTime: Double): AxisAlignedBB {
        val origin = ray.origin
        val end = ray.pointAt(maxTime)
        val radius = World.MAX_ENTITY_RADIUS

        return AxisAlignedBB(
            min(origin.x, end.x) - radius,
            min(origin.y, end.y) - radius,
            min(origin.z, end.z) - radius,
            max(origin.x, end.x) + radius,
            max(origin.y, end.y) + radius,
            max(origin.z, end.z) + radius,
        )
    }


    private fun addChunkBuckets(buckets: MutableList<EntityRayBucket>, chunk: Chunk, ray: RayView, length: Double, searchBox: AxisAlignedBB) {
        val entitySections = chunk.entityLists

        val minimumSectionIndex = floor(searchBox.minY / SECTION_SIZE).toInt()
            .coerceIn(0, entitySections.lastIndex)
        val maximumSectionIndex = floor(searchBox.maxY / SECTION_SIZE).toInt()
            .coerceIn(0, entitySections.lastIndex)

        for (sectionIndex in minimumSectionIndex..maximumSectionIndex) {
            val entitySection = entitySections[sectionIndex]
            if (entitySection.isEmpty()) continue

            val lowerBoundTime = getSectionLowerBoundTime(chunk, sectionIndex, ray, length) ?: continue

            buckets += EntityRayBucket(lowerBoundTime, entitySectionEntities(entitySection).asIterable())
        }
    }

    private fun entitySectionEntities(entitySection: Iterable<Entity>): Sequence<EntityAccess> = sequence {
        for (entity in entitySection) {
            yield(entity.entity())

            val parts = entity.parts
            if (parts != null) {
                for (part in parts) {
                    yield(part.entity())
                }
            }
        }
    }

    private fun getSectionLowerBoundTime(chunk: Chunk, sectionIndex: Int, ray: RayView, length: Double): Double? {
        val chunkX = chunk.x
        val chunkZ = chunk.z
        val radius = World.MAX_ENTITY_RADIUS

        val minimumX = chunkX * 16.0 - radius
        val minimumY = sectionIndex * 16.0 - radius
        val minimumZ = chunkZ * 16.0 - radius

        val maximumX = (chunkX + 1) * 16.0 + radius
        val maximumY = (sectionIndex + 1) * 16.0 + radius
        val maximumZ = (chunkZ + 1) * 16.0 + radius

        return intersectSectionBounds(ray, length, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
    }

    private fun intersectSectionBounds(ray: RayView, length: Double, minimumX: Double, minimumY: Double, minimumZ: Double, maximumX: Double, maximumY: Double, maximumZ: Double): Double? {
        val box = Geometry.box(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
        return box.intersect(ray, length)?.nearestHitTime
    }
}
