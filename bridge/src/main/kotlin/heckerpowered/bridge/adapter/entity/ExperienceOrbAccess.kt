/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * An experience orb entity with a stable experience value.
 */
interface ExperienceOrbAccess : EntityAccess {
    val experiencePoints: Int
}
