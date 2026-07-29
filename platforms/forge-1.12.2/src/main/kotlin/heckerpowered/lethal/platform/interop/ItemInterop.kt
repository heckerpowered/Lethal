/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.requireAccess
import heckerpowered.bridge.requireHost
import heckerpowered.bridge.adapter.item.*
import heckerpowered.bridge.resources.Identifier
import net.minecraft.init.Items
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.EnumAction
import net.minecraft.item.Item
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand

object ItemInterop {
    @JvmStatic
    fun asView(item: Item): ItemAccess = requireAccess(item)

    @JvmStatic
    fun asHost(slot: EquipmentSlot): EntityEquipmentSlot = slot.asHost()

    @JvmStatic
    fun identifier(item: Item): Identifier {
        return item.registryName?.asView() ?: error("Item is not registered")
    }
}

fun ItemAccess.asHost(): Item = requireHost(this)

fun ItemBlueprint.asHost(): Item {
    val item = Item.REGISTRY.getObject(identifier.asHost())
    return item.takeUnless { it == Items.AIR } ?: error("Item has not been registered: ${identifier.asString()}")
}

fun Item.asView(): ItemAccess = requireAccess(this)

fun Item.identifier(): Identifier = ItemInterop.identifier(this)

fun EnumHand.asView(): Hand {
    return when (this) {
        EnumHand.MAIN_HAND -> Hand.Main
        EnumHand.OFF_HAND -> Hand.Off
    }
}

fun Hand.asHost(): EnumHand {
    return when (this) {
        Hand.Main -> EnumHand.MAIN_HAND
        Hand.Off -> EnumHand.OFF_HAND
    }
}

fun EntityEquipmentSlot.asView(): EquipmentSlot {
    return when (this) {
        EntityEquipmentSlot.MAINHAND -> EquipmentSlot.MainHand
        EntityEquipmentSlot.OFFHAND -> EquipmentSlot.OffHand
        EntityEquipmentSlot.FEET -> EquipmentSlot.Feet
        EntityEquipmentSlot.LEGS -> EquipmentSlot.Legs
        EntityEquipmentSlot.CHEST -> EquipmentSlot.Chest
        EntityEquipmentSlot.HEAD -> EquipmentSlot.Head
    }
}

fun EquipmentSlot.asHost(): EntityEquipmentSlot {
    return when (this) {
        EquipmentSlot.MainHand -> EntityEquipmentSlot.MAINHAND
        EquipmentSlot.OffHand -> EntityEquipmentSlot.OFFHAND
        EquipmentSlot.Feet -> EntityEquipmentSlot.FEET
        EquipmentSlot.Legs -> EntityEquipmentSlot.LEGS
        EquipmentSlot.Chest -> EntityEquipmentSlot.CHEST
        EquipmentSlot.Head -> EntityEquipmentSlot.HEAD
    }
}

fun ItemInteractionResult.asHost(): EnumActionResult {
    return when (this) {
        ItemInteractionResult.Pass -> EnumActionResult.PASS
        ItemInteractionResult.Success, ItemInteractionResult.Consume -> EnumActionResult.SUCCESS
        ItemInteractionResult.Fail -> EnumActionResult.FAIL
    }
}

fun EnumActionResult.asView(): ItemInteractionResult {
    return when (this) {
        EnumActionResult.PASS -> ItemInteractionResult.Pass
        EnumActionResult.SUCCESS -> ItemInteractionResult.Success
        EnumActionResult.FAIL -> ItemInteractionResult.Fail
    }
}

fun ItemUseAnimation.asHost(): EnumAction {
    return when (this) {
        ItemUseAnimation.None -> EnumAction.NONE
        ItemUseAnimation.Eat -> EnumAction.EAT
        ItemUseAnimation.Drink -> EnumAction.DRINK
        ItemUseAnimation.Block -> EnumAction.BLOCK
        ItemUseAnimation.Bow, ItemUseAnimation.Spear -> EnumAction.BOW
    }
}

fun EnumAction.asView(): ItemUseAnimation {
    return when (this) {
        EnumAction.NONE -> ItemUseAnimation.None
        EnumAction.EAT -> ItemUseAnimation.Eat
        EnumAction.DRINK -> ItemUseAnimation.Drink
        EnumAction.BLOCK -> ItemUseAnimation.Block
        EnumAction.BOW -> ItemUseAnimation.Bow
    }
}

fun MiningToolCategory.toolClass(): String {
    return when (this) {
        MiningToolCategory.Pickaxe -> "pickaxe"
        MiningToolCategory.Axe -> "axe"
        MiningToolCategory.Shovel -> "shovel"
        MiningToolCategory.Hoe -> "hoe"
    }
}
