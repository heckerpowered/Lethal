/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.math.BlockPositionProvider
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.lethal.platform.interop.blockPosition
import net.minecraft.util.math.BlockPos

class HostingBlockPositionProvider : BlockPositionProvider {
    override fun position(x: Int, y: Int, z: Int): BlockPositionView {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return BlockPos(x, y, z) as? BlockPositionView ?: BlockPositionProvider.Freestanding.position(x, y, z)
    }

    override fun fromPackedLong(value: Long): BlockPositionView {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return BlockPos.fromLong(value) as? BlockPositionView ?: BlockPositionProvider.Freestanding.fromPackedLong(value)
    }

    override fun asLong(position: BlockPositionView): Long {
        return position.blockPosition().toLong()
    }
}
