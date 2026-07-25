/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.entity

import heckerpowered.lethal.gameplay.common.entity.ForgeEnhancedStarJudgementEntity
import heckerpowered.lethal.gameplay.common.entity.ForgeStandardStarJudgementEntity
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
import kotlin.test.assertIs
import kotlin.test.assertSame

class ForgeEntityTypesTest {
    @Test
    fun definitionsAreStableAndConstructTheirNativeEntityKinds() {
        Bootstrap.register()
        val entityTypes = ForgeEntityTypes.all()

        assertEquals(listOf("star_judgement", "enhanced_star_judgement"), entityTypes.map { entityType -> entityType.registryName?.path })
        assertSame(entityTypes, ForgeEntityTypes.all())

        val world = TestWorld()
        assertIs<ForgeStandardStarJudgementEntity>(entityTypes[0].newInstance(world))
        assertIs<ForgeEnhancedStarJudgementEntity>(entityTypes[1].newInstance(world))
    }

    private class TestWorld : World(
        SaveHandlerMP(),
        WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"),
        WorldProviderSurface(),
        Profiler(),
        false,
    ) {
        init {
            provider.setWorld(this)
        }

        override fun getBlockState(position: BlockPos) = Blocks.AIR.defaultState

        override fun createChunkProvider(): IChunkProvider {
            error("Chunk provider is not used by entity type tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return true
        }
    }
}
