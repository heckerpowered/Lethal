/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import com.mojang.authlib.GameProfile
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.ContinuousUseItem
import heckerpowered.bridge.adapter.item.ItemGlint
import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.adapter.item.ItemForm
import heckerpowered.bridge.adapter.item.ItemProperties
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm
import heckerpowered.lethal.platform.interop.asHost
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Bootstrap
import net.minecraft.item.EnumAction
import net.minecraft.item.ItemStack
import net.minecraft.profiler.Profiler
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HostedItemTest {
    @Test
    fun continuousUseFeatureMapsToBowUseState() {
        Bootstrap.register()
        val item = HostedItem(TestContinuousUseItem)
        val stack = ItemStack(item)

        assertEquals(expected = EnumAction.BOW, actual = item.getItemUseAction(stack))
        assertEquals(expected = Int.MAX_VALUE, actual = item.getMaxItemUseDuration(stack))
    }

    @Test
    fun firearmDoesNotUseTheNativeChargeState() {
        Bootstrap.register()
        val item = HostedItem(TestFirearm)
        val stack = proxy<ItemStackAccess>()

        assertFalse(item.blueprint is ContinuousUseItem)
        assertEquals(expected = EnumAction.NONE, actual = item.blueprint.getUseAnimation(stack).asHost())
        assertEquals(expected = 0, actual = item.blueprint.getUseDurationTicks(stack, null))
    }

    @Test
    fun rightClickStartsContinuousUse() {
        Bootstrap.register()
        val world = TestWorld()
        val player = TestPlayer(world)
        val item = HostedItem(TestContinuousUseItem)
        val stack = ItemStack(item)
        player.setHeldItem(EnumHand.MAIN_HAND, stack)

        val result = item.onItemRightClick(world, player, EnumHand.MAIN_HAND)

        assertEquals(expected = EnumActionResult.SUCCESS, actual = result.type)
        assertTrue(player.isHandActive)
        assertEquals(expected = EnumHand.MAIN_HAND, actual = player.activeHand)
    }

    @Test
    fun optionalGlintCapabilityControlsHostedItemPresentation() {
        Bootstrap.register()
        val item = HostedItem(TestGlintItem)
        val itemGlint = assertIs<ItemGlint>(item.blueprint)

        assertTrue(itemGlint.hasGlint(proxy()))
    }

    private object TestContinuousUseItem : ItemBlueprint, ContinuousUseItem {
        override val identifier = Identifier.create("test", "continuous_use")
        override val form = ItemForm.Regular
        override val properties = ItemProperties()
    }

    private object TestFirearm : Firearm() {
        override val identifier = Identifier.create("test", "firearm_without_native_charge")

        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
            return Frequency.perSecond(1)
        }

        override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        }
    }

    private object TestGlintItem : ItemBlueprint, ItemGlint {
        override val identifier = Identifier.create("test", "glint")
        override val form = ItemForm.Regular
        override val properties = ItemProperties()

        override fun hasGlint(stack: ItemStackAccess): Boolean {
            return true
        }
    }

    private inline fun <reified Access : Any> proxy(): Access {
        val proxy = Proxy.newProxyInstance(Access::class.java.classLoader, arrayOf(Access::class.java)) { _, method, _ ->
            error("Unsupported ${Access::class.java.simpleName} method: ${method.name}")
        }
        return Access::class.java.cast(proxy)
    }

    private class TestPlayer(world: World) : EntityPlayer(world, GameProfile(UUID.randomUUID(), "continuous-use-test")) {
        override fun isSpectator() = false
        override fun isCreative() = false
    }

    private class TestWorld : World(SaveHandlerMP(), WorldInfo(WorldSettings(0L, GameType.SURVIVAL, false, false, WorldType.DEFAULT), "test"), WorldProviderSurface(), Profiler(), false) {
        init {
            provider.setWorld(this)
        }

        override fun createChunkProvider(): IChunkProvider {
            error("Chunk provider is not used by item tests")
        }

        override fun isChunkLoaded(chunkX: Int, chunkZ: Int, allowEmpty: Boolean): Boolean {
            return true
        }
    }
}
