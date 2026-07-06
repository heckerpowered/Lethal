/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.lethal.bridge.math.BlockPositionProvider
import heckerpowered.lethal.bridge.math.BlockPositionView
import heckerpowered.lethal.platform.GeometryInterop
import net.minecraft.util.math.BlockPos

class HostingBlockPositionProvider : BlockPositionProvider {
    override fun position(x: Int, y: Int, z: Int): BlockPositionView {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return BlockPos(x, y, z) as BlockPositionView
    }

    override fun fromPackedLong(value: Long): BlockPositionView {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return BlockPos.fromLong(value) as BlockPositionView
    }

    override fun asLong(position: BlockPositionView): Long {
        return GeometryInterop.blockPosition(position).toLong()
    }
}
