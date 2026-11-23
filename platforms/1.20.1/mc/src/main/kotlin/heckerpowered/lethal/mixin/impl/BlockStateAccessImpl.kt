/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.block.BlockCategory
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * Tests a native block state against the bridge’s block categories. Callers can classify a state
 * without depending on the current host’s block constants or tags.
 */
object BlockStateAccessImpl {
    @JvmStatic
    fun isIn(state: BlockState, category: BlockCategory): Boolean = when (category) {
        BlockCategory.SugarCane -> state.block == Blocks.SUGAR_CANE
        BlockCategory.Logs -> state.`is`(BlockTags.LOGS)
        BlockCategory.Planks -> state.`is`(BlockTags.PLANKS)
        BlockCategory.Snow -> state.`is`(BlockTags.SNOW)
        BlockCategory.Sand -> state.`is`(BlockTags.SAND)
    }
}
