/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.event

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm
import heckerpowered.lethal.platform.adapter.item.HostedItem
import net.minecraft.client.model.ModelBiped
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHandSide
import kotlin.test.Test
import kotlin.test.assertEquals

class FirearmRenderEventHandlerTest {
    @Test
    fun mainHandFirearmUsesThePrimaryArm() {
        Bootstrap.register()
        val model = ModelBiped()

        applyHeldFirearmPose(model, EnumHandSide.RIGHT, firearmStack(), ItemStack.EMPTY)

        assertEquals(expected = ModelBiped.ArmPose.BOW_AND_ARROW, actual = model.rightArmPose)
        assertEquals(expected = ModelBiped.ArmPose.EMPTY, actual = model.leftArmPose)
    }

    @Test
    fun leftHandedMainHandFirearmUsesTheLeftArm() {
        Bootstrap.register()
        val model = ModelBiped()

        applyHeldFirearmPose(model, EnumHandSide.LEFT, firearmStack(), ItemStack.EMPTY)

        assertEquals(expected = ModelBiped.ArmPose.EMPTY, actual = model.rightArmPose)
        assertEquals(expected = ModelBiped.ArmPose.BOW_AND_ARROW, actual = model.leftArmPose)
    }

    @Test
    fun offHandFirearmUsesTheOppositeArm() {
        Bootstrap.register()
        val model = ModelBiped()

        applyHeldFirearmPose(model, EnumHandSide.RIGHT, ItemStack.EMPTY, firearmStack())

        assertEquals(expected = ModelBiped.ArmPose.EMPTY, actual = model.rightArmPose)
        assertEquals(expected = ModelBiped.ArmPose.BOW_AND_ARROW, actual = model.leftArmPose)
    }

    @Test
    fun dualWieldedFirearmsUseTheMainHandArm() {
        Bootstrap.register()
        val model = ModelBiped()

        applyHeldFirearmPose(model, EnumHandSide.RIGHT, firearmStack(), firearmStack())

        assertEquals(expected = ModelBiped.ArmPose.BOW_AND_ARROW, actual = model.rightArmPose)
        assertEquals(expected = ModelBiped.ArmPose.EMPTY, actual = model.leftArmPose)
    }

    @Test
    fun emptyHandsDoNotChangeArmPoses() {
        val model = ModelBiped()

        applyHeldFirearmPose(model, EnumHandSide.RIGHT, ItemStack.EMPTY, ItemStack.EMPTY)

        assertEquals(expected = ModelBiped.ArmPose.EMPTY, actual = model.rightArmPose)
        assertEquals(expected = ModelBiped.ArmPose.EMPTY, actual = model.leftArmPose)
    }

    private fun firearmStack(): ItemStack {
        return ItemStack(HostedItem(TestFirearm))
    }

    private object TestFirearm : Firearm() {
        override val identifier = Identifier.create("test", "firearm_pose")

        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
            return Frequency.perSecond(1)
        }

        override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        }
    }
}
