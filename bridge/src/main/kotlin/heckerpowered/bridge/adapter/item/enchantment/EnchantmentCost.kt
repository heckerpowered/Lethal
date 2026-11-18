/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.enchantment

data class EnchantmentCost(val base: Int, val perLevelAboveFirst: Int = 0) {
    fun calculate(level: Int): Int = base + perLevelAboveFirst * (level - 1)
}
