/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.form.armor

import heckerpowered.bridge.adapter.item.equipment.EquipmentSlot

object Chestplate : Armor {
    override val equipmentSlot: EquipmentSlot
        get() = EquipmentSlot.Chest
}
