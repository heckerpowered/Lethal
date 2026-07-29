/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.math.BlockPositionProvider
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.core.BlockPos

class HostingBlockPositionProvider : BlockPositionProvider {
    override fun position(x: Int, y: Int, z: Int): BlockPositionView {
        return BlockPos(x, y, z).asView()
    }

    override fun fromPackedLong(value: Long): BlockPositionView {
        return BlockPos.of(value).asView()
    }

    override fun asLong(position: BlockPositionView): Long {
        return BlockPos.asLong(position.x, position.y, position.z)
    }
}
