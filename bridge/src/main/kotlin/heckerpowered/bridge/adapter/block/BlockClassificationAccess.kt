/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.block

/**
 * Exposes host block classifications that are not part of a block state's stable identity.
 *
 * This capability remains independent from [BlockStateAccess] because older hosts may need
 * contextual or compatibility-specific rules to answer the same classification.
 */
interface BlockClassificationAccess {
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
