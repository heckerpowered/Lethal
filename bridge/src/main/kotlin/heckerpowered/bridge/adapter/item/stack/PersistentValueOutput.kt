/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.stack

import heckerpowered.bridge.resources.Identifier

interface PersistentValueOutput {
    fun putLong(key: Identifier, value: Long)

    fun putDouble(key: Identifier, value: Double)

    fun remove(key: Identifier)
}
