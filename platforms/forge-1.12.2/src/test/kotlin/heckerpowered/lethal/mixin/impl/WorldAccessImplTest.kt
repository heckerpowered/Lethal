/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositions
import heckerpowered.bridge.math.Geometry
import heckerpowered.lethal.platform.interop.asHost
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.init.Bootstrap
import net.minecraft.profiler.Profiler
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameType
import net.minecraft.world.World
import net.minecraft.world.WorldProviderSurface
import net.minecraft.world.WorldSettings
import net.minecraft.world.WorldType
import net.minecraft.world.chunk.IChunkProvider
import net.minecraft.world.storage.SaveHandlerMP
import net.minecraft.world.storage.WorldInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldAccessImplTest {
    @Test
    fun blockRaycastReturnsEveryCollisionInOrder() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(0, 0, 2) to Blocks.STONE.defaultState, BlockPos(0, 0, 4) to Blocks.GLASS.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 0.5), Geometry.vector(0.0, 0.0, 2.0))

        val hits = WorldAccessImpl.raycastBlockHits(world, ray, 6.0).toList()

        assertEquals(expected = listOf(2, 4), actual = hits.map { it.blockPosition.z })
        assertEquals(expected = listOf("minecraft:stone", "minecraft:glass"), actual = hits.map { it.blockState.asHost().block.registryName.toString() })
        assertEquals(expected = listOf(BlockDirection.North, BlockDirection.North), actual = hits.map(BlockHitResult::face))
        assertEquals(expected = 0.75, actual = hits[0].time, absoluteTolerance = 1.0E-9)
        assertEquals(expected = 1.75, actual = hits[1].time, absoluteTolerance = 1.0E-9)

        val nativeHit = assertNotNull(world.rayTraceBlocks(Vec3d(0.5, 0.5, 0.5), Vec3d(0.5, 0.5, 6.5), false, true, false))
        assertEquals(expected = nativeHit.blockPos.z, actual = hits.first().blockPosition.z)
        assertEquals(expected = nativeHit.hitVec.z, actual = hits.first().point.z, absoluteTolerance = 1.0E-9)
    }

    @Test
    fun blockRaycastIgnoresLiquidsAndBlocksWithoutCollisionBoxes() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(0, 0, 2) to Blocks.WATER.defaultState, BlockPos(0, 0, 3) to Blocks.TALLGRASS.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 0.5), Geometry.vector(0.0, 0.0, 1.0))

        assertNull(WorldAccessImpl.raycastBlockHits(world, ray, 4.0).firstOrNull())
    }

    @Test
    fun outlineBlockRaycastIncludesBlocksWithoutCollisionBoxes() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(0, 0, 3) to Blocks.TALLGRASS.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 0.5), Geometry.vector(0.0, 0.0, 1.0))

        val hit = WorldAccessImpl.raycastBlockHits(world, ray, 4.0, BlockRaycastShape.Outline).single()

        assertEquals(expected = 3, actual = hit.blockPosition.z)
    }

    @Test
    fun blockRaycastStopsTraversingAfterTheFirstConsumedHit() {
        Bootstrap.register()
        val world = object : TestWorld(mapOf(BlockPos(0, 0, 2) to Blocks.STONE.defaultState)) {
            override fun getBlockState(position: BlockPos): IBlockState {
                check(position.z <= 2) { "Raycast traversed beyond its first consumed hit" }
                return super.getBlockState(position)
            }
        }
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 0.5), Geometry.vector(0.0, 0.0, 1.0))

        val hit = WorldAccessImpl.raycastBlockHits(world, ray, 512.0).first()

        assertEquals(expected = 2, actual = hit.blockPosition.z)
    }

    @Test
    fun blockRaycastSupportsNegativeNonUnitDirections() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(0, 0, 3) to Blocks.STONE.defaultState, BlockPos(0, 0, 1) to Blocks.STONE.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 5.5), Geometry.vector(0.0, 0.0, -2.0))

        val hits = WorldAccessImpl.raycastBlockHits(world, ray, 6.0).toList()

        assertEquals(expected = listOf(3, 1), actual = hits.map { it.blockPosition.z })
        assertEquals(expected = listOf(BlockDirection.South, BlockDirection.South), actual = hits.map(BlockHitResult::face))
        assertEquals(expected = 0.75, actual = hits[0].time, absoluteTolerance = 1.0E-9)
        assertEquals(expected = 1.75, actual = hits[1].time, absoluteTolerance = 1.0E-9)
    }

    @Test
    fun blockRaycastDoesNotVisitVoxelsOutsideAForwardCornerRay() {
        Bootstrap.register()
        val forwardBlock = BlockPos(-1, -1, 0)
        val offRayBlock = BlockPos(0, -1, -1)
        val world = TestWorld(mapOf(forwardBlock to Blocks.STONE.defaultState, offRayBlock to Blocks.STONE.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.0, 0.0, 0.0), Geometry.vector(-1.0, -1.0, 1.0))

        val hits = WorldAccessImpl.raycastBlockHits(world, ray, 3.0).toList()

        assertTrue(hits.any { hit -> hit.blockPosition.x == -1 && hit.blockPosition.y == -1 && hit.blockPosition.z == 0 })
        assertTrue(hits.none { hit -> hit.blockPosition.x == 0 && hit.blockPosition.y == -1 && hit.blockPosition.z == -1 })
        assertTrue(hits.all { hit -> hit.time >= 0.0 })
        assertEquals(expected = hits.map(BlockHitResult::time).sorted(), actual = hits.map(BlockHitResult::time))
        assertEquals(expected = hits.map(BlockHitResult::blockPosition).distinct().size, actual = hits.size)
    }

    @Test
    fun blockRaycastEntersNegativeNeighborAtZeroTime() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(-1, 0, 0) to Blocks.STONE.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.0, 0.5, 0.5), Geometry.vector(-2.0, 1.0, 0.0))

        val hit = WorldAccessImpl.raycastBlockHits(world, ray, 2.0).single()

        assertEquals(expected = -1, actual = hit.blockPosition.x)
        assertEquals(expected = 0.0, actual = hit.time, absoluteTolerance = 1.0E-9)
    }

    @Test
    fun blockRaycastUsesFloorOfIntegerEndpoint() {
        Bootstrap.register()
        val negativeWorld = TestWorld(mapOf(BlockPos(-1, 0, 0) to Blocks.STONE.defaultState))
        val negativeRay = Geometry.ray(Geometry.vector(1.5, 0.5, 0.5), Geometry.vector(-1.0, 0.0, 0.0))

        assertNull(WorldAccessImpl.raycastBlockHits(negativeWorld, negativeRay, 1.5).firstOrNull())
    }

    @Test
    fun blockRaycastDoesNotInheritTheVanillaVoxelLimit() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(0, 0, 250) to Blocks.STONE.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 0.5), Geometry.vector(0.0, 0.0, 1.0))

        val hit = WorldAccessImpl.raycastBlockHits(world, ray, 260.0).single()

        assertEquals(expected = 250, actual = hit.blockPosition.z)
    }

    @Test
    fun blockRaycastIncludesCollisionAtSegmentEndpoint() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(0, 0, 6) to Blocks.STONE.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 2.0), Geometry.vector(0.0, 0.0, 1.0))

        val hit = WorldAccessImpl.raycastBlockHits(world, ray, 4.0).single()

        assertEquals(expected = 6, actual = hit.blockPosition.z)
        assertEquals(expected = 4.0, actual = hit.time, absoluteTolerance = 1.0E-9)
    }

    @Test
    fun blockDestructionPreservesTheDropChoiceAndPosition() {
        val world = RecordingDestructionWorld()
        val position = BlockPositions.of(1, 2, 3)

        assertTrue(WorldAccessImpl.destroyBlock(world, position, false))

        assertEquals(expected = BlockPos(1, 2, 3), actual = world.destroyedPosition)
        assertFalse(world.dropItems)
    }

    private open class TestWorld(private val blockStates: Map<BlockPos, IBlockState>) : World(SaveHandlerMP(), WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"), WorldProviderSurface(), Profiler(), false) {
        override fun getBlockState(position: BlockPos): IBlockState {
            return blockStates[position] ?: Blocks.AIR.defaultState
        }

        override fun createChunkProvider(): IChunkProvider {
            error("Chunk provider is not used by block raycast tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return true
        }
    }

    private class RecordingDestructionWorld : TestWorld(emptyMap()) {
        var destroyedPosition: BlockPos? = null
        var dropItems = true

        override fun destroyBlock(position: BlockPos, dropBlock: Boolean): Boolean {
            destroyedPosition = position
            dropItems = dropBlock
            return true
        }
    }
}
