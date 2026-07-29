/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

import heckerpowered.bridge.adapter.BridgeAccess
import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.resources.Identifier

interface ItemAccess : BridgeAccess {
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
