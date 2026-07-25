/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/** Applies the vanilla glowing status effect to a supported entity. */
interface GlowingAccess {
    fun glowFor(durationTicks: Int)
}
