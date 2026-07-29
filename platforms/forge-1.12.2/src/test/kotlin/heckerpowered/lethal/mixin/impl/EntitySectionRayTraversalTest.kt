/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayIntersectionContext
import heckerpowered.bridge.math.intersectTime
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.init.Bootstrap
import net.minecraft.profiler.Profiler
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.GameType
import net.minecraft.world.World
import net.minecraft.world.WorldProviderSurface
import net.minecraft.world.WorldSettings
import net.minecraft.world.WorldType
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.chunk.IChunkProvider
import net.minecraft.world.storage.SaveHandlerMP
import net.minecraft.world.storage.WorldInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class EntitySectionRayTraversalTest {
    @Test
    fun traversalMatchesFiniteSectionScanInEveryRayDirection() {
        val rays = listOf(
            Geometry.ray(Geometry.vector(8.0, 24.0, 8.0), Geometry.vector(1.0, 0.25, 0.5)),
            Geometry.ray(Geometry.vector(40.0, 40.0, 40.0), Geometry.vector(-1.0, -0.5, -0.25)),
            Geometry.ray(Geometry.vector(16.0, 16.0, 16.0), Geometry.vector(-1.0, -1.0, 1.0)),
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
    fun verticalBoundarySectionsCoverClampedEntityStorage() {
        val belowWorld = requireNotNull(RayIntersectionContext.create(Geometry.ray(Geometry.vector(8.0, -100.0, 8.0), Geometry.vector(1.0, 0.0, 0.0)), 32.0))
        val aboveWorld = requireNotNull(RayIntersectionContext.create(Geometry.ray(Geometry.vector(8.0, 300.0, 8.0), Geometry.vector(1.0, 0.0, 0.0)), 32.0))

        assertEquals(expected = EntitySectionIntersection(0, 0, 0, 0.0), actual = EntitySectionRayTraversal(belowWorld, 2.0).first())
        assertEquals(expected = EntitySectionIntersection(0, 15, 0, 0.0), actual = EntitySectionRayTraversal(aboveWorld, 2.0).first())
    }

    @Test
    fun firstBucketDoesNotQueryTheRemainingRay() {
        Bootstrap.register()
        val world = EntitySearchWorld()
        val ray = Geometry.ray(Geometry.vector(8.0, 8.0, 8.0), Geometry.vector(1.0, 0.0, 0.0))
        val buckets = WorldAccessImpl.getEntityRayBuckets(world, ray, 1024.0)

        assertEquals(expected = emptyList(), actual = world.loadedChunkQueries)

        val bucket = buckets.first()

        assertEquals(expected = 0.0, actual = bucket.lowerBoundTime)
        assertEquals(expected = listOf(ChunkPos.asLong(0, 0)), actual = world.loadedChunkQueries)
    }

    private fun scanSections(context: RayIntersectionContext, radius: Double): List<EntitySectionIntersection> {
        val intersections = mutableListOf<EntitySectionIntersection>()
        for (chunkX in -8..8) {
            for (sectionY in 0..15) {
                for (chunkZ in -8..8) {
                    val lowerBoundTime = sectionLowerBoundTime(context, radius, chunkX, sectionY, chunkZ)
                    if (!lowerBoundTime.isNaN()) intersections += EntitySectionIntersection(chunkX, sectionY, chunkZ, lowerBoundTime)
                }
            }
        }
        return intersections.sortedWith(compareBy(EntitySectionIntersection::lowerBoundTime).thenBy(EntitySectionIntersection::chunkX).thenBy(EntitySectionIntersection::sectionY).thenBy(EntitySectionIntersection::chunkZ))
    }

    private fun sectionLowerBoundTime(context: RayIntersectionContext, radius: Double, chunkX: Int, sectionY: Int, chunkZ: Int): Double {
        val minimumX = chunkX * 16.0 - radius
        val minimumY = if (sectionY == 0) Double.NEGATIVE_INFINITY else sectionY * 16.0 - radius
        val minimumZ = chunkZ * 16.0 - radius
        val maximumX = (chunkX + 1) * 16.0 + radius
        val maximumY = if (sectionY == 15) Double.POSITIVE_INFINITY else (sectionY + 1) * 16.0 + radius
        val maximumZ = (chunkZ + 1) * 16.0 + radius
        return intersectTime(context, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
    }

    private class EntitySearchWorld : World(SaveHandlerMP(), WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"), WorldProviderSurface(), Profiler(), false) {
        private val recordingChunkProvider = RecordingChunkProvider()

        val loadedChunkQueries: List<Long>
            get() = recordingChunkProvider.loadedChunkQueries

        init {
            chunkProvider = recordingChunkProvider
            recordingChunkProvider.add(Chunk(this, 0, 0).apply { addEntity(EntityXPOrb(this@EntitySearchWorld, 8.0, 8.0, 8.0, 1)) })
            recordingChunkProvider.add(Chunk(this, 32, 0).apply { addEntity(EntityXPOrb(this@EntitySearchWorld, 520.0, 8.0, 8.0, 1)) })
        }

        override fun createChunkProvider(): IChunkProvider = recordingChunkProvider

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return chunkProvider.getLoadedChunk(chunkX, chunkZ) != null
        }
    }

    private class RecordingChunkProvider : IChunkProvider {
        private val chunks = mutableMapOf<Long, Chunk>()
        val loadedChunkQueries = mutableListOf<Long>()

        fun add(chunk: Chunk) {
            chunks[ChunkPos.asLong(chunk.x, chunk.z)] = chunk
        }

        override fun getLoadedChunk(chunkX: Int, chunkZ: Int): Chunk? {
            val position = ChunkPos.asLong(chunkX, chunkZ)
            loadedChunkQueries += position
            return chunks[position]
        }

        override fun provideChunk(chunkX: Int, chunkZ: Int): Chunk {
            return getLoadedChunk(chunkX, chunkZ) ?: error("Chunk $chunkX, $chunkZ is not loaded")
        }

        override fun tick() = false

        override fun makeString() = "RecordingChunkProvider"

        override fun isChunkGeneratedAt(chunkX: Int, chunkZ: Int) = ChunkPos.asLong(chunkX, chunkZ) in chunks
    }
}
