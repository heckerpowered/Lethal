/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import com.mojang.authlib.GameProfile
import net.minecraft.entity.IEntityMultiPart
import net.minecraft.entity.MultiPartEntityPart
import net.minecraft.entity.effect.EntityLightningBolt
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.init.Bootstrap
import net.minecraft.profiler.Profiler
import net.minecraft.util.DamageSource
import net.minecraft.util.math.BlockPos
import net.minecraft.world.*
import net.minecraft.world.chunk.IChunkProvider
import net.minecraft.world.storage.SaveHandlerMP
import net.minecraft.world.storage.WorldInfo
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityTypeInteropTest {
    @Test
    fun playerUsesTheLogicalVanillaTypeOutsideTheEntityRegistry() {
        val player = TestPlayer(TestWorld())

        val type = EntityTypeInterop.typeOf(player)

        assertEquals(expected = "minecraft:player", actual = type.identifier.asString())
    }

    @Test
    fun lightningUsesTheLogicalVanillaTypeOutsideTheEntityRegistry() {
        val lightning = EntityLightningBolt(TestWorld(), 0.0, 0.0, 0.0, false)

        val type = EntityTypeInterop.typeOf(lightning)

        assertEquals(expected = "minecraft:lightning_bolt", actual = type.identifier.asString())
    }

    @Test
    fun multipartEntityUsesItsLogicalParentType() {
        val parent = TestPlayer(TestWorld())
        val part = MultiPartEntityPart(parent, "test", 1.0F, 1.0F)

        val type = EntityTypeInterop.typeOf(part)

        assertEquals(expected = "minecraft:player", actual = type.identifier.asString())
    }

    private class TestPlayer(world: World) : EntityPlayer(world, GameProfile(UUID.randomUUID(), "test")), IEntityMultiPart {
        override fun isSpectator() = false

        override fun isCreative() = false

        override fun getWorld(): World = world

        override fun attackEntityFromPart(part: MultiPartEntityPart, source: DamageSource, damage: Float) = false
    }

    private class TestWorld : World(SaveHandlerMP(), WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"), WorldProviderSurface(), Profiler(), false) {
        init {
            Bootstrap.register()
            provider.setWorld(this)
        }

        override fun getBlockState(position: BlockPos) = Blocks.AIR.defaultState

        override fun createChunkProvider(): IChunkProvider {
            error("Chunk provider is not used by entity type tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean) = true
    }
}
