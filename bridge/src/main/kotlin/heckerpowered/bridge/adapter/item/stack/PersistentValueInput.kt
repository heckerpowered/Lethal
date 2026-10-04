/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.stack

import heckerpowered.bridge.resources.Identifier

/**
 * Reads persistent values without creating or changing host storage.
 * Missing values and values of a different stored type return null; numeric types are not converted.
 */
interface PersistentValueInput {
    fun getLong(key: Identifier): Long?

    fun getDouble(key: Identifier): Double?

    fun getLongOr(key: Identifier, defaultValue: Long): Long = getLong(key) ?: defaultValue

    fun getDoubleOr(key: Identifier, defaultValue: Double): Double = getDouble(key) ?: defaultValue
}
