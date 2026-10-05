/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.effect

import heckerpowered.bridge.adapter.entity.LivingEntityAccess

interface StatusEffectAccess : LivingEntityAccess {
    fun addStatusEffect(effect: StatusEffectInstance)
}
