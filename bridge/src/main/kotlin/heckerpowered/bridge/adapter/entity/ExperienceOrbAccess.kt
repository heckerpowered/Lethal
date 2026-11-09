/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * An experience orb entity exposing all remaining experience, including merged entries.
 */
interface ExperienceOrbAccess : EntityAccess {
    val experiencePoints: Int
}
