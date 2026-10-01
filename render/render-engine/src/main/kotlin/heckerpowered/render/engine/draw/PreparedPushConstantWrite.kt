/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.shader.ShaderStage
import java.nio.ByteBuffer
import java.util.*

/**
 * Stable host bytes for one push-constant update performed immediately before a prepared draw.
 *
 * Preparation may finish before the render pass begins, so retaining a temporary native address
 * would be invalid. This value snapshots the bytes instead. Execution copies them into the pass's
 * temporary memory stack immediately before calling `RenderPass.pushConstants`.
 *
 * The selected pipeline performs the final layout compatibility check when the write is encoded.
 * This type only enforces requirements that are independent of that pipeline: a non-empty stage
 * set, non-negative four-byte-aligned destination offset, and a positive four-byte-aligned size.
 */
internal class PreparedPushConstantWrite(
    stages: Set<ShaderStage>,
    bytes: ByteArray,
    val destinationOffsetBytes: Int = 0,
) {
    val stages: Set<ShaderStage>
    val sizeBytes: Int get() = data.size

    private val data: ByteArray

    init {
        require(stages.isNotEmpty()) { "Push constant write requires at least one shader stage" }
        require(destinationOffsetBytes >= 0 && destinationOffsetBytes % 4 == 0) { "Push constant destination offset must be non-negative and four-byte aligned" }
        require(bytes.isNotEmpty() && bytes.size % 4 == 0) { "Push constant data must be positive in size and four-byte aligned" }
        require(bytes.size <= Int.MAX_VALUE - destinationOffsetBytes) { "Push constant write end exceeds Int capacity" }

        this.stages = Collections.unmodifiableSet(EnumSet.copyOf(stages))
        data = bytes.copyOf()
    }

    /** Copies the snapshotted bytes without exposing mutable backing storage. */
    internal fun copyTo(destination: ByteBuffer) {
        require(destination.remaining() >= data.size) { "Push constant destination is too small" }
        destination.put(data)
    }
}
