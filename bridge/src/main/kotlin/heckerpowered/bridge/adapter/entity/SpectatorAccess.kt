/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * Exposes whether an entity is currently observing without participating in gameplay.
 */
interface SpectatorAccess {
    val isSpectator: Boolean
}
