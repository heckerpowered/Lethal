/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * Receives raw experience points without prescribing where the host stores them.
 */
interface ExperienceReceiverAccess {
    fun addExperiencePoints(points: Int)
}
