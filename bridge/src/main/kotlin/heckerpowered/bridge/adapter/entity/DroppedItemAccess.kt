/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * A dropped item entity that can be made eligible for collection immediately.
 */
interface DroppedItemAccess : EntityAccess {
    fun makeImmediatelyCollectible()
}
