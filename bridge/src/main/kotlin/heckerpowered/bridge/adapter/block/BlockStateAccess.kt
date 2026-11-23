/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.block

import heckerpowered.bridge.adapter.BridgeAccess

/**
 * A particular state of a block. [block] identifies the registered type shared by its states;
 * optional classification capabilities are exposed separately.
 */
interface BlockStateAccess : BridgeAccess {
    val block: BlockAccess
}
