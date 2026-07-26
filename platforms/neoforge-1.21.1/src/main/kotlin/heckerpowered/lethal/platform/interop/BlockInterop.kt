/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.block.BlockStateAccess
import net.minecraft.world.level.block.state.BlockState

fun BlockStateAccess.asHost() = requireHost<BlockState>(this)

fun BlockState.asView() = requireAccess<BlockStateAccess>(this)
