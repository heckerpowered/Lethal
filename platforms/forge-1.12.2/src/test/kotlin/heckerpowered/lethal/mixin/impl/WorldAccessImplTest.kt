/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositions
import heckerpowered.bridge.math.Geometry
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

        assertEquals(listOf(2, 4), hits.map { hit -> hit.blockPosition.z })
        assertEquals(listOf("minecraft:stone", "minecraft:glass"), hits.map { hit -> hit.blockState.block.identifier.asString() })
        assertEquals(listOf(BlockDirection.North, BlockDirection.North), hits.map { hit -> hit.face })
        assertEquals(0.75, hits[0].time, 1.0E-9)
        assertEquals(1.75, hits[1].time, 1.0E-9)

        val nativeHit = assertNotNull(world.rayTraceBlocks(Vec3d(0.5, 0.5, 0.5), Vec3d(0.5, 0.5, 6.5), false, true, false))
        assertEquals(nativeHit.blockPos.z, hits.first().blockPosition.z)
        assertEquals(nativeHit.hitVec.z, hits.first().point.z, 1.0E-9)
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

        assertEquals(3, hit.blockPosition.z)
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

        assertEquals(2, hit.blockPosition.z)
    }

    @Test
    fun blockRaycastSupportsNegativeNonUnitDirections() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(0, 0, 3) to Blocks.STONE.defaultState, BlockPos(0, 0, 1) to Blocks.STONE.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 5.5), Geometry.vector(0.0, 0.0, -2.0))

        val hits = WorldAccessImpl.raycastBlockHits(world, ray, 6.0).toList()

        assertEquals(listOf(3, 1), hits.map { hit -> hit.blockPosition.z })
        assertEquals(listOf(BlockDirection.South, BlockDirection.South), hits.map { hit -> hit.face })
        assertEquals(0.75, hits[0].time, 1.0E-9)
        assertEquals(1.75, hits[1].time, 1.0E-9)
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
        assertEquals(hits.map { hit -> hit.time }.sorted(), hits.map { hit -> hit.time })
        assertEquals(hits.map { hit -> hit.blockPosition }.distinct().size, hits.size)
    }

    @Test
    fun blockRaycastEntersNegativeNeighborAtZeroTime() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(-1, 0, 0) to Blocks.STONE.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.0, 0.5, 0.5), Geometry.vector(-2.0, 1.0, 0.0))

        val hit = WorldAccessImpl.raycastBlockHits(world, ray, 2.0).single()

        assertEquals(-1, hit.blockPosition.x)
        assertEquals(0.0, hit.time, 1.0E-9)
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

        assertEquals(250, hit.blockPosition.z)
    }

    @Test
    fun blockRaycastIncludesCollisionAtSegmentEndpoint() {
        Bootstrap.register()
        val world = TestWorld(mapOf(BlockPos(0, 0, 6) to Blocks.STONE.defaultState))
        val ray = Geometry.ray(Geometry.vector(0.5, 0.5, 2.0), Geometry.vector(0.0, 0.0, 1.0))

        val hit = WorldAccessImpl.raycastBlockHits(world, ray, 4.0).single()

        assertEquals(6, hit.blockPosition.z)
        assertEquals(4.0, hit.time, 1.0E-9)
    }

    @Test
    fun blockDestructionPreservesTheDropChoiceAndPosition() {
        val world = RecordingDestructionWorld()
        val position = BlockPositions.of(1, 2, 3)

        assertTrue(WorldAccessImpl.destroyBlock(world, position, dropItems = false))

        assertEquals(BlockPos(1, 2, 3), world.destroyedPosition)
        assertFalse(world.dropItems)
    }

    private open class TestWorld(private val blockStates: Map<BlockPos, IBlockState>) : World(
        SaveHandlerMP(),
        WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"),
        WorldProviderSurface(),
        Profiler(),
        false,
    ) {
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
