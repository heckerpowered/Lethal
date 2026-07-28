/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.stack

import heckerpowered.bridge.resources.Identifier

/**
 * Provides namespaced primitive state that survives host serialization.
 *
 * Reads must not create or otherwise mutate host storage. Persistent data remains a separate
 * capability because hosts expose it through different storage APIs. Extending [ItemStackAccess]
 * preserves the capability's stable ownership by the item stack.
 */
interface PersistentDataAccess : ItemStackAccess {
    fun getLong(key: Identifier): Long?

    fun setLong(key: Identifier, value: Long)

    fun getDouble(key: Identifier): Double?

    fun setDouble(key: Identifier, value: Double)

    fun remove(key: Identifier)
}
