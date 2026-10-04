/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.*
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.Constants

class ZeusArmor private constructor(
    path: String,
    override val form: ItemForm.Armor,
    private val protectionPoints: Int,
    durabilityPoints: Int,
) : ItemBlueprint, ItemTooltip {
    override val identifier = Constants.identifier(path)
    override val properties = ItemProperties(
        maxStackCount = 1,
        maxDamagePoints = durabilityPoints,
        descriptionKey = "item.lethal.$path",
    )

    override fun getArmorProtectionPoints(stack: ItemStackAccess, slot: EquipmentSlot?, wearer: EntityAccess?): Int {
        return if (slot == form.equipmentSlot) protectionPoints else 0
    }

    override fun getTooltipLines(stack: ItemStackAccess): List<TooltipLinePresentation> {
        return listOf(TooltipLine(listOf(TooltipText.Translatable("tooltip.lethal.zeus_armor")), TooltipColor.Gray))
    }

    companion object {
        val Helmet = ZeusArmor("zeus_helmet", ItemForm.Helmet, 2, 165)
        val Chestplate = ZeusArmor("zeus_chestplate", ItemForm.Chestplate, 6, 240)
        val Leggings = ZeusArmor("zeus_leggings", ItemForm.Leggings, 5, 225)
        val Boots = ZeusArmor("zeus_boots", ItemForm.Boots, 2, 195)
        val Pieces = listOf(Helmet, Chestplate, Leggings, Boots)

        @JvmStatic
        fun preventsDamage(wearer: EntityAccess, damageType: Identifier): Boolean {
            val vanillaType = VanillaDamageType.fromIdentifier(damageType) ?: return false
            if (vanillaType == VanillaDamageType.FellOutOfWorld || vanillaType == VanillaDamageType.GenericKill) return false
            val equipment = wearer as? EntityEquipmentAccess ?: return false

            return Pieces.all { piece ->
                val stack = equipment.getEquippedStack(piece.form.equipmentSlot)
                !stack.isEmpty && stack.item.identifier == piece.identifier
            }
        }
    }
}
