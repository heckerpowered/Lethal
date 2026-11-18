/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.form.armor

import heckerpowered.bridge.adapter.item.equipment.EquipmentSlot
import heckerpowered.bridge.adapter.item.form.ItemForm

interface Armor : ItemForm {
    val equipmentSlot: EquipmentSlot
}
