/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.block.BlockAccess
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

@JvmName("asNullableBlockStateHost")
fun BlockStateAccess?.asHost(): IBlockState? = this?.asHost()

fun IBlockState.asView(): BlockStateAccess = requireAccess(this)

fun IBlockState.asView(world: World, position: BlockPos): BlockStateAccess = ForgeBlockClassificationView(this, world, position)
