/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.enchantment

import heckerpowered.bridge.adapter.item.EquipmentSlot
import heckerpowered.bridge.resources.Identifier

/** Finite item and enchantment identities can become native holder sets without executable predicates. */
data class EnchantmentProperties(
    val supportedItems: Set<Identifier>,
    val activeSlots: Set<EquipmentSlot>,
    val rarity: EnchantmentRarity,
    val maxLevel: Int,
    val minCost: EnchantmentCost,
    val maxCost: EnchantmentCost,
    val primaryItems: Set<Identifier>? = null,
    val exclusiveWith: Set<Identifier> = emptySet(),
    val isTreasure: Boolean = false,
) {
    init {
        require(supportedItems.isNotEmpty())
        require(activeSlots.isNotEmpty())
        require(maxLevel in 1..255)
        require(primaryItems == null || supportedItems.containsAll(primaryItems))
    }
}
