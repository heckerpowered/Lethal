/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import heckerpowered.bridge.adapter.item.*
import heckerpowered.bridge.math.Geometry
import heckerpowered.lethal.platform.adapter.item.creativetab.ForgeCreativeModeTabs
import heckerpowered.lethal.platform.interop.asHost
import heckerpowered.lethal.platform.interop.asView
import heckerpowered.lethal.platform.interop.toolClass
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
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
        registryName = blueprint.identifier.asHost()

        setTranslationKey(blueprint.properties.descriptionKey ?: defaultDescriptionKey())
        setMaxStackSize(blueprint.properties.effectiveMaxStackCount)
        setMaxDamage(blueprint.properties.maxDamagePoints)
        setCraftingRemainingItem()
        setPrimaryCreativeModeTab()
    }

    override fun onItemRightClick(world: World, player: EntityPlayer, hand: EnumHand): ActionResult<ItemStack> {
        val stack = player.getHeldItem(hand)
        if (blueprint is ContinuousUseItem) {
            player.setActiveHand(hand)
            return ActionResult(EnumActionResult.SUCCESS, stack)
        }

        val result = blueprint.use(stack.asView(), world.asView(), player.asView(), hand.asView())

        return ActionResult(result.asHost(), stack)
    }

    override fun onItemUse(player: EntityPlayer, world: World, position: BlockPos, hand: EnumHand, facing: EnumFacing, hitOffsetX: Float, hitOffsetY: Float, hitOffsetZ: Float): EnumActionResult {
        val stack = player.getHeldItem(hand)
        val blockState = world.getBlockState(position)
        val blockStateAccess = blockState.asView()
        val hitPosition = Geometry.vector(hitOffsetX.toDouble(), hitOffsetY.toDouble(), hitOffsetZ.toDouble())
        val result = blueprint.useOnBlock(stack.asView(), world.asView(), player.asView(), hand.asView(), position.asView(), blockStateAccess, facing.asView(), hitPosition)

        return result.asHost()
    }

    override fun itemInteractionForEntity(stack: ItemStack, player: EntityPlayer, target: EntityLivingBase, hand: EnumHand): Boolean {
        val result = blueprint.interactLivingEntity(stack.asView(), player.world.asView(), player.asView(), target.asView(), hand.asView())

        return result != ItemInteractionResult.Pass
    }

    override fun onUsingTick(stack: ItemStack, player: EntityLivingBase, count: Int) {
        blueprint.onUseTick(stack.asView(), player.world.asView(), player.asView(), count)
    }

    override fun onItemUseFinish(stack: ItemStack, world: World, livingEntity: EntityLivingBase): ItemStack {
        val result = blueprint.finishUsing(stack.asView(), world.asView(), livingEntity.asView())

        return result.asHost()
    }

    override fun onPlayerStoppedUsing(stack: ItemStack, world: World, livingEntity: EntityLivingBase, remainingUseTicks: Int) {
        blueprint.releaseUsing(stack.asView(), world.asView(), livingEntity.asView(), remainingUseTicks)
    }

    override fun onUpdate(stack: ItemStack, world: World, owner: Entity, slotIndex: Int, isSelected: Boolean) {
        val equipmentSlot = if (isSelected) EquipmentSlot.MainHand else null
        blueprint.inventoryTick(stack.asView(), world.asView(), owner.asView(), equipmentSlot, slotIndex, isSelected)
    }

    override fun onCreated(stack: ItemStack, world: World, player: EntityPlayer) {
        blueprint.onCrafted(stack.asView(), world.asView(), player.asView())
    }

    override fun getItemUseAction(stack: ItemStack): EnumAction {
        if (blueprint is ContinuousUseItem) return EnumAction.BOW
        return blueprint.getUseAnimation(stack.asView()).asHost()
    }

    override fun getCreativeTabs(): Array<CreativeTabs> {
        val tabs = ForgeCreativeModeTabs.findAll(blueprint)
        if (tabs.isEmpty()) return super.getCreativeTabs()
        return tabs.toTypedArray()
    }

    override fun getMaxItemUseDuration(stack: ItemStack): Int {
        if (blueprint is ContinuousUseItem) return Int.MAX_VALUE
        return blueprint.getUseDurationTicks(stack.asView(), null)
    }

    override fun addInformation(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        val itemTooltip = blueprint as? ItemTooltip ?: return
        tooltip += itemTooltip.getTooltipLines(stack.asView())
            .map(TooltipLine::asHost)
    }

    override fun hasEffect(stack: ItemStack): Boolean {
        val itemGlint = blueprint as? ItemGlint ?: return super.hasEffect(stack)
        return super.hasEffect(stack) || itemGlint.hasGlint(stack.asView())
    }

    override fun getDestroySpeed(stack: ItemStack, state: IBlockState): Float {
        return blueprint.getDestroySpeed(stack.asView(), state.asView()).toFloat()
    }

    override fun canHarvestBlock(state: IBlockState): Boolean {
        return canHarvestBlock(state, ItemStack(this))
    }

    override fun canHarvestBlock(state: IBlockState, stack: ItemStack): Boolean {
        return blueprint.canHarvest(stack.asView(), state.asView())
    }

    override fun getHarvestLevel(stack: ItemStack, toolClass: String, player: EntityPlayer?, blockState: IBlockState?): Int {
        val itemForm = blueprint.form
        if (itemForm !is ItemForm.MiningTool) return super.getHarvestLevel(stack, toolClass, player, blockState)
        if (itemForm.miningCategory.toolClass() != toolClass) return -1

        return blueprint.getMiningLevel(stack.asView(), blockState?.asView(), player?.asView())
    }

    override fun getToolClasses(stack: ItemStack): Set<String> {
        val itemForm = blueprint.form
        if (itemForm !is ItemForm.MiningTool) return super.getToolClasses(stack)
        return setOf(itemForm.miningCategory.toolClass())
    }

    override fun onBlockDestroyed(stack: ItemStack, world: World, state: IBlockState, position: BlockPos, livingEntity: EntityLivingBase): Boolean {
        return blueprint.mineBlock(stack.asView(), world.asView(), state.asView(), position.asView(), livingEntity.asView())
    }

    override fun hitEntity(stack: ItemStack, target: EntityLivingBase, attacker: EntityLivingBase): Boolean {
        val result = blueprint.hurtEnemy(stack.asView(), attacker.world.asView(), target.asView(), attacker.asView())
        blueprint.postHurtEnemy(stack.asView(), attacker.world.asView(), target.asView(), attacker.asView())

        return result
    }

    override fun isValidArmor(stack: ItemStack, armorType: EntityEquipmentSlot, entity: Entity): Boolean {
        val equipmentSlot = blueprint.getEquipmentSlot(stack.asView(), armorType.asView(), entity.asView())
        return equipmentSlot?.asHost() == armorType
    }

    override fun getEquipmentSlot(stack: ItemStack): EntityEquipmentSlot? {
        val equipmentSlot = blueprint.getEquipmentSlot(stack.asView(), null, null)
        return equipmentSlot?.asHost()
    }

    override fun getAttributeModifiers(slot: EntityEquipmentSlot, stack: ItemStack): Multimap<String, AttributeModifier> {
        val modifiers = HashMultimap.create(super.getAttributeModifiers(slot, stack))
        val equipmentSlot = blueprint.getEquipmentSlot(stack.asView(), slot.asView(), null)

        if (equipmentSlot?.asHost() != slot) return modifiers

        val protectionPoints = blueprint.getArmorProtectionPoints(stack.asView(), equipmentSlot, null)
        val toughnessPoints = blueprint.getArmorToughnessPoints(stack.asView(), equipmentSlot, null)

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
        val remainingItem = REGISTRY.getObject(remainingIdentifier.asHost())
        if (remainingItem == Items.AIR) return

        setContainerItem(remainingItem)
    }

    private fun setPrimaryCreativeModeTab() {
        val primaryTab = ForgeCreativeModeTabs.findAll(blueprint)
            .firstOrNull() ?: return
        setCreativeTab(primaryTab)
    }

    private fun attributeModifierId(name: String, slot: EntityEquipmentSlot): UUID {
        val seed = "${blueprint.identifier.asString()}:$name:${slot.name}"
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
    }
}
