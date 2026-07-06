/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.lethal.bridge.adapter.item.*
import heckerpowered.lethal.bridge.math.BlockDirection
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.EnumAction
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand

object ItemInterop {
    fun hand(hand: EnumHand): Hand {
        return when (hand) {
            EnumHand.MAIN_HAND -> Hand.Main
            EnumHand.OFF_HAND -> Hand.Off
        }
    }

    fun equipmentSlot(slot: EntityEquipmentSlot): EquipmentSlot {
        return when (slot) {
            EntityEquipmentSlot.MAINHAND -> EquipmentSlot.MainHand
            EntityEquipmentSlot.OFFHAND -> EquipmentSlot.OffHand
            EntityEquipmentSlot.FEET -> EquipmentSlot.Feet
            EntityEquipmentSlot.LEGS -> EquipmentSlot.Legs
            EntityEquipmentSlot.CHEST -> EquipmentSlot.Chest
            EntityEquipmentSlot.HEAD -> EquipmentSlot.Head
        }
    }

    fun equipmentSlot(slot: EquipmentSlot?): EntityEquipmentSlot? {
        if (slot == null) return null

        return when (slot) {
            EquipmentSlot.MainHand -> EntityEquipmentSlot.MAINHAND
            EquipmentSlot.OffHand -> EntityEquipmentSlot.OFFHAND
            EquipmentSlot.Feet -> EntityEquipmentSlot.FEET
            EquipmentSlot.Legs -> EntityEquipmentSlot.LEGS
            EquipmentSlot.Chest -> EntityEquipmentSlot.CHEST
            EquipmentSlot.Head -> EntityEquipmentSlot.HEAD
        }
    }

    fun interactionResult(result: ItemInteractionResult): EnumActionResult {
        return when (result) {
            ItemInteractionResult.Pass -> EnumActionResult.PASS
            ItemInteractionResult.Success -> EnumActionResult.SUCCESS
            ItemInteractionResult.Consume -> EnumActionResult.SUCCESS
            ItemInteractionResult.Fail -> EnumActionResult.FAIL
        }
    }

    fun useAnimation(animation: ItemUseAnimation): EnumAction {
        return when (animation) {
            ItemUseAnimation.None -> EnumAction.NONE
            ItemUseAnimation.Eat -> EnumAction.EAT
            ItemUseAnimation.Drink -> EnumAction.DRINK
            ItemUseAnimation.Block -> EnumAction.BLOCK
            ItemUseAnimation.Bow -> EnumAction.BOW
            ItemUseAnimation.Spear -> EnumAction.BOW
        }
    }

    fun toolClass(category: MiningToolCategory): String {
        return when (category) {
            MiningToolCategory.Pickaxe -> "pickaxe"
            MiningToolCategory.Axe -> "axe"
            MiningToolCategory.Shovel -> "shovel"
            MiningToolCategory.Hoe -> "hoe"
        }
    }

    fun blockDirection(facing: EnumFacing): BlockDirection {
        return when (facing) {
            EnumFacing.DOWN -> BlockDirection.Down
            EnumFacing.UP -> BlockDirection.Up
            EnumFacing.NORTH -> BlockDirection.North
            EnumFacing.SOUTH -> BlockDirection.South
            EnumFacing.WEST -> BlockDirection.West
            EnumFacing.EAST -> BlockDirection.East
        }
    }
}