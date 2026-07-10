/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.lethal.bridge.adapter.item.*
import heckerpowered.lethal.bridge.math.BlockDirection
import heckerpowered.lethal.bridge.resources.Identifier
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.EnumAction
import net.minecraft.item.Item
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand

object ItemInterop {
    @JvmStatic
    fun item(item: ItemAccess): Item {
        return item as? Item ?: error("Unsupported ItemAccess implementation: ${item::class.java.name}")
    }

    @JvmStatic
    fun item(item: Item): ItemAccess {
        return item as? ItemAccess ?: error("ItemAccess is not mixin into Item")
    }

    @JvmStatic
    fun identifier(item: Item): Identifier {
        val registryName = item.registryName ?: error("Item is not registered")
        return IdentifierInterop.identifier(registryName)
    }

    fun hand(hand: EnumHand): Hand {
        return when (hand) {
            EnumHand.MAIN_HAND -> Hand.Main
            EnumHand.OFF_HAND -> Hand.Off
        }
    }

    @JvmStatic
    fun hand(hand: Hand): EnumHand {
        return when (hand) {
            Hand.Main -> EnumHand.MAIN_HAND
            Hand.Off -> EnumHand.OFF_HAND
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

    @JvmName("equipmentSlotOrNull")
    fun equipmentSlot(slot: EquipmentSlot?): EntityEquipmentSlot? {
        if (slot == null) return null

        return equipmentSlot(slot)
    }

    @JvmStatic
    fun equipmentSlot(slot: EquipmentSlot): EntityEquipmentSlot {
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

    @JvmStatic
    fun interactionResult(result: EnumActionResult): ItemInteractionResult {
        return when (result) {
            EnumActionResult.PASS -> ItemInteractionResult.Pass
            EnumActionResult.SUCCESS -> ItemInteractionResult.Success
            EnumActionResult.FAIL -> ItemInteractionResult.Fail
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

    @JvmStatic
    fun useAnimation(animation: EnumAction): ItemUseAnimation {
        return when (animation) {
            EnumAction.NONE -> ItemUseAnimation.None
            EnumAction.EAT -> ItemUseAnimation.Eat
            EnumAction.DRINK -> ItemUseAnimation.Drink
            EnumAction.BLOCK -> ItemUseAnimation.Block
            EnumAction.BOW -> ItemUseAnimation.Bow
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

    @JvmStatic
    fun blockDirection(direction: BlockDirection): EnumFacing {
        return when (direction) {
            BlockDirection.Down -> EnumFacing.DOWN
            BlockDirection.Up -> EnumFacing.UP
            BlockDirection.North -> EnumFacing.NORTH
            BlockDirection.South -> EnumFacing.SOUTH
            BlockDirection.West -> EnumFacing.WEST
            BlockDirection.East -> EnumFacing.EAST
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
}

fun ItemAccess.item(): Item {
    return ItemInterop.item(this)
}

fun Item.item(): ItemAccess {
    return ItemInterop.item(this)
}

fun Item.identifier(): Identifier {
    return ItemInterop.identifier(this)
}

fun EnumHand.hand(): Hand {
    return ItemInterop.hand(this)
}

fun Hand.hand(): EnumHand {
    return ItemInterop.hand(this)
}

fun EntityEquipmentSlot.equipmentSlot(): EquipmentSlot {
    return ItemInterop.equipmentSlot(this)
}

fun EquipmentSlot?.equipmentSlot(): EntityEquipmentSlot? {
    return ItemInterop.equipmentSlot(this)
}

fun ItemInteractionResult.interactionResult(): EnumActionResult {
    return ItemInterop.interactionResult(this)
}

fun EnumActionResult.interactionResult(): ItemInteractionResult {
    return ItemInterop.interactionResult(this)
}

fun ItemUseAnimation.useAnimation(): EnumAction {
    return ItemInterop.useAnimation(this)
}

fun EnumAction.useAnimation(): ItemUseAnimation {
    return ItemInterop.useAnimation(this)
}

fun EnumFacing.blockDirection(): BlockDirection {
    return ItemInterop.blockDirection(this)
}

fun BlockDirection.blockDirection(): EnumFacing {
    return ItemInterop.blockDirection(this)
}

fun MiningToolCategory.toolClass(): String {
    return ItemInterop.toolClass(this)
}
