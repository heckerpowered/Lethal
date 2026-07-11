/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item

import heckerpowered.bridge.resources.Identifier

/**
 * Static item settings used when a host materializes an item blueprint.
 *
 * @property maxStackCount Largest stack size when the item is not damageable.
 * @property maxDamagePoints Maximum durability points. Damageable items use a stack size of one.
 * @property craftingRemainingItem Item left behind when this item is consumed by crafting.
 * @property descriptionKey Stable display-text key. Hosts map it to their own translation or description system.
 * @property model Item model asset identifier. Hosts without model-level registration may ignore it.
 * @property maxUseDurationTicks Longest continuous use duration in game ticks.
 */
data class ItemProperties(
    val maxStackCount: Int = 64,
    val maxDamagePoints: Int = 0,
    val craftingRemainingItem: Identifier? = null,
    val descriptionKey: String? = null,
    val model: Identifier? = null,
    val maxUseDurationTicks: Int = 0,
) {
    init {
        require(maxStackCount in 1..64) { "Max stack count must be between 1 and 64" }
        require(maxDamagePoints >= 0) { "Max damage points must not be negative" }
        require(maxUseDurationTicks >= 0) { "Max use duration ticks must not be negative" }
    }

    val effectiveMaxStackCount: Int
        get() {
            if (maxDamagePoints > 0) return 1
            return maxStackCount
        }
}
