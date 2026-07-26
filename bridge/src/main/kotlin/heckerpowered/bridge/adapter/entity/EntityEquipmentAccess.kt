/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

import heckerpowered.bridge.adapter.item.EquipmentSlot
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

interface EntityEquipmentAccess : LivingEntityAccess {
    fun getEquippedStack(slot: EquipmentSlot): ItemStackAccess
}
