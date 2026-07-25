/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.stack

import heckerpowered.bridge.resources.Identifier

/**
 * Provides namespaced primitive state that survives host serialization.
 *
 * Reads must not create or otherwise mutate host storage. This capability is independent from
 * [ItemStackAccess] because a host may expose stack state through a separate adapter.
 */
interface PersistentDataAccess {
    fun getLong(key: Identifier): Long?

    fun setLong(key: Identifier, value: Long)

    fun getDouble(key: Identifier): Double?

    fun setDouble(key: Identifier, value: Double)

    fun remove(key: Identifier)
}
