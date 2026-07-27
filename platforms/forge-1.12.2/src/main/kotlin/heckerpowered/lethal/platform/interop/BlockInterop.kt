/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.requireAccess
import heckerpowered.bridge.requireHost
import heckerpowered.bridge.adapter.block.BlockAccess
import heckerpowered.bridge.adapter.block.BlockClassificationAccess
import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.lethal.platform.adapter.block.ForgeBlockClassificationView
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

fun Block.asView(): BlockAccess = requireAccess(this)

fun BlockStateAccess.asHost(): IBlockState {
    return if (this is ForgeBlockClassificationView) state else requireHost(this)
}

fun IBlockState.asView(): BlockStateAccess = requireAccess(this)

fun IBlockState.asClassificationView(world: World, position: BlockPos): BlockClassificationAccess = ForgeBlockClassificationView(this, world, position)
