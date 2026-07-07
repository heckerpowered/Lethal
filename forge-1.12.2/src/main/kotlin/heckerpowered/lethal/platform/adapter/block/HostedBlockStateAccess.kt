/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.block

import heckerpowered.lethal.bridge.adapter.block.BlockStateAccess
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class HostedBlockStateAccess(
    val state: IBlockState,
    val world: World? = null,
    val position: BlockPos? = null,
) : BlockStateAccess
