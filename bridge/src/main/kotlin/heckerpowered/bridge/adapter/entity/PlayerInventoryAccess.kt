/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

/**
 * Reads the player's current main inventory, hotbar and offhand on the owning thread.
 *
 * The selected main-hand stack is included through the hotbar. The sequence is a live
 * view, not a snapshot; it excludes armor, open containers and other inventories.
 */
interface PlayerInventoryAccess : PlayerAccess {
    val carriedStacks: Sequence<ItemStackAccess>
}

