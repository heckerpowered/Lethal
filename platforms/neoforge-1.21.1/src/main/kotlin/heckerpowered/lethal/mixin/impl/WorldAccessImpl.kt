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
import heckerpowered.lethal.platform.interop.asHost
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.entity.EntitySection
import net.minecraft.world.level.entity.EntitySectionStorage
import net.minecraft.world.level.entity.LevelEntityGetter
import net.minecraft.world.level.entity.LevelEntityGetterAdapter
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

object WorldAccessImpl {
    private const val NEAR_ZERO = 1.0E-8

    @JvmStatic
    fun loadedEntityCount(entities: LevelEntityGetter<Entity>) = entities.requireAdapter().visibleEntities.count()

    @JvmStatic
    fun entities(level: Level, entities: LevelEntityGetter<Entity>): Sequence<EntityAccess> {
        return (entities.all.asSequence() + level.partEntities.asSequence()).map(Entity::asView)
    }

    @JvmStatic
    fun getEntities(level: Level, entities: LevelEntityGetter<Entity>, searchBox: BoxView): Sequence<EntityAccess> {
        val nativeSearchBox = searchBox.asHost()
        val sectionEntities = entities.requireAdapter().sectionStorage
            .getAccessibleNonEmptySections(nativeSearchBox)
            .flatMap { it.section.storage.asSequence() }
            .filter { nativeSearchBox.intersects(it.boundingBox) }
        val partEntities = level.partEntities
            .asSequence()
            .filter { nativeSearchBox.intersects(it.boundingBox) }

        return (sectionEntities + partEntities).map(Entity::asView)
    }

    @JvmStatic
    fun getEntityRayBuckets(level: Level, entities: LevelEntityGetter<Entity>, ray: RayView, length: Double): Sequence<EntityRayBucket> {
        requireRayLength(length)
        val segment = createRaySegment(ray, length) ?: return emptySequence()
        val context = RayIntersectionContext.create(ray, length) ?: return emptySequence()
        val radius = level.maxEntityRadius
        val searchBox = AABB(segment.start, segment.end).inflate(radius)
        val sectionStorage = entities.requireAdapter().sectionStorage
        val partEntities = level.partEntities

        return sequence {
            if (partEntities.isNotEmpty()) {
                val candidates = partEntities.asSequence().filter { searchBox.intersects(it.boundingBox) }.map(Entity::asView).asIterable()
                yield(EntityRayBucket(0.0, candidates))
            }

            for ((sectionX, sectionY, sectionZ, lowerBoundTime) in EntitySectionRayTraversal(context, radius)) {
                val identifier = SectionPos.asLong(sectionX, sectionY, sectionZ)
                val section = sectionStorage.getSection(identifier) ?: continue
                if (section.isEmpty || !section.status.isAccessible) continue

                val candidates = section.storage.asSequence().map(Entity::asView).asIterable()
                yield(EntityRayBucket(lowerBoundTime, candidates))
            }
        }
    }

    @JvmStatic
    fun raycastBlockHits(level: Level, ray: RayView, distanceBlocks: Double, shape: BlockRaycastShape): Sequence<BlockHitResult> {
        requireRayLength(distanceBlocks)
        return Sequence {
            val segment = createRaySegment(ray, distanceBlocks)
            if (segment == null) emptyList<BlockHitResult>().iterator() else BlockHitIterator(level, ray, segment, shape)
        }
    }

    @JvmStatic
    fun destroyBlock(level: Level, position: BlockPositionView, dropItems: Boolean): Boolean {
        return level.destroyBlock(position.asHost(), dropItems)
    }

    @JvmStatic
    fun playSound(level: Level, position: VectorView, playback: SoundPlayback) {
        val identifier = playback.sound.identifier
        val location = identifier.asHost()
        val sound = BuiltInRegistries.SOUND_EVENT.getOptional(location).orElseThrow { IllegalArgumentException("Unregistered sound event: ${identifier.asString()}") }
        level.playSound(null, position.x, position.y, position.z, sound, playback.category.asHost(), playback.volume.toFloat(), playback.pitch.toFloat())
    }

    @JvmStatic
    fun spawnParticles(level: Level, position: VectorView, effect: ParticleEffect) {
        val particle = effect.asHost()
        if (level is ServerLevel) {
            if (effect.longDistance) {
                for (player in level.players()) {
                    level.sendParticles(player, particle, true, position.x, position.y, position.z, effect.count, effect.positionSpread.x, effect.positionSpread.y, effect.positionSpread.z, effect.velocitySpread)
                }
            } else {
                level.sendParticles(particle, position.x, position.y, position.z, effect.count, effect.positionSpread.x, effect.positionSpread.y, effect.positionSpread.z, effect.velocitySpread)
            }
            return
        }

        repeat(effect.count) {
            val particleX = position.x + level.random.nextGaussian() * effect.positionSpread.x
            val particleY = position.y + level.random.nextGaussian() * effect.positionSpread.y
            val particleZ = position.z + level.random.nextGaussian() * effect.positionSpread.z
            val velocityX = level.random.nextGaussian() * effect.velocitySpread
            val velocityY = level.random.nextGaussian() * effect.velocitySpread
            val velocityZ = level.random.nextGaussian() * effect.velocitySpread
            level.addParticle(particle, effect.longDistance, particleX, particleY, particleZ, velocityX, velocityY, velocityZ)
        }
    }

    private fun requireRayLength(length: Double) {
        require(length.isFinite() && length >= 0.0) { "Raycast distance must be finite and non-negative" }
    }

    private fun createRaySegment(ray: RayView, distanceBlocks: Double): RaySegment? {
        val origin = ray.origin
        val direction = ray.direction
        val directionLengthSquared = direction.lengthSquared
        if (!directionLengthSquared.isFinite() || directionLengthSquared <= NEAR_ZERO * NEAR_ZERO) return null
        if (!origin.isFinite() || !direction.isFinite()) return null

        val maximumTime = distanceBlocks / sqrt(directionLengthSquared)
        val start = Vec3(origin.x, origin.y, origin.z)
        val endX = origin.x + direction.x * maximumTime
        val endY = origin.y + direction.y * maximumTime
        val endZ = origin.z + direction.z * maximumTime
        val end = Vec3(endX, endY, endZ)
        if (!end.isFinite()) return null
        return RaySegment(start, end)
    }

    private fun Vec3.isFinite(): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
    }

    @Suppress("UNCHECKED_CAST")
    private fun LevelEntityGetter<Entity>.requireAdapter(): LevelEntityGetterAdapter<Entity> {
        return this as? LevelEntityGetterAdapter<Entity> ?: error("Level entity getter ${javaClass.name} does not expose the 1.21.1 entity index")
    }

    private fun EntitySectionStorage<Entity>.getAccessibleNonEmptySections(searchBox: AABB): Sequence<EntitySectionEntry> = sequence {
        // These asymmetric margins are part of the host's 1.21.1 broad-phase section search.
        val minimumSectionX = SectionPos.posToSectionCoord(searchBox.minX - 2.0)
        val minimumSectionY = SectionPos.posToSectionCoord(searchBox.minY - 4.0)
        val minimumSectionZ = SectionPos.posToSectionCoord(searchBox.minZ - 2.0)
        val maximumSectionX = SectionPos.posToSectionCoord(searchBox.maxX + 2.0)
        val maximumSectionY = SectionPos.posToSectionCoord(searchBox.maxY)
        val maximumSectionZ = SectionPos.posToSectionCoord(searchBox.maxZ + 2.0)

        for (sectionX in minimumSectionX..maximumSectionX) {
            // At a fixed x, these packed keys cover every y/z bit pattern.
            val minimumSectionKey = SectionPos.asLong(sectionX, 0, 0)
            val maximumSectionKey = SectionPos.asLong(sectionX, -1, -1)
            val sectionKeys = sectionIds.subSet(minimumSectionKey, maximumSectionKey + 1L).iterator()

            while (sectionKeys.hasNext()) {
                val sectionKey = sectionKeys.nextLong()
                val sectionY = SectionPos.y(sectionKey)
                val sectionZ = SectionPos.z(sectionKey)
                if (sectionY !in minimumSectionY..maximumSectionY || sectionZ !in minimumSectionZ..maximumSectionZ) continue

                val section = getSection(sectionKey) ?: continue
                if (section.isEmpty || !section.status.isAccessible) continue

                yield(EntitySectionEntry(sectionKey, section))
            }
        }
    }

    private data class EntitySectionEntry(
        val identifier: Long,
        val section: EntitySection<Entity>,
    )

    private class RaySegment(val start: Vec3, val end: Vec3)

    private class BlockHitIterator(private val level: Level, private val ray: RayView, segment: RaySegment, private val shape: BlockRaycastShape) : Iterator<BlockHitResult> {
        private val start = segment.start
        private val end = segment.end
        private val position = BlockPos.MutableBlockPos()
        private val endBlockX = floorToInt(end.x)
        private val endBlockY = floorToInt(end.y)
        private val endBlockZ = floorToInt(end.z)
        private val stepX: Int
        private val stepY: Int
        private val stepZ: Int
        private val timePerBlockX = timePerBlock(ray.direction.x)
        private val timePerBlockY = timePerBlock(ray.direction.y)
        private val timePerBlockZ = timePerBlock(ray.direction.z)
        private var blockX = floorToInt(start.x)
        private var blockY = floorToInt(start.y)
        private var blockZ = floorToInt(start.z)
        private var nextBoundaryTimeX: Double
        private var nextBoundaryTimeY: Double
        private var nextBoundaryTimeZ: Double
        private var isFirstBlock = true
        private var exhausted = false
        private var nextHit: BlockHitResult? = null

        init {
            stepX = endBlockX.compareTo(blockX)
            stepY = endBlockY.compareTo(blockY)
            stepZ = endBlockZ.compareTo(blockZ)
            nextBoundaryTimeX = nextBoundaryTime(start.x, ray.direction.x, blockX, stepX)
            nextBoundaryTimeY = nextBoundaryTime(start.y, ray.direction.y, blockY, stepY)
            nextBoundaryTimeZ = nextBoundaryTime(start.z, ray.direction.z, blockZ, stepZ)
        }

        override fun hasNext(): Boolean {
            if (nextHit == null && !exhausted) advance()
            return nextHit != null
        }

        override fun next(): BlockHitResult {
            if (!hasNext()) throw NoSuchElementException()
            val result = requireNotNull(nextHit)
            nextHit = null
            return result
        }

        private fun advance() {
            while (!exhausted) {
                if (isFirstBlock) {
                    isFirstBlock = false
                } else if (!stepToNextBlock()) {
                    exhausted = true
                    return
                }

                nextHit = hitCurrentBlock()
                if (nextHit != null) return
                if (isAtEnd()) exhausted = true
            }
        }

        private fun stepToNextBlock(): Boolean {
            if (isAtEnd()) return false

            val candidateX = if (blockX == endBlockX) Double.POSITIVE_INFINITY else nextBoundaryTimeX
            val candidateY = if (blockY == endBlockY) Double.POSITIVE_INFINITY else nextBoundaryTimeY
            val candidateZ = if (blockZ == endBlockZ) Double.POSITIVE_INFINITY else nextBoundaryTimeZ
            val entryTime = min(candidateX, min(candidateY, candidateZ))
            check(entryTime.isFinite()) { "Block traversal could not reach the ray endpoint" }

            if (candidateX == entryTime) {
                blockX += stepX
                nextBoundaryTimeX += timePerBlockX
            }
            if (candidateY == entryTime) {
                blockY += stepY
                nextBoundaryTimeY += timePerBlockY
            }
            if (candidateZ == entryTime) {
                blockZ += stepZ
                nextBoundaryTimeZ += timePerBlockZ
            }
            return true
        }

        private fun isAtEnd(): Boolean {
            return blockX == endBlockX && blockY == endBlockY && blockZ == endBlockZ
        }

        private fun hitCurrentBlock(): BlockHitResult? {
            position.set(blockX, blockY, blockZ)
            val state = level.getBlockState(position)
            var nativeShape = shape(state)
            if (nativeShape.isEmpty && shape == BlockRaycastShape.Collision && state.`is`(Blocks.NETHER_PORTAL)) nativeShape = state.getShape(level, position, CollisionContext.empty())
            if (nativeShape.isEmpty) return null

            val nativeHit = level.clipWithInteractionOverride(start, end, position, nativeShape, state) ?: return null
            val point = nativeHit.location
            val direction = ray.direction
            val origin = ray.origin

            val offsetX = point.x - origin.x
            val offsetY = point.y - origin.y
            val offsetZ = point.z - origin.z

            val time = (offsetX * direction.x + offsetY * direction.y + offsetZ * direction.z) / direction.lengthSquared

            val blockPosition = position.immutable().asView()
            val blockState = state.asView()
            val face = nativeHit.direction.asView()
            val hitPosition = point.asView()
            return BlockHitResult(blockPosition, blockState, face, hitPosition, time)
        }

        private fun shape(state: BlockState): VoxelShape {
            return when (shape) {
                BlockRaycastShape.Collision -> state.getCollisionShape(level, position, CollisionContext.empty())
                BlockRaycastShape.Outline -> state.getShape(level, position, CollisionContext.empty())
            }
        }

        private fun floorToInt(value: Double): Int {
            val integer = value.toInt()
            return if (value < integer) integer - 1 else integer
        }

        private fun timePerBlock(direction: Double): Double {
            return if (direction == 0.0) Double.POSITIVE_INFINITY else 1.0 / abs(direction)
        }

        private fun nextBoundaryTime(origin: Double, direction: Double, blockCoordinate: Int, step: Int): Double {
            if (step == 0) return Double.POSITIVE_INFINITY
            val boundary = if (step > 0) blockCoordinate + 1.0 else blockCoordinate.toDouble()
            val time = (boundary - origin) / direction
            return if (time == 0.0) 0.0 else time
        }

    }
}
