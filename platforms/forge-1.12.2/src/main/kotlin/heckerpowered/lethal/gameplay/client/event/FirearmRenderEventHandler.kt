/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.event

import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm
import heckerpowered.lethal.platform.adapter.item.HostedItem
import net.minecraft.client.model.ModelBiped
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHandSide
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = [Side.CLIENT])
object FirearmRenderEventHandler {
    @SubscribeEvent
    @JvmStatic
    fun onRenderEntity(event: RenderLivingEvent.Pre<*>) {
        val model = event.renderer.mainModel as? ModelBiped ?: return
        val entity = event.entity

        // Active-use poses have already been selected by the renderer and take precedence.
        if (entity.isHandActive) return

        applyHeldFirearmPose(model, entity.primaryHand, entity.heldItemMainhand, entity.heldItemOffhand)
    }
}

internal fun applyHeldFirearmPose(model: ModelBiped, primaryHand: EnumHandSide, mainHandStack: ItemStack, offHandStack: ItemStack) {
    val firearmSide = when {
        mainHandStack.holdsFirearm() -> primaryHand
        offHandStack.holdsFirearm() -> primaryHand.opposite()
        else -> return
    }

    when (firearmSide) {
        EnumHandSide.RIGHT -> model.rightArmPose = ModelBiped.ArmPose.BOW_AND_ARROW
        EnumHandSide.LEFT -> model.leftArmPose = ModelBiped.ArmPose.BOW_AND_ARROW
    }
}

private fun ItemStack.holdsFirearm(): Boolean {
    val hostedItem = item as? HostedItem ?: return false
    return hostedItem.blueprint is Firearm
}
