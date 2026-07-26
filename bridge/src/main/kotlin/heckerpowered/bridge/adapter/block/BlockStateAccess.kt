/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.block

import heckerpowered.bridge.adapter.BridgeAccess

interface BlockStateAccess : BridgeAccess {
    /**
     * Block that owns this state.
     */
    val block: BlockAccess
}
