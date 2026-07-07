/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.math.BoxView
import heckerpowered.lethal.platform.interop.box
import heckerpowered.lethal.platform.interop.entity
import net.minecraft.entity.Entity
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk
import kotlin.math.floor

object WorldAccessImpl {
    private const val SECTION_SIZE = 16.0

    interface ChunkAccess {
        fun isChunkLoadedForEntitySearch(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean
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
}
