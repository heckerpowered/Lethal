/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.block

interface BlockStateAccess {
    /** Block that owns this state. */
    val block: BlockAccess
}
