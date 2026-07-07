/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import com.google.common.collect.Multimap
import heckerpowered.lethal.bridge.adapter.block.BlockStateAccess
import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.item.*
import heckerpowered.lethal.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.bridge.adapter.world.WorldAccess
import heckerpowered.lethal.bridge.math.BlockDirection
import heckerpowered.lethal.bridge.math.BlockPositionView
import heckerpowered.lethal.bridge.math.VectorView
import heckerpowered.lethal.platform.interop.*
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.*

/**
 * Native implementation behind ItemMixin's soft ItemAccess methods.
 *
 * HostedItem delegates native hooks into a blueprint; this object delegates ItemAccess calls back into native Item hooks.
 */
object ItemAccessImpl {
    @JvmStatic
    fun use(item: Item, world: WorldAccess, user: EntityAccess, hand: Hand): ItemInteractionResult {
        val nativeWorld = world.world()
        val nativeUser = user.entityOrNull() as? EntityPlayer ?: return ItemInteractionResult.Pass
        val result = item.onItemRightClick(nativeWorld, nativeUser, hand.hand())

        return result.type.interactionResult()
    }

    @JvmStatic
    fun useOnBlock(item: Item, world: WorldAccess, user: EntityAccess, hand: Hand, position: BlockPositionView, face: BlockDirection, hitPosition: VectorView): ItemInteractionResult {
        val nativeWorld = world.world()
        val nativeUser = user.entityOrNull() as? EntityPlayer ?: return ItemInteractionResult.Pass
        val result = item.onItemUse(nativeUser, nativeWorld, position.blockPosition(), hand.hand(), face.blockDirection(), hitPosition.x.toFloat(), hitPosition.y.toFloat(), hitPosition.z.toFloat())

        return result.interactionResult()
    }

    @JvmStatic
    fun interactLivingEntity(item: Item, itemStackAccess: ItemStackAccess, userAccess: EntityAccess, targetAccess: EntityAccess, itemHand: Hand): ItemInteractionResult {
        val nativeStack = itemStackAccess.stack()
        val nativeUser = userAccess.entityOrNull() as? EntityPlayer ?: return ItemInteractionResult.Pass
        val nativeTarget = targetAccess.entityOrNull() as? EntityLivingBase ?: return ItemInteractionResult.Pass
        val result = item.itemInteractionForEntity(nativeStack, nativeUser, nativeTarget, itemHand.hand())
        if (result) return ItemInteractionResult.Success

        return ItemInteractionResult.Pass
    }

    @JvmStatic
    fun onUseTick(item: Item, itemStackAccess: ItemStackAccess, userAccess: EntityAccess, remainingUseTicks: Int) {
        val nativeStack = itemStackAccess.stack()
        val nativeUser = userAccess.entityOrNull() as? EntityLivingBase ?: return

        item.onUsingTick(nativeStack, nativeUser, remainingUseTicks)
    }

    @JvmStatic
    fun finishUsing(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, userAccess: EntityAccess): ItemStackAccess {
        val nativeStack = itemStackAccess.stack()
        val nativeWorld = worldAccess.world()
        val nativeUser = userAccess.entityOrNull() as? EntityLivingBase ?: return itemStackAccess
        val result = item.onItemUseFinish(nativeStack, nativeWorld, nativeUser)

        return result.stack()
    }

    @Suppress("SameReturnValue")
    @JvmStatic
    fun releaseUsing(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, userAccess: EntityAccess, remainingUseTicks: Int): Boolean {
        val nativeStack = itemStackAccess.stack()
        val nativeWorld = worldAccess.world()
        val nativeUser = userAccess.entityOrNull() as? EntityLivingBase ?: return false

        item.onPlayerStoppedUsing(nativeStack, nativeWorld, nativeUser, remainingUseTicks)
        return false
    }

    @JvmStatic
    fun inventoryTick(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, ownerAccess: EntityAccess, slotIndex: Int?, isSelected: Boolean) {
        val nativeStack = itemStackAccess.stack()
        val nativeWorld = worldAccess.world()
        val nativeOwner = ownerAccess.entityOrNull() ?: return

        item.onUpdate(nativeStack, nativeWorld, nativeOwner, slotIndex ?: -1, isSelected)
    }

    @JvmStatic
    fun onCrafted(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, playerAccess: EntityAccess?) {
        val nativeStack = itemStackAccess.stack()
        val nativeWorld = worldAccess.world()
        val nativePlayer = playerAccess.entityOrNull() as? EntityPlayer ?: return

        item.onCreated(nativeStack, nativeWorld, nativePlayer)
    }

    @JvmStatic
    fun getUseAnimation(item: Item, itemStackAccess: ItemStackAccess): ItemUseAnimation {
        val nativeStack = itemStackAccess.stack()
        return item.getItemUseAction(nativeStack).useAnimation()
    }

    @JvmStatic
    fun getUseDurationTicks(item: Item, itemStackAccess: ItemStackAccess): Int {
        val nativeStack = itemStackAccess.stack()
        return item.getMaxItemUseDuration(nativeStack)
    }

    @JvmStatic
    fun getDestroySpeed(item: Item, itemStackAccess: ItemStackAccess, blockStateAccess: BlockStateAccess): Double {
        val nativeStack = itemStackAccess.stack()
        val nativeBlockState = blockStateAccess.blockState()

        return item.getDestroySpeed(nativeStack, nativeBlockState).toDouble()
    }

    @JvmStatic
    fun canHarvest(item: Item, itemStackAccess: ItemStackAccess, blockStateAccess: BlockStateAccess): Boolean {
        val nativeBlockState = blockStateAccess.blockState()
        val nativeStack = itemStackAccess.stack()

        return item.canHarvestBlock(nativeBlockState, nativeStack)
    }

    @JvmStatic
    fun getMiningLevel(item: Item, itemStackAccess: ItemStackAccess, blockStateAccess: BlockStateAccess?, userAccess: EntityAccess?): Int {
        val nativeStack = itemStackAccess.stack()
        val itemForm = form(item) as? ItemForm.MiningTool ?: return 0
        val nativeUser = userAccess.entityOrNull()
        val nativePlayer = nativeUser as? EntityPlayer

        return item.getHarvestLevel(nativeStack, itemForm.miningCategory.toolClass(), nativePlayer, blockStateAccess.blockStateOrNull())
    }

    @JvmStatic
    fun mineBlock(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, blockStateAccess: BlockStateAccess, position: BlockPositionView, minerAccess: EntityAccess): Boolean {
        val nativeStack = itemStackAccess.stack()
        val nativeWorld = worldAccess.world()
        val nativeBlockState = blockStateAccess.blockState()
        val nativeMiner = minerAccess.entityOrNull() as? EntityLivingBase ?: return false

        return item.onBlockDestroyed(nativeStack, nativeWorld, nativeBlockState, position.blockPosition(), nativeMiner)
    }

    @JvmStatic
    fun hurtEnemy(item: Item, itemStackAccess: ItemStackAccess, targetAccess: EntityAccess, attackerAccess: EntityAccess): Boolean {
        val nativeStack = itemStackAccess.stack()
        val nativeTarget = targetAccess.entityOrNull() as? EntityLivingBase ?: return false
        val nativeAttacker = attackerAccess.entityOrNull() as? EntityLivingBase ?: return false

        return item.hitEntity(nativeStack, nativeTarget, nativeAttacker)
    }

    /**
     * Forge 1.12 has no separate post-hit hook; hitEntity already handled the item-side behavior.
     */
    @Suppress("unused")
    @JvmStatic
    fun postHurtEnemy(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, targetAccess: EntityAccess, attackerAccess: EntityAccess) {
    }

    @JvmStatic
    fun getEquipmentSlot(item: Item, itemStackAccess: ItemStackAccess, slot: EquipmentSlot?, wearerAccess: EntityAccess?): EquipmentSlot? {
        val nativeStack = itemStackAccess.stack()
        val stackSlot = item.getEquipmentSlot(nativeStack)
        if (stackSlot != null) return stackSlot.equipmentSlot()
        if (item is ItemArmor) return item.armorType.equipmentSlot()

        val nativeWearer = wearerAccess.entityOrNull() ?: return null
        val requestedSlot = slot.equipmentSlot() ?: return null
        if (!item.isValidArmor(nativeStack, requestedSlot, nativeWearer)) return null

        return slot
    }

    @JvmStatic
    fun getArmorProtectionPoints(item: Item, itemStackAccess: ItemStackAccess, slot: EquipmentSlot?): Int {
        if (item is ItemArmor) return armorProtectionPoints(item, slot)

        val nativeStack = itemStackAccess.stack()
        return attributeAmount(item, nativeStack, slot, SharedMonsterAttributes.ARMOR.name).toInt()
    }

    @JvmStatic
    fun getArmorToughnessPoints(item: Item, itemStackAccess: ItemStackAccess, slot: EquipmentSlot?): Double {
        if (item is ItemArmor) return armorToughnessPoints(item, slot)

        val nativeStack = itemStackAccess.stack()
        return attributeAmount(item, nativeStack, slot, SharedMonsterAttributes.ARMOR_TOUGHNESS.name)
    }

    @JvmStatic
    fun form(item: Item): ItemForm {
        return when (item) {
            is ItemPickaxe -> ItemForm.Pickaxe
            is ItemAxe -> ItemForm.Axe
            is ItemSpade -> ItemForm.Shovel
            is ItemHoe -> ItemForm.Hoe
            is ItemSword -> ItemForm.Sword
            is ItemArmor -> armorForm(item)
            else -> ItemForm.Regular
        }
    }

    fun armorProtectionPoints(item: ItemArmor, slot: EquipmentSlot?): Int {
        val nativeSlot = slot.equipmentSlot()
        if (nativeSlot != null && nativeSlot != item.armorType) return 0
        return item.damageReduceAmount
    }

    @JvmStatic
    fun armorToughnessPoints(item: ItemArmor, slot: EquipmentSlot?): Double {
        val nativeSlot = slot.equipmentSlot()
        if (nativeSlot != null && nativeSlot != item.armorType) return 0.0
        return item.toughness.toDouble()
    }

    @JvmStatic
    fun attributeAmount(item: Item, stack: ItemStack, slot: EquipmentSlot?, attributeName: String): Double {
        val nativeSlot = slot.equipmentSlot() ?: return 0.0

        var amount = 0.0
        val modifiers: Multimap<String, AttributeModifier> = item.getAttributeModifiers(nativeSlot, stack)
        for (modifier in modifiers[attributeName]) {
            if (modifier.operation == 0) {
                amount += modifier.amount
            }
        }
        return amount
    }

    private fun armorForm(item: ItemArmor): ItemForm {
        return when (item.armorType) {
            EntityEquipmentSlot.HEAD -> ItemForm.Helmet
            EntityEquipmentSlot.CHEST -> ItemForm.Chestplate
            EntityEquipmentSlot.LEGS -> ItemForm.Leggings
            EntityEquipmentSlot.FEET -> ItemForm.Boots
            else -> ItemForm.Regular
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
