/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.item.*
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.platform.interop.asHost
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.*
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.BlockHitResult

object ItemAccessImpl {
    @JvmStatic
    fun identifier(item: Item): Identifier {
        return BuiltInRegistries.ITEM.getKey(item).asView()
    }

    @JvmStatic
    fun form(item: Item): ItemForm {
        return when (item) {
            is PickaxeItem -> ItemForm.Pickaxe
            is AxeItem -> ItemForm.Axe
            is ShovelItem -> ItemForm.Shovel
            is HoeItem -> ItemForm.Hoe
            is SwordItem -> ItemForm.Sword
            is ArmorItem -> item.form()
            else -> ItemForm.Regular
        }
    }

    @JvmStatic
    fun use(item: Item, world: WorldAccess, user: EntityAccess, hand: Hand): ItemInteractionResult {
        val player = user.asHost() as? Player ?: return ItemInteractionResult.Pass
        return item.use(world.asHost(), player, hand.asHost()).result.asView()
    }

    @JvmStatic
    fun useOnBlock(item: Item, stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, hand: Hand, position: BlockPositionView, face: BlockDirection, hitPosition: VectorView): ItemInteractionResult {
        val player = user.asHost() as? Player ?: return ItemInteractionResult.Pass
        val hit = BlockHitResult(hitPosition.asHost(), face.asHost(), position.asHost(), false)
        val context = UseOnContext(world.asHost(), player, hand.asHost(), stack.asHost(), hit)
        return item.useOn(context).asView()
    }

    @JvmStatic
    fun interactLivingEntity(item: Item, stack: ItemStackAccess, user: EntityAccess, target: EntityAccess, hand: Hand): ItemInteractionResult {
        val player = user.asHost() as? Player ?: return ItemInteractionResult.Pass
        val livingTarget = target.asHost() as? LivingEntity ?: return ItemInteractionResult.Pass
        return item.interactLivingEntity(stack.asHost(), player, livingTarget, hand.asHost()).asView()
    }

    @JvmStatic
    fun onUseTick(item: Item, stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, remainingUseTicks: Int) {
        val livingUser = user.asHost() as? LivingEntity ?: return
        item.onUseTick(world.asHost(), livingUser, stack.asHost(), remainingUseTicks)
    }

    @JvmStatic
    fun finishUsing(item: Item, stack: ItemStackAccess, world: WorldAccess, user: EntityAccess): ItemStackAccess {
        val livingUser = user.asHost() as? LivingEntity ?: return stack
        return item.finishUsingItem(stack.asHost(), world.asHost(), livingUser).asView()
    }

    @Suppress("SameReturnValue")
    @JvmStatic
    fun releaseUsing(item: Item, stack: ItemStackAccess, world: WorldAccess, user: EntityAccess, remainingUseTicks: Int): Boolean {
        val livingUser = user.asHost() as? LivingEntity ?: return false
        item.releaseUsing(stack.asHost(), world.asHost(), livingUser, remainingUseTicks)
        return false
    }

    @JvmStatic
    fun inventoryTick(item: Item, stack: ItemStackAccess, world: WorldAccess, owner: EntityAccess, slotIndex: Int?, isSelected: Boolean) {
        item.inventoryTick(stack.asHost(), world.asHost(), owner.asHost(), slotIndex ?: -1, isSelected)
    }

    @JvmStatic
    fun onCrafted(item: Item, stack: ItemStackAccess, world: WorldAccess, player: EntityAccess?) {
        val nativeStack = stack.asHost()
        val nativeWorld = world.asHost()
        if (player == null) {
            item.onCraftedPostProcess(nativeStack, nativeWorld)
            return
        }

        val craftingPlayer = player.asHost() as? Player ?: return
        item.onCraftedBy(nativeStack, nativeWorld, craftingPlayer)
    }

    @JvmStatic
    fun getUseAnimation(item: Item, stack: ItemStackAccess): ItemUseAnimation {
        return item.getUseAnimation(stack.asHost()).asView()
    }

    @JvmStatic
    fun getUseDurationTicks(item: Item, stack: ItemStackAccess, user: EntityAccess?): Int {
        val livingUser = nullableJavaArgument(user?.asHost() as? LivingEntity)
        // Java permits a missing user here, and the bridge deliberately preserves that cross-version case.
        return item.getUseDuration(stack.asHost(), livingUser)
    }

    @JvmStatic
    fun getDestroySpeed(item: Item, stack: ItemStackAccess, blockState: BlockStateAccess): Double {
        return item.getDestroySpeed(stack.asHost(), blockState.asHost()).toDouble()
    }

    @JvmStatic
    fun canHarvest(item: Item, stack: ItemStackAccess, blockState: BlockStateAccess): Boolean {
        return item.isCorrectToolForDrops(stack.asHost(), blockState.asHost())
    }

    @JvmStatic
    fun getMiningLevel(item: Item): Int {
        if (form(item) !is ItemForm.MiningTool || item !is TieredItem) return 0
        val tier = item.tier
        if (tier == Tiers.NETHERITE || tier.incorrectBlocksForDrops == BlockTags.INCORRECT_FOR_NETHERITE_TOOL) return 4
        if (tier == Tiers.DIAMOND || tier.incorrectBlocksForDrops == BlockTags.INCORRECT_FOR_DIAMOND_TOOL) return 3
        if (tier == Tiers.IRON || tier.incorrectBlocksForDrops == BlockTags.INCORRECT_FOR_IRON_TOOL) return 2
        if (tier == Tiers.STONE || tier.incorrectBlocksForDrops == BlockTags.INCORRECT_FOR_STONE_TOOL) return 1
        return 0
    }

    @JvmStatic
    fun mineBlock(item: Item, stack: ItemStackAccess, world: WorldAccess, blockState: BlockStateAccess, position: BlockPositionView, miner: EntityAccess): Boolean {
        val livingMiner = miner.asHost() as? LivingEntity ?: return false
        return item.mineBlock(stack.asHost(), world.asHost(), blockState.asHost(), position.asHost(), livingMiner)
    }

    @JvmStatic
    fun hurtEnemy(item: Item, stack: ItemStackAccess, target: EntityAccess, attacker: EntityAccess): Boolean {
        val livingTarget = target.asHost() as? LivingEntity ?: return false
        val livingAttacker = attacker.asHost() as? LivingEntity ?: return false
        return item.hurtEnemy(stack.asHost(), livingTarget, livingAttacker)
    }

    @JvmStatic
    fun postHurtEnemy(item: Item, stack: ItemStackAccess, target: EntityAccess, attacker: EntityAccess) {
        val livingTarget = target.asHost() as? LivingEntity ?: return
        val livingAttacker = attacker.asHost() as? LivingEntity ?: return
        item.postHurtEnemy(stack.asHost(), livingTarget, livingAttacker)
    }

    @JvmStatic
    fun getEquipmentSlot(item: Item, stack: ItemStackAccess, requestedSlot: EquipmentSlot?, wearer: EntityAccess?): EquipmentSlot? {
        val nativeStack = stack.asHost()
        var nativeSlot = item.getEquipmentSlot(nativeStack)
        if (nativeSlot == null && item is ArmorItem) nativeSlot = item.equipmentSlot
        if (nativeSlot != null) return nativeSlot.asView()
        if (requestedSlot == null || wearer == null) return null

        val livingWearer = wearer.asHost() as? LivingEntity ?: return null
        if (!item.canEquip(nativeStack, requestedSlot.asHost(), livingWearer)) return null
        return requestedSlot
    }

    @JvmStatic
    fun getArmorProtectionPoints(item: Item, stack: ItemStackAccess, slot: EquipmentSlot?): Int {
        if (item is ArmorItem) {
            val armorSlot = item.equipmentSlot.asView() ?: return 0
            return if (slot == null || slot == armorSlot) item.defense else 0
        }
        return attributeAmount(stack.asHost(), slot, Attributes.ARMOR).toInt()
    }

    @JvmStatic
    fun getArmorToughnessPoints(item: Item, stack: ItemStackAccess, slot: EquipmentSlot?): Double {
        if (item is ArmorItem) {
            val armorSlot = item.equipmentSlot.asView() ?: return 0.0
            return if (slot == null || slot == armorSlot) item.toughness.toDouble() else 0.0
        }
        return attributeAmount(stack.asHost(), slot, Attributes.ARMOR_TOUGHNESS)
    }

    private fun attributeAmount(stack: ItemStack, slot: EquipmentSlot?, attribute: Holder<Attribute>): Double {
        val nativeSlot = slot?.asHost() ?: return 0.0
        var amount = 0.0
        for (entry in stack.attributeModifiers.modifiers()) {
            if (!entry.slot().test(nativeSlot)) continue
            if (entry.attribute() != attribute) continue
            if (entry.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) continue
            amount += entry.modifier().amount()
        }
        return amount
    }

    private fun ArmorItem.form(): ItemForm {
        return when (type) {
            ArmorItem.Type.HELMET -> ItemForm.Helmet
            ArmorItem.Type.CHESTPLATE -> ItemForm.Chestplate
            ArmorItem.Type.LEGGINGS -> ItemForm.Leggings
            ArmorItem.Type.BOOTS -> ItemForm.Boots
            ArmorItem.Type.BODY -> ItemForm.Regular
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <Value> nullableJavaArgument(value: Value?): Value {
        return value as Value
    }
}
