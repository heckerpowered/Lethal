/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.item

import heckerpowered.lethal.bridge.adapter.block.BlockStateAccess
import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.bridge.adapter.world.WorldAccess
import heckerpowered.lethal.bridge.math.BlockDirection
import heckerpowered.lethal.bridge.math.BlockPositionView
import heckerpowered.lethal.bridge.math.VectorView
import heckerpowered.lethal.bridge.resources.Identifier

interface ItemAccess {
    val identifier: Identifier
    val form: ItemForm

    fun use(stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, hand: Hand): ItemInteractionResult {
        return ItemInteractionResult.Pass
    }

    fun useOnBlock(stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, hand: Hand, position: BlockPositionView, blockState: BlockStateAccess, face: BlockDirection, hitPosition: VectorView): ItemInteractionResult {
        return ItemInteractionResult.Pass
    }

    fun interactLivingEntity(stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, target: EntityAccess, hand: Hand): ItemInteractionResult {
        return ItemInteractionResult.Pass
    }

    fun onUseTick(stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, remainingUseTicks: Int) {
    }

    fun finishUsing(stack: ItemStackAccess, world: WorldAccess, user: EntityAccess): ItemStackAccess {
        return stack
    }

    fun releaseUsing(stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, remainingUseTicks: Int): Boolean {
        return false
    }

    fun inventoryTick(stack: ItemStackAccess, world: WorldAccess, owner: EntityAccess, slot: EquipmentSlot?, slotIndex: Int?, isSelected: Boolean) {
    }

    fun onCrafted(stack: ItemStackAccess, world: WorldAccess, player: EntityAccess?) {
    }

    fun getUseAnimation(stack: ItemStackAccess): ItemUseAnimation {
        return ItemUseAnimation.None
    }

    fun getUseDurationTicks(stack: ItemStackAccess, user: EntityAccess?): Int {
        return 0
    }

    fun getDestroySpeed(stack: ItemStackAccess, blockState: BlockStateAccess): Double {
        return 1.0
    }

    fun canHarvest(stack: ItemStackAccess, blockState: BlockStateAccess): Boolean {
        return false
    }

    fun getMiningLevel(stack: ItemStackAccess, blockState: BlockStateAccess?, user: EntityAccess?): Int {
        return 0
    }

    fun mineBlock(stack: ItemStackAccess, world: WorldAccess, blockState: BlockStateAccess, position: BlockPositionView, miner: EntityAccess): Boolean {
        return false
    }

    fun hurtEnemy(stack: ItemStackAccess, world: WorldAccess, target: EntityAccess, attacker: EntityAccess): Boolean {
        return false
    }

    fun postHurtEnemy(stack: ItemStackAccess, world: WorldAccess, target: EntityAccess, attacker: EntityAccess) {
    }

    fun getEquipmentSlot(stack: ItemStackAccess, slot: EquipmentSlot?, wearer: EntityAccess?): EquipmentSlot? {
        val itemForm = form
        if (itemForm !is ItemForm.Armor) return null
        return itemForm.equipmentSlot
    }

    fun getArmorProtectionPoints(stack: ItemStackAccess, slot: EquipmentSlot?, wearer: EntityAccess?): Int {
        return 0
    }

    fun getArmorToughnessPoints(stack: ItemStackAccess, slot: EquipmentSlot?, wearer: EntityAccess?): Double {
        return 0.0
    }
}

interface ItemBlueprint : ItemAccess {
    val properties: ItemProperties

    override fun getUseDurationTicks(stack: ItemStackAccess, user: EntityAccess?): Int {
        return properties.maxUseDurationTicks
    }
}

data class SimpleItemBlueprint(
    override val identifier: Identifier,
    override val form: ItemForm = ItemForm.Regular,
    override val properties: ItemProperties = ItemProperties(),
) : ItemBlueprint
