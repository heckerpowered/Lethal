/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.stack

/**
 * Provides namespaced values that survive the item stack's host serialization.
 *
 * Input and output resolve the stack's current storage on each operation. Use them on the owning
 * game thread; output changes are visible before the call returns.
 */
interface PersistentDataAccess : ItemStackAccess {
    val input: PersistentValueInput

    val output: PersistentValueOutput
}
