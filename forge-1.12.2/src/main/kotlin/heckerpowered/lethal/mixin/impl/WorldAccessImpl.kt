/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.math.*
import heckerpowered.lethal.platform.interop.box
import heckerpowered.lethal.platform.interop.entity
import heckerpowered.lethal.platform.interop.particle
import heckerpowered.lethal.platform.interop.soundCategory
import heckerpowered.lethal.platform.interop.soundEvent
import net.minecraft.entity.Entity
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.world.World
import net.minecraft.world.WorldServer
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

    @JvmStatic
    fun playSound(world: World, position: VectorView, playback: SoundPlayback) {
        world.playSound(null, position.x, position.y, position.z, playback.sound.soundEvent(), playback.category.soundCategory(), playback.volume.toFloat(), playback.pitch.toFloat())
    }

    @JvmStatic
    fun spawnParticles(world: World, position: VectorView, effect: ParticleEffect) {
        val particle = effect.particle.particle()
        val particleData = effect.data.toIntArray()

        if (world is WorldServer) {
            world.spawnParticle(particle, effect.longDistance, position.x, position.y, position.z, effect.count, effect.positionSpread.x, effect.positionSpread.y, effect.positionSpread.z, effect.velocitySpread, *particleData)
            return
        }

        repeat(effect.count) {
            val particleX = position.x + world.rand.nextGaussian() * effect.positionSpread.x
            val particleY = position.y + world.rand.nextGaussian() * effect.positionSpread.y
            val particleZ = position.z + world.rand.nextGaussian() * effect.positionSpread.z
            val velocityX = world.rand.nextGaussian() * effect.velocitySpread
            val velocityY = world.rand.nextGaussian() * effect.velocitySpread
            val velocityZ = world.rand.nextGaussian() * effect.velocitySpread
            world.spawnParticle(particle, effect.longDistance, particleX, particleY, particleZ, velocityX, velocityY, velocityZ, *particleData)
        }
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
        val context = RayIntersectionContext.create(ray, length) ?: return emptySequence()

        val searchBox = getRaySearchBox(context)

        val buckets = ArrayList<EntityRayBucket>()

        for (chunk in getChunks(world, searchBox)) {
            addChunkBuckets(buckets, chunk, context, searchBox)
        }

        buckets.sortBy { it.lowerBoundTime }
        return buckets.asSequence()
    }

    private fun getRaySearchBox(context: RayIntersectionContext): AxisAlignedBB {
        val endX = context.originX + context.directionX * context.maximumTime
        val endY = context.originY + context.directionY * context.maximumTime
        val endZ = context.originZ + context.directionZ * context.maximumTime
        val radius = World.MAX_ENTITY_RADIUS

        return AxisAlignedBB(
            min(context.originX, endX) - radius,
            min(context.originY, endY) - radius,
            min(context.originZ, endZ) - radius,
            max(context.originX, endX) + radius,
            max(context.originY, endY) + radius,
            max(context.originZ, endZ) + radius,
        )
    }


    private fun addChunkBuckets(buckets: MutableList<EntityRayBucket>, chunk: Chunk, context: RayIntersectionContext, searchBox: AxisAlignedBB) {
        val entitySections = chunk.entityLists

        val minimumSectionIndex = floor(searchBox.minY / SECTION_SIZE).toInt()
            .coerceIn(0, entitySections.lastIndex)
        val maximumSectionIndex = floor(searchBox.maxY / SECTION_SIZE).toInt()
            .coerceIn(0, entitySections.lastIndex)

        for (sectionIndex in minimumSectionIndex..maximumSectionIndex) {
            val entitySection = entitySections[sectionIndex]
            if (entitySection.isEmpty()) continue

            val lowerBoundTime = getSectionLowerBoundTime(chunk, sectionIndex, context) ?: continue

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

    private fun getSectionLowerBoundTime(chunk: Chunk, sectionIndex: Int, context: RayIntersectionContext): Double? {
        val chunkX = chunk.x
        val chunkZ = chunk.z
        val radius = World.MAX_ENTITY_RADIUS

        val minimumX = chunkX * 16.0 - radius
        val minimumY = sectionIndex * 16.0 - radius
        val minimumZ = chunkZ * 16.0 - radius

        val maximumX = (chunkX + 1) * 16.0 + radius
        val maximumY = (sectionIndex + 1) * 16.0 + radius
        val maximumZ = (chunkZ + 1) * 16.0 + radius

        return intersectSectionBounds(context, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
    }

    private fun intersectSectionBounds(context: RayIntersectionContext, minimumX: Double, minimumY: Double, minimumZ: Double, maximumX: Double, maximumY: Double, maximumZ: Double): Double? {
        val hitTime = intersectTime(context, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
        return if (hitTime.isNaN()) null else hitTime
    }
}
