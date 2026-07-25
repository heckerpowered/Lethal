/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.world.raycast

import heckerpowered.bridge.adapter.block.BlockStateAccess
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.VectorView

/**
 * A block shape intersected by a ray.
 *
 * @property blockState State observed by the host while computing this hit.
 * @property time Parameter used by `ray.pointAt(time)` to reach [point].
 */
data class BlockHitResult(
    val blockPosition: BlockPositionView,
    val blockState: BlockStateAccess,
    val face: BlockDirection,
    val point: VectorView,
    val time: Double,
)
