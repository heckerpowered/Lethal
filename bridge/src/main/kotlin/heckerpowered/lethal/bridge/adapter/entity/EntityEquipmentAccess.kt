/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.entity

import heckerpowered.lethal.bridge.adapter.item.EquipmentSlot
import heckerpowered.lethal.bridge.adapter.item.stack.ItemStackAccess

interface EntityEquipmentAccess : EntityAccess {
    fun getEquippedStack(slot: EquipmentSlot): ItemStackAccess
}
