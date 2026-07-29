/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.item.EquipmentSlot
import heckerpowered.bridge.adapter.item.Hand
import heckerpowered.bridge.adapter.item.ItemInteractionResult
import heckerpowered.bridge.adapter.item.ItemUseAnimation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot as NativeEquipmentSlot
import net.minecraft.world.item.UseAnim

fun Hand.asHost(): InteractionHand {
    return when (this) {
        Hand.Main -> InteractionHand.MAIN_HAND
        Hand.Off -> InteractionHand.OFF_HAND
    }
}

fun InteractionHand.asView(): Hand {
    return when (this) {
        InteractionHand.MAIN_HAND -> Hand.Main
        InteractionHand.OFF_HAND -> Hand.Off
    }
}

fun ItemInteractionResult.asHost(): InteractionResult {
    return when (this) {
        ItemInteractionResult.Pass -> InteractionResult.PASS
        ItemInteractionResult.Success -> InteractionResult.SUCCESS
        ItemInteractionResult.Consume -> InteractionResult.CONSUME
        ItemInteractionResult.Fail -> InteractionResult.FAIL
    }
}

fun InteractionResult.asView(): ItemInteractionResult {
    return when (this) {
        InteractionResult.SUCCESS, InteractionResult.SUCCESS_NO_ITEM_USED -> ItemInteractionResult.Success
        InteractionResult.CONSUME, InteractionResult.CONSUME_PARTIAL -> ItemInteractionResult.Consume
        InteractionResult.PASS -> ItemInteractionResult.Pass
        InteractionResult.FAIL -> ItemInteractionResult.Fail
    }
}

fun ItemUseAnimation.asHost(): UseAnim {
    return when (this) {
        ItemUseAnimation.None -> UseAnim.NONE
        ItemUseAnimation.Eat -> UseAnim.EAT
        ItemUseAnimation.Drink -> UseAnim.DRINK
        ItemUseAnimation.Block -> UseAnim.BLOCK
        ItemUseAnimation.Bow -> UseAnim.BOW
        ItemUseAnimation.Spear -> UseAnim.SPEAR
    }
}

fun UseAnim.asView(): ItemUseAnimation {
    return when (this) {
        UseAnim.EAT -> ItemUseAnimation.Eat
        UseAnim.DRINK -> ItemUseAnimation.Drink
        UseAnim.BLOCK -> ItemUseAnimation.Block
        UseAnim.BOW -> ItemUseAnimation.Bow
        UseAnim.SPEAR -> ItemUseAnimation.Spear
        UseAnim.NONE, UseAnim.CROSSBOW, UseAnim.SPYGLASS, UseAnim.TOOT_HORN, UseAnim.BRUSH, UseAnim.CUSTOM -> ItemUseAnimation.None
    }
}

fun EquipmentSlot.asHost(): NativeEquipmentSlot {
    return when (this) {
        EquipmentSlot.MainHand -> NativeEquipmentSlot.MAINHAND
        EquipmentSlot.OffHand -> NativeEquipmentSlot.OFFHAND
        EquipmentSlot.Feet -> NativeEquipmentSlot.FEET
        EquipmentSlot.Legs -> NativeEquipmentSlot.LEGS
        EquipmentSlot.Chest -> NativeEquipmentSlot.CHEST
        EquipmentSlot.Head -> NativeEquipmentSlot.HEAD
    }
}

fun NativeEquipmentSlot.asView(): EquipmentSlot? {
    return when (this) {
        NativeEquipmentSlot.MAINHAND -> EquipmentSlot.MainHand
        NativeEquipmentSlot.OFFHAND -> EquipmentSlot.OffHand
        NativeEquipmentSlot.FEET -> EquipmentSlot.Feet
        NativeEquipmentSlot.LEGS -> EquipmentSlot.Legs
        NativeEquipmentSlot.CHEST -> EquipmentSlot.Chest
        NativeEquipmentSlot.HEAD -> EquipmentSlot.Head
        NativeEquipmentSlot.BODY -> null
    }
}
