/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

interface LivingEntityAccess : EntityAccess {
    var health: Double
    val maximumHealth: Double
}
