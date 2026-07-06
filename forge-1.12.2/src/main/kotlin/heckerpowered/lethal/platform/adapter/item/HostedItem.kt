/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import heckerpowered.lethal.bridge.adapter.item.*
import heckerpowered.lethal.bridge.math.Geometry
import heckerpowered.lethal.platform.adapter.block.HostedBlockStateAccess
import heckerpowered.lethal.platform.adapter.world.HostedWorldAccess
import heckerpowered.lethal.platform.interop.GeometryInterop
import heckerpowered.lethal.platform.interop.IdentifierInterop
import heckerpowered.lethal.platform.interop.ItemInterop
import heckerpowered.lethal.platform.interop.ObjectInterop
import net.minecraft.block.state.IBlockState
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

open class HostedItem(val blueprint: ItemBlueprint) : Item(), ItemAccess by blueprint {
    init {
        registryName = IdentifierInterop.identifier(blueprint.identifier)

        setTranslationKey(blueprint.properties.descriptionKey ?: defaultDescriptionKey())
        setMaxStackSize(blueprint.properties.effectiveMaxStackCount)
        setMaxDamage(blueprint.properties.maxDamagePoints)
        setCraftingRemainingItem()
    }

    override fun onItemRightClick(world: World, player: EntityPlayer, hand: EnumHand): ActionResult<ItemStack> {
        val stack = player.getHeldItem(hand)
        val result = blueprint.use(stackAccess(stack), HostedWorldAccess(world), ObjectInterop.entity(player), ItemInterop.hand(hand))

        return ActionResult(ItemInterop.interactionResult(result), stack)
    }

    override fun onItemUse(player: EntityPlayer, world: World, position: BlockPos, hand: EnumHand, facing: EnumFacing, hitOffsetX: Float, hitOffsetY: Float, hitOffsetZ: Float): EnumActionResult {
        val stack = player.getHeldItem(hand)
        val blockState = world.getBlockState(position)
        val result = blueprint.useOnBlock(stackAccess(stack), HostedWorldAccess(world), ObjectInterop.entity(player), ItemInterop.hand(hand), GeometryInterop.blockPosition(position), HostedBlockStateAccess(blockState, world, position), ItemInterop.blockDirection(facing), Geometry.vector(hitOffsetX.toDouble(), hitOffsetY.toDouble(), hitOffsetZ.toDouble()))

        return ItemInterop.interactionResult(result)
    }

    override fun itemInteractionForEntity(
        stack: ItemStack,
        player: EntityPlayer,
        target: EntityLivingBase,
        hand: EnumHand,
    ): Boolean {
        val result = blueprint.interactLivingEntity(stackAccess(stack), HostedWorldAccess(player.world), ObjectInterop.entity(player), ObjectInterop.entity(target), ItemInterop.hand(hand))

        return result != ItemInteractionResult.Pass
    }

    override fun onUsingTick(stack: ItemStack, player: EntityLivingBase, count: Int) {
        blueprint.onUseTick(stackAccess(stack), HostedWorldAccess(player.world), ObjectInterop.entity(player), count)
    }

    override fun onItemUseFinish(stack: ItemStack, world: World, livingEntity: EntityLivingBase): ItemStack {
        val result = blueprint.finishUsing(stackAccess(stack), HostedWorldAccess(world), ObjectInterop.entity(livingEntity))

        return (result as? HostedItemStackAccess)?.stack ?: stack
    }

    override fun onPlayerStoppedUsing(stack: ItemStack, world: World, livingEntity: EntityLivingBase, remainingUseTicks: Int) {
        blueprint.releaseUsing(stackAccess(stack), HostedWorldAccess(world), ObjectInterop.entity(livingEntity), remainingUseTicks)
    }

    override fun onUpdate(stack: ItemStack, world: World, owner: Entity, slotIndex: Int, isSelected: Boolean) {
        blueprint.inventoryTick(stackAccess(stack), HostedWorldAccess(world), ObjectInterop.entity(owner), if (isSelected) EquipmentSlot.MainHand else null, slotIndex, isSelected)
    }

    override fun onCreated(stack: ItemStack, world: World, player: EntityPlayer) {
        blueprint.onCrafted(stackAccess(stack), HostedWorldAccess(world), ObjectInterop.entity(player))
    }

    override fun getItemUseAction(stack: ItemStack): EnumAction {
        return ItemInterop.useAnimation(blueprint.getUseAnimation(stackAccess(stack)))
    }

    override fun getMaxItemUseDuration(stack: ItemStack): Int {
        return blueprint.getUseDurationTicks(stackAccess(stack), null)
    }

    override fun getDestroySpeed(stack: ItemStack, state: IBlockState): Float {
        return blueprint.getDestroySpeed(stackAccess(stack), HostedBlockStateAccess(state)).toFloat()
    }

    override fun canHarvestBlock(state: IBlockState): Boolean {
        return canHarvestBlock(state, ItemStack(this))
    }

    override fun canHarvestBlock(state: IBlockState, stack: ItemStack): Boolean {
        return blueprint.canHarvest(stackAccess(stack), HostedBlockStateAccess(state))
    }

    override fun getHarvestLevel(stack: ItemStack, toolClass: String, player: EntityPlayer?, blockState: IBlockState?): Int {
        val itemForm = blueprint.form
        if (itemForm !is ItemForm.MiningTool) return super.getHarvestLevel(stack, toolClass, player, blockState)
        if (ItemInterop.toolClass(itemForm.miningCategory) != toolClass) return -1

        return blueprint.getMiningLevel(stackAccess(stack), blockState?.let { HostedBlockStateAccess(it) }, player?.let { ObjectInterop.entity(it) })
    }

    override fun getToolClasses(stack: ItemStack): Set<String> {
        val itemForm = blueprint.form
        if (itemForm !is ItemForm.MiningTool) return super.getToolClasses(stack)
        return setOf(ItemInterop.toolClass(itemForm.miningCategory))
    }

    override fun onBlockDestroyed(stack: ItemStack, world: World, state: IBlockState, position: BlockPos, livingEntity: EntityLivingBase): Boolean {
        return blueprint.mineBlock(stackAccess(stack), HostedWorldAccess(world), HostedBlockStateAccess(state, world, position), GeometryInterop.blockPosition(position), ObjectInterop.entity(livingEntity))
    }

    override fun hitEntity(stack: ItemStack, target: EntityLivingBase, attacker: EntityLivingBase): Boolean {
        val result = blueprint.hurtEnemy(stackAccess(stack), HostedWorldAccess(attacker.world), ObjectInterop.entity(target), ObjectInterop.entity(attacker))
        blueprint.postHurtEnemy(stackAccess(stack), HostedWorldAccess(attacker.world), ObjectInterop.entity(target), ObjectInterop.entity(attacker))

        return result
    }

    override fun isValidArmor(stack: ItemStack, armorType: EntityEquipmentSlot, entity: Entity): Boolean {
        val equipmentSlot = blueprint.getEquipmentSlot(stackAccess(stack), ItemInterop.equipmentSlot(armorType), ObjectInterop.entity(entity))
        return ItemInterop.equipmentSlot(equipmentSlot) == armorType
    }

    override fun getEquipmentSlot(stack: ItemStack): EntityEquipmentSlot? {
        val equipmentSlot = blueprint.getEquipmentSlot(stackAccess(stack), null, null)
        return ItemInterop.equipmentSlot(equipmentSlot)
    }

    override fun getAttributeModifiers(slot: EntityEquipmentSlot, stack: ItemStack): Multimap<String, AttributeModifier> {
        val modifiers = HashMultimap.create(super.getAttributeModifiers(slot, stack))
        val equipmentSlot = blueprint.getEquipmentSlot(stackAccess(stack), ItemInterop.equipmentSlot(slot), null)

        if (ItemInterop.equipmentSlot(equipmentSlot) != slot) return modifiers

        val protectionPoints = blueprint.getArmorProtectionPoints(stackAccess(stack), equipmentSlot, null)
        val toughnessPoints = blueprint.getArmorToughnessPoints(stackAccess(stack), equipmentSlot, null)

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
        val remainingItem = REGISTRY.getObject(IdentifierInterop.identifier(remainingIdentifier))
        if (remainingItem == Items.AIR) return

        setContainerItem(remainingItem)
    }

    private fun stackAccess(stack: ItemStack): HostedItemStackAccess {
        return HostedItemStackAccess(stack, this)
    }

    private fun attributeModifierId(name: String, slot: EntityEquipmentSlot): UUID {
        val seed = "${blueprint.identifier.asString()}:$name:${slot.name}"
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
    }
}
