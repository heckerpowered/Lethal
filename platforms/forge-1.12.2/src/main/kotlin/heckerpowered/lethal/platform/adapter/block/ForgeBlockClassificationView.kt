/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.block

import heckerpowered.bridge.HostingRepresentation
import heckerpowered.bridge.adapter.block.BlockCategory
import heckerpowered.bridge.adapter.block.BlockClassificationAccess
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.block.Block
import net.minecraft.block.BlockLog
import net.minecraft.block.BlockPlanks
import net.minecraft.block.BlockSand
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class ForgeBlockClassificationView(val state: IBlockState, private val world: World, private val position: BlockPos) : BlockClassificationAccess, HostingRepresentation {
    override val block
        get() = state.block.asView()

    override val isReplaceable: Boolean
        get() = state.block.isReplaceable(world, position)

    override fun isIn(category: BlockCategory): Boolean {
        val block = state.block
        return when (category) {
            BlockCategory.SugarCane -> block === Blocks.REEDS
            BlockCategory.Logs -> block.isLog()
            BlockCategory.Planks -> block is BlockPlanks
            BlockCategory.Snow -> block === Blocks.SNOW || block === Blocks.SNOW_LAYER
            BlockCategory.Sand -> block is BlockSand
        }
    }

    private fun Block.isLog(): Boolean {
        return this is BlockLog || isWood(world, position)
    }
}
