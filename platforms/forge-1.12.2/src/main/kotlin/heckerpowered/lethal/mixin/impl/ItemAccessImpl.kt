/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import com.google.common.collect.Multimap
import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.item.*
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.platform.adapter.item.HostedItem
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
        val nativeWorld = world.asHost()
        val nativeUser = user.asHost() as? EntityPlayer ?: return ItemInteractionResult.Pass
        val result = item.onItemRightClick(nativeWorld, nativeUser, hand.asHost())

        return result.type.asView()
    }

    @JvmStatic
    fun useOnBlock(item: Item, world: WorldAccess, user: EntityAccess, hand: Hand, position: BlockPositionView, face: BlockDirection, hitPosition: VectorView): ItemInteractionResult {
        val nativeWorld = world.asHost()
        val nativeUser = user.asHost() as? EntityPlayer ?: return ItemInteractionResult.Pass
        val hitOffsetX = hitPosition.x.toFloat()
        val hitOffsetY = hitPosition.y.toFloat()
        val hitOffsetZ = hitPosition.z.toFloat()
        val result = item.onItemUse(nativeUser, nativeWorld, position.asHost(), hand.asHost(), face.asHost(), hitOffsetX, hitOffsetY, hitOffsetZ)

        return result.asView()
    }

    @JvmStatic
    fun interactLivingEntity(item: Item, itemStackAccess: ItemStackAccess, userAccess: EntityAccess, targetAccess: EntityAccess, itemHand: Hand): ItemInteractionResult {
        val nativeStack = itemStackAccess.asHost()
        val nativeUser = userAccess.asHost() as? EntityPlayer ?: return ItemInteractionResult.Pass
        val nativeTarget = targetAccess.asHost() as? EntityLivingBase ?: return ItemInteractionResult.Pass
        val result = item.itemInteractionForEntity(nativeStack, nativeUser, nativeTarget, itemHand.asHost())
        if (result) return ItemInteractionResult.Success

        return ItemInteractionResult.Pass
    }

    @JvmStatic
    fun onUseTick(item: Item, itemStackAccess: ItemStackAccess, userAccess: EntityAccess, remainingUseTicks: Int) {
        val nativeStack = itemStackAccess.asHost()
        val nativeUser = userAccess.asHost() as? EntityLivingBase ?: return

        item.onUsingTick(nativeStack, nativeUser, remainingUseTicks)
    }

    @JvmStatic
    fun finishUsing(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, userAccess: EntityAccess): ItemStackAccess {
        val nativeStack = itemStackAccess.asHost()
        val nativeWorld = worldAccess.asHost()
        val nativeUser = userAccess.asHost() as? EntityLivingBase ?: return itemStackAccess
        val result = item.onItemUseFinish(nativeStack, nativeWorld, nativeUser)

        return result.asView()
    }

    @Suppress("SameReturnValue")
    @JvmStatic
    fun releaseUsing(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, userAccess: EntityAccess, remainingUseTicks: Int): Boolean {
        val nativeStack = itemStackAccess.asHost()
        val nativeWorld = worldAccess.asHost()
        val nativeUser = userAccess.asHost() as? EntityLivingBase ?: return false

        item.onPlayerStoppedUsing(nativeStack, nativeWorld, nativeUser, remainingUseTicks)
        return false
    }

    @JvmStatic
    fun inventoryTick(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, ownerAccess: EntityAccess, slotIndex: Int?, isSelected: Boolean) {
        val nativeStack = itemStackAccess.asHost()
        val nativeWorld = worldAccess.asHost()
        val nativeOwner = ownerAccess.asHost()

        item.onUpdate(nativeStack, nativeWorld, nativeOwner, slotIndex ?: -1, isSelected)
    }

    @JvmStatic
    fun onCrafted(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, playerAccess: EntityAccess?) {
        val nativeStack = itemStackAccess.asHost()
        val nativeWorld = worldAccess.asHost()
        val nativePlayer = playerAccess?.asHost() as? EntityPlayer ?: return

        item.onCreated(nativeStack, nativeWorld, nativePlayer)
    }

    @JvmStatic
    fun getUseAnimation(item: Item, itemStackAccess: ItemStackAccess): ItemUseAnimation {
        val nativeStack = itemStackAccess.asHost()
        return item.getItemUseAction(nativeStack).asView()
    }

    @JvmStatic
    fun getUseDurationTicks(item: Item, itemStackAccess: ItemStackAccess): Int {
        val nativeStack = itemStackAccess.asHost()
        return item.getMaxItemUseDuration(nativeStack)
    }

    @JvmStatic
    fun getDestroySpeed(item: Item, itemStackAccess: ItemStackAccess, blockStateAccess: BlockStateAccess): Double {
        val nativeStack = itemStackAccess.asHost()
        val nativeBlockState = blockStateAccess.asHost()

        return item.getDestroySpeed(nativeStack, nativeBlockState).toDouble()
    }

    @JvmStatic
    fun canHarvest(item: Item, itemStackAccess: ItemStackAccess, blockStateAccess: BlockStateAccess): Boolean {
        val nativeBlockState = blockStateAccess.asHost()
        val nativeStack = itemStackAccess.asHost()

        return item.canHarvestBlock(nativeBlockState, nativeStack)
    }

    @JvmStatic
    fun getMiningLevel(item: Item, itemStackAccess: ItemStackAccess, blockStateAccess: BlockStateAccess?, userAccess: EntityAccess?): Int {
        val nativeStack = itemStackAccess.asHost()
        val itemForm = form(item) as? ItemForm.MiningTool ?: return 0
        val nativeUser = userAccess?.asHost()
        val nativePlayer = nativeUser as? EntityPlayer

        return item.getHarvestLevel(nativeStack, itemForm.miningCategory.toolClass(), nativePlayer, blockStateAccess?.asHost())
    }

    @JvmStatic
    fun mineBlock(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, blockStateAccess: BlockStateAccess, position: BlockPositionView, minerAccess: EntityAccess): Boolean {
        val nativeStack = itemStackAccess.asHost()
        val nativeWorld = worldAccess.asHost()
        val nativeBlockState = blockStateAccess.asHost()
        val nativeMiner = minerAccess.asHost() as? EntityLivingBase ?: return false

        return item.onBlockDestroyed(nativeStack, nativeWorld, nativeBlockState, position.asHost(), nativeMiner)
    }

    @JvmStatic
    fun hurtEnemy(item: Item, itemStackAccess: ItemStackAccess, targetAccess: EntityAccess, attackerAccess: EntityAccess): Boolean {
        val nativeStack = itemStackAccess.asHost()
        val nativeTarget = targetAccess.asHost() as? EntityLivingBase ?: return false
        val nativeAttacker = attackerAccess.asHost() as? EntityLivingBase ?: return false

        return item.hitEntity(nativeStack, nativeTarget, nativeAttacker)
    }

    // Forge 1.12 has no separate post-hit hook; hitEntity already handled the item-side behavior.
    @Suppress("unused")
    @JvmStatic
    fun postHurtEnemy(item: Item, itemStackAccess: ItemStackAccess, worldAccess: WorldAccess, targetAccess: EntityAccess, attackerAccess: EntityAccess) {
    }

    @JvmStatic
    fun getEquipmentSlot(item: Item, itemStackAccess: ItemStackAccess, slot: EquipmentSlot?, wearerAccess: EntityAccess?): EquipmentSlot? {
        val nativeStack = itemStackAccess.asHost()
        val stackSlot = item.getEquipmentSlot(nativeStack)
        if (stackSlot != null) return stackSlot.asView()
        if (item is ItemArmor) return item.armorType.asView()

        val nativeWearer = wearerAccess?.asHost() ?: return null
        val requestedSlot = slot?.asHost() ?: return null
        if (!item.isValidArmor(nativeStack, requestedSlot, nativeWearer)) return null

        return slot
    }

    @JvmStatic
    fun getArmorProtectionPoints(item: Item, itemStackAccess: ItemStackAccess, slot: EquipmentSlot?): Int {
        if (item is ItemArmor) return armorProtectionPoints(item, slot)

        val nativeStack = itemStackAccess.asHost()
        return attributeAmount(item, nativeStack, slot, SharedMonsterAttributes.ARMOR.name).toInt()
    }

    @JvmStatic
    fun getArmorToughnessPoints(item: Item, itemStackAccess: ItemStackAccess, slot: EquipmentSlot?): Double {
        if (item is ItemArmor) return armorToughnessPoints(item, slot)

        val nativeStack = itemStackAccess.asHost()
        return attributeAmount(item, nativeStack, slot, SharedMonsterAttributes.ARMOR_TOUGHNESS.name)
    }

    @JvmStatic
    fun form(item: Item): ItemForm {
        if (item is HostedItem) return item.blueprint.form
        if (item is ItemForm) return item

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
        val nativeSlot = slot?.asHost()
        if (nativeSlot != null && nativeSlot != item.armorType) return 0
        return item.damageReduceAmount
    }

    fun armorToughnessPoints(item: ItemArmor, slot: EquipmentSlot?): Double {
        val nativeSlot = slot?.asHost()
        if (nativeSlot != null && nativeSlot != item.armorType) return 0.0
        return item.toughness.toDouble()
    }

    fun attributeAmount(item: Item, stack: ItemStack, slot: EquipmentSlot?, attributeName: String): Double {
        val nativeSlot = slot?.asHost() ?: return 0.0

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
