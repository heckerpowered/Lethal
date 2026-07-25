/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.load
import heckerpowered.lethal.gameplay.common.entity.ForgeEnhancedStarJudgementEntity
import heckerpowered.lethal.gameplay.common.entity.ForgeStandardStarJudgementEntity
import heckerpowered.lethal.gameplay.common.skill.StarJudgementKind
import heckerpowered.lethal.gameplay.common.skill.StarJudgementSpawner
import heckerpowered.lethal.platform.adapter.world.HostedWorldAccess
import net.minecraft.entity.Entity
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
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ForgeStarJudgementSpawnerTest {
    @Test
    fun serviceCreatesTheNativeEntityForEachJudgementKind() {
        Bootstrap.register()
        val world = RecordingWorld()
        val worldAccess = HostedWorldAccess(world)
        val owner = createPlayer()
        val position = Geometry.vector(1.25, 2.5, 3.75)
        val spawner = Services.load<StarJudgementSpawner>()

        spawner.spawn(worldAccess, owner, position, StarJudgementKind.Standard)
        spawner.spawn(worldAccess, owner, position, StarJudgementKind.Enhanced)

        assertIs<ForgeStandardStarJudgementEntity>(world.spawnedEntities[0])
        assertIs<ForgeEnhancedStarJudgementEntity>(world.spawnedEntities[1])
        assertTrue(world.spawnedEntities.all { entity -> entity.positionVector.x == position.x })
        assertTrue(world.spawnedEntities.all { entity -> entity.positionVector.y == position.y })
        assertTrue(world.spawnedEntities.all { entity -> entity.positionVector.z == position.z })
        assertEquals(2, world.spawnedEntities.size)
    }

    private fun createPlayer(): PlayerAccess {
        val identifier = UUID.randomUUID()
        return Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java)) { _, method, _ ->
            when (method.name) {
                "getUuid" -> identifier
                else -> error("Unsupported player method: ${method.name}")
            }
        } as PlayerAccess
    }

    private class RecordingWorld : World(
        SaveHandlerMP(),
        WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"),
        WorldProviderSurface(),
        Profiler(),
        false,
    ) {
        val spawnedEntities = mutableListOf<Entity>()

        init {
            provider.setWorld(this)
        }

        override fun getBlockState(position: BlockPos) = Blocks.AIR.defaultState

        override fun spawnEntity(entityIn: Entity): Boolean {
            spawnedEntities += entityIn
            return true
        }

        override fun createChunkProvider(): IChunkProvider {
            error("Chunk provider is not used by Star Judgement spawner tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return true
        }
    }
}
