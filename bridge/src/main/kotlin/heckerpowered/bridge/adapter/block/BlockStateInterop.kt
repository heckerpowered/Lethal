/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.block

/** Resolves optional block-state capabilities at a single boundary. */
object BlockStateInterop {
    @JvmStatic
    fun classification(blockState: BlockStateAccess?): BlockClassificationAccess? {
        return blockState as? BlockClassificationAccess
    }
}
