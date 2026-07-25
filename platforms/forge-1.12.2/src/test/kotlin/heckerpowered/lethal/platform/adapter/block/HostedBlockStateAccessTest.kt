/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.block

import heckerpowered.bridge.adapter.block.BlockCategory
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.init.Bootstrap
import net.minecraft.profiler.Profiler
import net.minecraft.util.math.BlockPos
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
import kotlin.test.assertTrue

class HostedBlockStateAccessTest {
    @Test
    fun exposesTheBlockThatOwnsTheState() {
        Bootstrap.register()

        val access = HostedBlockStateAccess(Blocks.STONE.defaultState)

        assertEquals("minecraft:stone", access.block.identifier.asString())
    }

    @Test
    fun mapsFortuneCategoriesToForgeTwelveBlockTypes() {
        Bootstrap.register()
        val states = mapOf(
            BlockPos(0, 0, 0) to Blocks.REEDS.defaultState,
            BlockPos(1, 0, 0) to Blocks.LOG.defaultState,
            BlockPos(2, 0, 0) to Blocks.PLANKS.defaultState,
            BlockPos(3, 0, 0) to Blocks.SNOW.defaultState,
            BlockPos(4, 0, 0) to Blocks.SNOW_LAYER.defaultState,
            BlockPos(5, 0, 0) to Blocks.SAND.defaultState,
        )
        val world = TestWorld(states)

        assertTrue(access(world, 0).isIn(BlockCategory.SugarCane))
        assertTrue(access(world, 1).isIn(BlockCategory.Logs))
        assertTrue(access(world, 2).isIn(BlockCategory.Planks))
        assertTrue(access(world, 3).isIn(BlockCategory.Snow))
        assertTrue(access(world, 4).isIn(BlockCategory.Snow))
        assertTrue(access(world, 5).isIn(BlockCategory.Sand))
        assertFalse(access(world, 5).isIn(BlockCategory.Logs))
    }

    @Test
    fun replaceabilityUsesTheBlockContextWhenAvailable() {
        Bootstrap.register()
        val oneLayerSnow = Blocks.SNOW_LAYER.defaultState
        val twoLayerSnow = oneLayerSnow.withProperty(net.minecraft.block.BlockSnow.LAYERS, 2)
        val states = mapOf(
            BlockPos(0, 0, 0) to oneLayerSnow,
            BlockPos(1, 0, 0) to twoLayerSnow,
        )
        val world = TestWorld(states)

        assertTrue(access(world, 0).isReplaceable)
        assertFalse(access(world, 1).isReplaceable)
    }

    private fun access(world: World, x: Int): HostedBlockStateAccess {
        val position = BlockPos(x, 0, 0)
        return HostedBlockStateAccess(world.getBlockState(position), world, position)
    }

    private class TestWorld(private val blockStates: Map<BlockPos, IBlockState>) : World(
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
            error("Chunk provider is not used by block classification tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return true
        }
    }
}
