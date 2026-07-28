/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucket
import heckerpowered.bridge.math.*
import heckerpowered.lethal.platform.interop.asClassificationView
import heckerpowered.lethal.platform.interop.asHost
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.Entity
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraft.world.chunk.Chunk
import kotlin.math.floor
import kotlin.math.min

object WorldAccessImpl {
    private const val SECTION_SIZE = 16.0

    @JvmStatic
    fun loadedEntityCount(world: World): Int {
        return world.loadedEntityList.size
    }

    @JvmStatic
    fun entities(world: World): Sequence<EntityAccess> {
        return world.loadedEntityList.asSequence().map(Entity::asView)
    }

    @JvmStatic
    fun getEntities(world: World, searchBox: BoxView): Sequence<EntityAccess> {
        val nativeSearchBox = searchBox.asHost()
        return getEntities(world, nativeSearchBox).map(Entity::asView)
    }

    @JvmStatic
    fun raycastBlockHits(world: World, ray: RayView, distanceBlocks: Double, shape: BlockRaycastShape = BlockRaycastShape.Collision): Sequence<BlockHitResult> {
        require(distanceBlocks.isFinite() && distanceBlocks >= 0.0) { "Raycast distance must be finite and non-negative" }
        val context = RayIntersectionContext.create(ray, distanceBlocks) ?: return emptySequence()
        val start = ray.origin.asHost()
        val end = ray.pointAt(context.maximumTime).asHost()
        if (!start.hasFiniteCoordinates() || !end.hasFiniteCoordinates()) return emptySequence()

        return raycastBlockHits(world, ray, start, end, shape)
    }

    @JvmStatic
    fun destroyBlock(world: World, position: BlockPositionView, dropItems: Boolean): Boolean {
        return world.destroyBlock(position.asHost(), dropItems)
    }

    @JvmStatic
    fun playSound(world: World, position: VectorView, playback: SoundPlayback) {
        world.playSound(null, position.x, position.y, position.z, playback.sound.asHost(), playback.category.asHost(), playback.volume.toFloat(), playback.pitch.toFloat())
    }

    @JvmStatic
    fun spawnParticles(world: World, position: VectorView, effect: ParticleEffect) {
        val particle = effect.particle.asHost()
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

    private fun raycastBlockHits(world: World, ray: RayView, start: Vec3d, end: Vec3d, shape: BlockRaycastShape): Sequence<BlockHitResult> {
        // DDA enters voxels in ray order, matching the host's first-hit traversal. Keep that
        // order lazy so consumers such as targeting can stop without scanning the remaining ray.
        return traverseBlockHits(world, ray, start, end, shape)
    }

    private fun traverseBlockHits(world: World, ray: RayView, start: Vec3d, end: Vec3d, shape: BlockRaycastShape): Sequence<BlockHitResult> = sequence {
        val endBlockX = floor(end.x).toInt()
        val endBlockY = floor(end.y).toInt()
        val endBlockZ = floor(end.z).toInt()
        var currentBlockX = floor(start.x).toInt()
        var currentBlockY = floor(start.y).toInt()
        var currentBlockZ = floor(start.z).toInt()

        val stepX = axisStep(start.x, end.x)
        val stepY = axisStep(start.y, end.y)
        val stepZ = axisStep(start.z, end.z)
        val timePerBlockX = ray.direction.x.timePerBlock()
        val timePerBlockY = ray.direction.y.timePerBlock()
        val timePerBlockZ = ray.direction.z.timePerBlock()
        var nextBoundaryTimeX = nextBoundaryTime(start.x, ray.direction.x, currentBlockX, stepX)
        var nextBoundaryTimeY = nextBoundaryTime(start.y, ray.direction.y, currentBlockY, stepY)
        var nextBoundaryTimeZ = nextBoundaryTime(start.z, ray.direction.z, currentBlockZ, stepZ)

        val initialPosition = BlockPos(currentBlockX, currentBlockY, currentBlockZ)
        raycastBlock(world, initialPosition, start, end, shape, false)?.let { nativeHit ->
            yield(nativeHit.toBlockHitResult(world, ray))
        }

        // Vanilla 1.12.2 stops after roughly 200 voxel boundaries. WorldAccess promises the requested
        // distance, so this traversal ends at the requested end voxel instead of inheriting that limit.
        while (currentBlockX != endBlockX || currentBlockY != endBlockY || currentBlockZ != endBlockZ) {
            val candidateTimeX = if (currentBlockX == endBlockX) Double.POSITIVE_INFINITY else nextBoundaryTimeX
            val candidateTimeY = if (currentBlockY == endBlockY) Double.POSITIVE_INFINITY else nextBoundaryTimeY
            val candidateTimeZ = if (currentBlockZ == endBlockZ) Double.POSITIVE_INFINITY else nextBoundaryTimeZ
            val entryTime = min(candidateTimeX, min(candidateTimeY, candidateTimeZ))
            check(entryTime.isFinite()) { "Block traversal could not reach the ray endpoint" }

            if (candidateTimeX == entryTime) {
                currentBlockX += stepX
                nextBoundaryTimeX += timePerBlockX
            }
            if (candidateTimeY == entryTime) {
                currentBlockY += stepY
                nextBoundaryTimeY += timePerBlockY
            }
            if (candidateTimeZ == entryTime) {
                currentBlockZ += stepZ
                nextBoundaryTimeZ += timePerBlockZ
            }

            val blockPosition = BlockPos(currentBlockX, currentBlockY, currentBlockZ)
            raycastBlock(world, blockPosition, start, end, shape, true)?.let { nativeHit ->
                yield(nativeHit.toBlockHitResult(world, ray))
            }
        }
    }

    private fun axisStep(origin: Double, end: Double): Int {
        return when {
            end > origin -> 1
            end < origin -> -1
            else -> 0
        }
    }

    private fun Double.timePerBlock(): Double {
        return if (this == 0.0) Double.POSITIVE_INFINITY else 1.0 / kotlin.math.abs(this)
    }

    private fun nextBoundaryTime(origin: Double, direction: Double, blockCoordinate: Int, step: Int): Double {
        if (step == 0) return Double.POSITIVE_INFINITY

        val boundary = if (step > 0) blockCoordinate + 1.0 else blockCoordinate.toDouble()
        val time = (boundary - origin) / direction
        return if (time == 0.0) 0.0 else time
    }

    private fun raycastBlock(world: World, position: BlockPos, start: Vec3d, end: Vec3d, shape: BlockRaycastShape, includePortalWithoutCollisionBox: Boolean): NativeBlockHit? {
        val state = world.getBlockState(position)
        if (shape == BlockRaycastShape.Collision) {
            val hasCollisionBox = state.getCollisionBoundingBox(world, position) != Block.NULL_AABB
            if (!hasCollisionBox && (!includePortalWithoutCollisionBox || state.material != Material.PORTAL)) return null
        }

        if (!state.block.canCollideCheck(state, false)) return null
        val result = state.collisionRayTrace(world, position, start, end) ?: return null
        return NativeBlockHit(state, result)
    }

    private fun NativeBlockHit.toBlockHitResult(world: World, ray: RayView): BlockHitResult {
        val point = result.hitVec.asView()
        val time = (point - ray.origin).dot(ray.direction) / ray.direction.lengthSquared
        return BlockHitResult(result.blockPos.asView(), state.asClassificationView(world, result.blockPos), result.sideHit.asView(), point, time)
    }

    private data class NativeBlockHit(
        val state: IBlockState,
        val result: RayTraceResult,
    )

    private fun Vec3d.hasFiniteCoordinates(): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
    }

    private fun getChunks(world: World, searchBox: AxisAlignedBB): Sequence<Chunk> = sequence {
        // Match vanilla 1.12.2's broad-phase entity chunk range. Its inclusive loop can scan one extra
        // chunk on exact 16-block boundaries; precise AABB filtering below removes false positives.
        val minimumChunkX = sectionCoordinate(searchBox.minX - World.MAX_ENTITY_RADIUS)
        val maximumChunkX = sectionCoordinate(searchBox.maxX + World.MAX_ENTITY_RADIUS)
        val minimumChunkZ = sectionCoordinate(searchBox.minZ - World.MAX_ENTITY_RADIUS)
        val maximumChunkZ = sectionCoordinate(searchBox.maxZ + World.MAX_ENTITY_RADIUS)

        for (chunkX in minimumChunkX..maximumChunkX) {
            for (chunkZ in minimumChunkZ..maximumChunkZ) {
                val chunk = world.chunkProvider.getLoadedChunk(chunkX, chunkZ) ?: continue
                yield(chunk)
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
                if (entity.entityBoundingBox.intersects(searchBox)) {
                    yield(entity)
                }

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
        require(length.isFinite() && length >= 0.0) { "Raycast distance must be finite and non-negative" }
        val context = RayIntersectionContext.create(ray, length) ?: return emptySequence()
        return sequence {
            for ((chunkX, sectionY, chunkZ, lowerBoundTime) in EntitySectionRayTraversal(context, World.MAX_ENTITY_RADIUS)) {
                val chunk = world.chunkProvider.getLoadedChunk(chunkX, chunkZ) ?: continue
                val entitySection = chunk.entityLists[sectionY]
                if (entitySection.isEmpty()) continue

                val entities = entitySection.asSequence().map(Entity::asView).asIterable()
                yield(EntityRayBucket(lowerBoundTime, entities))
            }
        }
    }
}
