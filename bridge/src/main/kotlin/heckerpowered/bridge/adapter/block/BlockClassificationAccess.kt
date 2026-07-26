/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.block

/**
 * Exposes host block classifications that are not part of a block state's stable identity.
 *
 * Classification remains a separate capability because hosts derive it through different rules.
 * Extending [BlockStateAccess] preserves the capability's stable ownership by the block state.
 */
interface BlockClassificationAccess : BlockStateAccess {
    val isReplaceable: Boolean

    fun isIn(category: BlockCategory): Boolean
}

enum class BlockCategory {
    SugarCane,
    Logs,
    Planks,
    Snow,
    Sand,
}
