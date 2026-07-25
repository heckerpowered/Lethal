/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.item.stack

/** Resolves optional item-stack capabilities at a single boundary. */
object ItemStackInterop {
    @JvmStatic
    fun persistentData(stack: ItemStackAccess?): PersistentDataAccess? {
        return stack as? PersistentDataAccess
    }
}
