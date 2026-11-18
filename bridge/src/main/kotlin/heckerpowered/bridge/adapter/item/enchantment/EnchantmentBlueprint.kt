/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.enchantment

import heckerpowered.bridge.resources.Identifier

/** A declaration that the host materializes in its native enchantment registry. */
interface EnchantmentBlueprint {
    val identifier: Identifier
    val properties: EnchantmentProperties
}
