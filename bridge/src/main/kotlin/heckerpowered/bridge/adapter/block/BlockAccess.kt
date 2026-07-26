/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.block

import heckerpowered.bridge.adapter.BridgeAccess
import heckerpowered.bridge.resources.Identifier

interface BlockAccess : BridgeAccess {
    /**
     * Registry identity of this block.
     */
    val identifier: Identifier
}
