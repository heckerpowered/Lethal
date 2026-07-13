/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import heckerpowered.bridge.adapter.item.*
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabRegistry
import heckerpowered.bridge.math.Geometry
import heckerpowered.lethal.platform.interop.*
import net.minecraft.block.state.IBlockState
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Items
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.EnumAction
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Forge 1.12.2 item materialized from an [ItemBlueprint].
 *
 * Native item hooks delegate into the blueprint. ItemMixin does the opposite for ordinary native items.
 */
open class HostedItem(val blueprint: ItemBlueprint) : Item(), ItemAccess by blueprint {
    init {
        registryName = blueprint.identifier.identifier()

        setTranslationKey(blueprint.properties.descriptionKey ?: defaultDescriptionKey())
        setMaxStackSize(blueprint.properties.effectiveMaxStackCount)
        setMaxDamage(blueprint.properties.maxDamagePoints)
        setCraftingRemainingItem()
        setPrimaryCreativeModeTab()
    }

    override fun onItemRightClick(world: World, player: EntityPlayer, hand: EnumHand): ActionResult<ItemStack> {
        val stack = player.getHeldItem(hand)
        val result = blueprint.use(stack.stack(), world.world(), player.entity(), hand.hand())

        return ActionResult(result.interactionResult(), stack)
    }

    override fun onItemUse(player: EntityPlayer, world: World, position: BlockPos, hand: EnumHand, facing: EnumFacing, hitOffsetX: Float, hitOffsetY: Float, hitOffsetZ: Float): EnumActionResult {
        val stack = player.getHeldItem(hand)
        val blockState = world.getBlockState(position)
        val result = blueprint.useOnBlock(stack.stack(), world.world(), player.entity(), hand.hand(), position.blockPosition(), blockState.blockState(world, position), facing.blockDirection(), Geometry.vector(hitOffsetX.toDouble(), hitOffsetY.toDouble(), hitOffsetZ.toDouble()))

        return result.interactionResult()
    }

    override fun itemInteractionForEntity(stack: ItemStack, player: EntityPlayer, target: EntityLivingBase, hand: EnumHand): Boolean {
        val result = blueprint.interactLivingEntity(stack.stack(), player.world.world(), player.entity(), target.entity(), hand.hand())

        return result != ItemInteractionResult.Pass
    }

    override fun onUsingTick(stack: ItemStack, player: EntityLivingBase, count: Int) {
        blueprint.onUseTick(stack.stack(), player.world.world(), player.entity(), count)
    }

    override fun onItemUseFinish(stack: ItemStack, world: World, livingEntity: EntityLivingBase): ItemStack {
        val result = blueprint.finishUsing(stack.stack(), world.world(), livingEntity.entity())

        return result.stack()
    }

    override fun onPlayerStoppedUsing(stack: ItemStack, world: World, livingEntity: EntityLivingBase, remainingUseTicks: Int) {
        blueprint.releaseUsing(stack.stack(), world.world(), livingEntity.entity(), remainingUseTicks)
    }

    override fun onUpdate(stack: ItemStack, world: World, owner: Entity, slotIndex: Int, isSelected: Boolean) {
        blueprint.inventoryTick(stack.stack(), world.world(), owner.entity(), if (isSelected) EquipmentSlot.MainHand else null, slotIndex, isSelected)
    }

    override fun onCreated(stack: ItemStack, world: World, player: EntityPlayer) {
        blueprint.onCrafted(stack.stack(), world.world(), player.entity())
    }

    override fun getItemUseAction(stack: ItemStack): EnumAction {
        return blueprint.getUseAnimation(stack.stack()).useAnimation()
    }

    override fun getCreativeTabs(): Array<CreativeTabs> {
        val tabs = CreativeModeTabRegistry.findAll(blueprint)
        if (tabs.isEmpty()) return super.getCreativeTabs()
        return tabs.map { it.creativeModeTab() }.toTypedArray()
    }

    override fun getMaxItemUseDuration(stack: ItemStack): Int {
        return blueprint.getUseDurationTicks(stack.stack(), null)
    }

    override fun getDestroySpeed(stack: ItemStack, state: IBlockState): Float {
        return blueprint.getDestroySpeed(stack.stack(), state.blockState()).toFloat()
    }

    override fun canHarvestBlock(state: IBlockState): Boolean {
        return canHarvestBlock(state, ItemStack(this))
    }

    override fun canHarvestBlock(state: IBlockState, stack: ItemStack): Boolean {
        return blueprint.canHarvest(stack.stack(), state.blockState())
    }

    override fun getHarvestLevel(stack: ItemStack, toolClass: String, player: EntityPlayer?, blockState: IBlockState?): Int {
        val itemForm = blueprint.form
        if (itemForm !is ItemForm.MiningTool) return super.getHarvestLevel(stack, toolClass, player, blockState)
        if (itemForm.miningCategory.toolClass() != toolClass) return -1

        return blueprint.getMiningLevel(stack.stack(), blockState?.blockState(), player?.entity())
    }

    override fun getToolClasses(stack: ItemStack): Set<String> {
        val itemForm = blueprint.form
        if (itemForm !is ItemForm.MiningTool) return super.getToolClasses(stack)
        return setOf(itemForm.miningCategory.toolClass())
    }

    override fun onBlockDestroyed(stack: ItemStack, world: World, state: IBlockState, position: BlockPos, livingEntity: EntityLivingBase): Boolean {
        return blueprint.mineBlock(stack.stack(), world.world(), state.blockState(world, position), position.blockPosition(), livingEntity.entity())
    }

    override fun hitEntity(stack: ItemStack, target: EntityLivingBase, attacker: EntityLivingBase): Boolean {
        val result = blueprint.hurtEnemy(stack.stack(), attacker.world.world(), target.entity(), attacker.entity())
        blueprint.postHurtEnemy(stack.stack(), attacker.world.world(), target.entity(), attacker.entity())

        return result
    }

    override fun isValidArmor(stack: ItemStack, armorType: EntityEquipmentSlot, entity: Entity): Boolean {
        val equipmentSlot = blueprint.getEquipmentSlot(stack.stack(), armorType.equipmentSlot(), entity.entity())
        return equipmentSlot.equipmentSlot() == armorType
    }

    override fun getEquipmentSlot(stack: ItemStack): EntityEquipmentSlot? {
        val equipmentSlot = blueprint.getEquipmentSlot(stack.stack(), null, null)
        return equipmentSlot.equipmentSlot()
    }

    override fun getAttributeModifiers(slot: EntityEquipmentSlot, stack: ItemStack): Multimap<String, AttributeModifier> {
        val modifiers = HashMultimap.create(super.getAttributeModifiers(slot, stack))
        val equipmentSlot = blueprint.getEquipmentSlot(stack.stack(), slot.equipmentSlot(), null)

        if (equipmentSlot.equipmentSlot() != slot) return modifiers

        val protectionPoints = blueprint.getArmorProtectionPoints(stack.stack(), equipmentSlot, null)
        val toughnessPoints = blueprint.getArmorToughnessPoints(stack.stack(), equipmentSlot, null)

        if (protectionPoints > 0) {
            modifiers.put(SharedMonsterAttributes.ARMOR.name, AttributeModifier(attributeModifierId("armor", slot), "Armor modifier", protectionPoints.toDouble(), 0))
        }

        if (toughnessPoints > 0.0) {
            modifiers.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.name, AttributeModifier(attributeModifierId("armor_toughness", slot), "Armor toughness", toughnessPoints, 0))
        }

        return modifiers
    }

    private fun defaultDescriptionKey(): String {
        val namespace = blueprint.identifier.namespace
        val path = blueprint.identifier.path.replace('/', '.')
        return "$namespace.$path"
    }

    private fun setCraftingRemainingItem() {
        val remainingIdentifier = blueprint.properties.craftingRemainingItem ?: return
        val remainingItem = REGISTRY.getObject(remainingIdentifier.identifier())
        if (remainingItem == Items.AIR) return

        setContainerItem(remainingItem)
    }

    private fun setPrimaryCreativeModeTab() {
        val primaryTab = CreativeModeTabRegistry.findAll(blueprint).firstOrNull() ?: return
        setCreativeTab(primaryTab.creativeModeTab())
    }

    private fun attributeModifierId(name: String, slot: EntityEquipmentSlot): UUID {
        val seed = "${blueprint.identifier.asString()}:$name:${slot.name}"
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
    }
}
