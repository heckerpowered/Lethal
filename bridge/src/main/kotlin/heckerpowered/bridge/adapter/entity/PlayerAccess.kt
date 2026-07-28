/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

interface PlayerAccess : LivingEntityAccess {
    val isSpectator: Boolean

    fun addExperiencePoints(points: Int)
}
