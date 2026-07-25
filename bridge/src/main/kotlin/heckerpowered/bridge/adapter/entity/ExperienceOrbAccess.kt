/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * An experience orb entity with stable value and consumption behavior.
 */
interface ExperienceOrbAccess : EntityAccess {
    val experiencePoints: Int

    fun consume()
}
