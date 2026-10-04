/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.combat.damage

interface CancellableDamageContext : DamageContext {
    val isCancelled: Boolean
    fun cancel()
}