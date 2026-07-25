/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.entity

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.lethal.gameplay.common.skill.StarJudgementEntityAccess
import heckerpowered.lethal.gameplay.common.skill.StarJudgementKind
import heckerpowered.lethal.platform.interop.entity
import net.minecraft.init.Blocks
import net.minecraft.init.Bootstrap
import net.minecraft.nbt.NBTTagCompound
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

class ForgeStarJudgementEntityTest {
    @Test
    fun nativeEntityPersistsFuseAndOwner() {
        Bootstrap.register()
        val world = TestWorld()
        val ownerIdentifier = UUID.randomUUID()
        val entity = TestStarJudgementEntity(world, StarJudgementKind.Standard)
        entity.setOwner(createPlayer(ownerIdentifier))
        entity.setPosition(1.0, 2.0, 3.0)
        repeat(7) { entity.onUpdate() }

        val savedData = NBTTagCompound()
        entity.writeEffectData(savedData)

        assertEquals(93, savedData.getInteger("Fuse"))
        assertEquals(ownerIdentifier, savedData.getUniqueId("Owner"))

        val loadedEntity = TestStarJudgementEntity(world, StarJudgementKind.Standard)
        loadedEntity.setPosition(1.0, 2.0, 3.0)
        loadedEntity.readEffectData(savedData)
        loadedEntity.onUpdate()
        val resavedData = NBTTagCompound()
        loadedEntity.writeEffectData(resavedData)

        assertEquals(92, resavedData.getInteger("Fuse"))
        assertEquals(ownerIdentifier, resavedData.getUniqueId("Owner"))
    }

    @Test
    fun nativeEffectEntityResolvesThroughItsGameplayAccessType() {
        Bootstrap.register()
        val nativeEntity = TestStarJudgementEntity(TestWorld(), StarJudgementKind.Enhanced)

        val access = assertIs<StarJudgementEntityAccess>(nativeEntity.entity())

        assertEquals(StarJudgementKind.Enhanced, access.starJudgementKind)
        assertEquals(nativeEntity.uniqueID, access.uuid)
    }

    private fun createPlayer(identifier: UUID): PlayerAccess {
        return Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java)) { _, method, _ ->
            when (method.name) {
                "getUuid" -> identifier
                else -> error("Unsupported player method: ${method.name}")
            }
        } as PlayerAccess
    }

    private class TestStarJudgementEntity(
        world: World,
        kind: StarJudgementKind,
    ) : ForgeStarJudgementEntity(world, kind) {
        fun writeEffectData(compound: NBTTagCompound) {
            writeEntityToNBT(compound)
        }

        fun readEffectData(compound: NBTTagCompound) {
            readEntityFromNBT(compound)
        }
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
            error("Chunk provider is not used by Star Judgement entity tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return true
        }
    }
}
