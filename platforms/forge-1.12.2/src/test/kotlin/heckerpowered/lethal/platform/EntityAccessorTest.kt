/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform

import net.minecraft.entity.monster.EntityZombie
import net.minecraft.init.Blocks
import net.minecraft.init.Bootstrap
import net.minecraft.init.MobEffects
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
import kotlin.test.assertTrue

class EntityAccessorTest {
    @Test
    fun livingEntityFallbackAppliesTheGlowingEffect() {
        Bootstrap.register()
        val entity = EntityZombie(TestWorld())

        LivingEntityAccessor(entity).glowFor(1_200)

        assertEquals(1_200, entity.getActivePotionEffect(MobEffects.GLOWING)?.duration)
    }

    @Test
    fun entityFallbackRemovesTheNativeEntity() {
        Bootstrap.register()
        val entity = EntityZombie(TestWorld())

        EntityAccessor(entity).remove()

        assertTrue(entity.isDead)
    }

    private class TestWorld : World(
        SaveHandlerMP(),
        WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"),
        WorldProviderSurface(),
        Profiler(),
        false,
    ) {
        override fun getBlockState(position: BlockPos) = Blocks.AIR.defaultState

        override fun createChunkProvider(): IChunkProvider {
            error("Chunk provider is not used by glowing-effect tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return true
        }
    }
}
