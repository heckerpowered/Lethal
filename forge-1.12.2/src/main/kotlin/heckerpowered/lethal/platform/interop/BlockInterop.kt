/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.lethal.bridge.adapter.block.BlockStateAccess
import heckerpowered.lethal.platform.adapter.block.HostedBlockStateAccess
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object BlockInterop {
    @JvmStatic
    fun blockState(access: BlockStateAccess): IBlockState {
        if (access is HostedBlockStateAccess) return access.state

        @Suppress("CAST_NEVER_SUCCEEDS")
        return access as? IBlockState ?: error("Unsupported BlockStateAccess implementation: ${access::class.java.name}")
    }

    @JvmStatic
    fun blockStateOrNull(access: BlockStateAccess?): IBlockState? {
        if (access == null) return null
        return blockState(access)
    }

    @JvmStatic
    fun blockState(state: IBlockState, world: World? = null, position: BlockPos? = null): BlockStateAccess {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return state as? BlockStateAccess ?: HostedBlockStateAccess(state, world, position)
    }
}

fun BlockStateAccess.blockState(): IBlockState {
    return BlockInterop.blockState(this)
}

fun BlockStateAccess?.blockStateOrNull(): IBlockState? {
    return BlockInterop.blockStateOrNull(this)
}

fun IBlockState.blockState(world: World? = null, position: BlockPos? = null): BlockStateAccess {
    return BlockInterop.blockState(this, world, position)
}
