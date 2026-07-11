/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.stack

import heckerpowered.lethal.bridge.adapter.item.ItemAccess

interface ItemStackAccess {
    val item: ItemAccess
    var count: Int
    var damagePoints: Int
    val maxStackCount: Int
    val maxDamagePoints: Int

    val isEmpty: Boolean
        get() = count <= 0

    val isDamageable: Boolean
        get() = maxDamagePoints > 0

    val isDamaged: Boolean
        get() = damagePoints > 0
}
