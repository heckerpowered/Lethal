/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import com.mojang.authlib.GameProfile
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageSourceSpec
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.math.Geometry
import heckerpowered.lethal.platform.interop.asHost
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.boss.EntityDragon
import net.minecraft.entity.monster.EntityEnderman
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.profiler.Profiler
import net.minecraft.util.EntityDamageSource
import net.minecraft.util.EntityDamageSourceIndirect
import net.minecraft.util.EnumHandSide
import net.minecraft.world.GameType
import net.minecraft.world.World
import net.minecraft.world.WorldProviderSurface
import net.minecraft.world.WorldSettings
import net.minecraft.world.WorldType
import net.minecraft.world.chunk.IChunkProvider
import net.minecraft.world.storage.SaveHandlerMP
import net.minecraft.world.storage.WorldInfo
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ForgeDamageProviderTest {
    @Test
    fun attributedFellOutOfWorldPreservesPlayerSourceAndDamageProperties() {
        Bootstrap.register()
        val world = TestWorld()
        val player = TestPlayer(world)

        val source = ForgeDamageSources
            .source(VanillaDamageSourceSpec(VanillaDamageType.FellOutOfWorld), player, player, Geometry.vector(1.0, 2.0, 3.0))
            .asHost()

        assertFalse(source is EntityDamageSource)
        assertSame(player, source.immediateSource)
        assertSame(player, source.trueSource)
        assertEquals(expected = 1.0, actual = source.damageLocation?.x)
        assertEquals(expected = 2.0, actual = source.damageLocation?.y)
        assertEquals(expected = 3.0, actual = source.damageLocation?.z)
        assertTrue(source.isUnblockable)
        assertTrue(source.canHarmInCreative())
        assertFalse(source.isDamageAbsolute)
    }

    @Test
    fun attributedDamageLocationUsesExplicitPositionThenDirectEntityOnly() {
        Bootstrap.register()
        val world = TestWorld()
        val player = TestPlayer(world)
        player.setPosition(4.0, 5.0, 6.0)
        val spec = VanillaDamageSourceSpec(VanillaDamageType.FellOutOfWorld)

        val directSource = ForgeDamageSources.source(spec, player, null).asHost()
        val causingSource = ForgeDamageSources.source(spec, null, player).asHost()
        val positionedSource = ForgeDamageSources.source(spec, null, player, Geometry.vector(1.0, 2.0, 3.0)).asHost()

        assertEquals(expected = 4.0, actual = directSource.damageLocation?.x)
        assertEquals(expected = 5.0, actual = directSource.damageLocation?.y)
        assertEquals(expected = 6.0, actual = directSource.damageLocation?.z)
        assertNull(causingSource.damageLocation)
        assertEquals(expected = 1.0, actual = positionedSource.damageLocation?.x)
        assertEquals(expected = 2.0, actual = positionedSource.damageLocation?.y)
        assertEquals(expected = 3.0, actual = positionedSource.damageLocation?.z)
    }

    @Test
    fun attributedFellOutOfWorldMarksVictimAsRecentlyHitByPlayer() {
        Bootstrap.register()
        val world = TestWorld()
        val player = TestPlayer(world)
        val victim = TestLivingEntity(world)
        val source = ForgeDamageSources
            .source(VanillaDamageSourceSpec(VanillaDamageType.FellOutOfWorld), player, player)
            .asHost()

        assertTrue(victim.attackEntityFrom(source, 1.0F))
        assertEquals(expected = 100, actual = victim.recentlyHitTicks)
        assertSame(player, victim.lastAttackingPlayer)
    }

    @Test
    fun attributedFellOutOfWorldDamagesEnderDragonThroughPart() {
        Bootstrap.register()
        val world = TestWorld()
        val player = TestPlayer(world)
        val dragon = TestDragon(world)
        val initialHealth = dragon.health
        val source = ForgeDamageSources
            .source(VanillaDamageSourceSpec(VanillaDamageType.FellOutOfWorld), player, player)
            .asHost()

        assertTrue(dragon.dragonPartHead.attackEntityFrom(source, 4.0F))
        assertTrue(dragon.health < initialHealth)
    }

    @Test
    fun attributedFellOutOfWorldDoesNotTriggerEndermanIndirectDamageDodge() {
        Bootstrap.register()
        val world = TestWorld()
        val player = TestPlayer(world)
        val enderman = TestEnderman(world)
        val initialHealth = enderman.health
        val source = ForgeDamageSources
            .source(VanillaDamageSourceSpec(VanillaDamageType.FellOutOfWorld), player, player)
            .asHost()

        assertFalse(source is EntityDamageSourceIndirect)
        assertTrue(enderman.attackEntityFrom(source, 1.0F))
        assertEquals(expected = initialHealth - 1.0F, actual = enderman.health)
    }

    @Test
    fun attributedVanillaIndirectDamageRemainsIndirect() {
        Bootstrap.register()
        val world = TestWorld()
        val player = TestPlayer(world)
        val source = ForgeDamageSources
            .source(VanillaDamageSourceSpec(VanillaDamageType.Thrown), player, player, Geometry.vector(1.0, 2.0, 3.0))
            .asHost()

        assertTrue(source is EntityDamageSourceIndirect)
        assertTrue(source.isProjectile)
        assertSame(player, source.immediateSource)
        assertSame(player, source.trueSource)
    }

    private class TestPlayer(world: World) : EntityPlayer(world, GameProfile(UUID.randomUUID(), "damage-source-test")) {
        override fun isSpectator() = false
        override fun isCreative() = false
    }

    private class TestLivingEntity(world: World) : EntityLivingBase(world) {
        val recentlyHitTicks: Int
            get() = recentlyHit

        val lastAttackingPlayer: EntityPlayer?
            get() = attackingPlayer

        override fun isOnLadder(): Boolean {
            return false
        }

        override fun getArmorInventoryList(): Iterable<ItemStack> {
            return emptyList()
        }

        override fun getItemStackFromSlot(slotIn: EntityEquipmentSlot): ItemStack {
            return ItemStack.EMPTY
        }

        override fun setItemStackToSlot(slotIn: EntityEquipmentSlot, stack: ItemStack) {
        }

        override fun getPrimaryHand(): EnumHandSide {
            return EnumHandSide.RIGHT
        }
    }

    private class TestDragon(world: World) : EntityDragon(world) {
        override fun isOnLadder(): Boolean {
            return false
        }
    }

    private class TestEnderman(world: World) : EntityEnderman(world) {
        override fun isOnLadder(): Boolean {
            return false
        }

        override fun teleportRandomly(): Boolean {
            return true
        }
    }

    private class TestWorld : World(SaveHandlerMP(), WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"), WorldProviderSurface(), Profiler(), false) {
        init {
            provider.setWorld(this)
        }

        override fun createChunkProvider(): IChunkProvider {
            error("Chunk provider is not used by damage source tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return true
        }
    }
}
