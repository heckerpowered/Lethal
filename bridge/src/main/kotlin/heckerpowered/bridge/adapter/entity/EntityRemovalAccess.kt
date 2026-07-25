/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * Removes an entity without requiring ordinary damage or living-entity death processing.
 */
interface EntityRemovalAccess {
    fun remove()
}
