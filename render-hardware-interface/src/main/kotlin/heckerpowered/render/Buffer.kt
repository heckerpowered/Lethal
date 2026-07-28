/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

enum class BufferUsage {
    Vertex,
    Index,
    Uniform,
    TransferDestination,
}

data class BufferDescription(
    val label: String,
    val sizeBytes: Int,
    val usage: Set<BufferUsage>,
) {
    init {
        require(sizeBytes > 0) { "GPU buffer size must be positive" }
        require(usage.isNotEmpty()) { "GPU buffer requires at least one usage" }
    }
}
