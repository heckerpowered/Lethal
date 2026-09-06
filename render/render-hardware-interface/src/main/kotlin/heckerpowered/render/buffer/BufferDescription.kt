/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.buffer

import heckerpowered.render.memory.Size
import java.util.*

/**
 * Describes the capacity and permitted uses of a buffer to be allocated by a graphics device.
 *
 * The capacity reserves space, while usage determines how that space may be accessed. For
 * example, a buffer populated by uploads and later read as vertex data requests both
 * [BufferUsage.TransferDestination] and [BufferUsage.Vertex].
 *
 * This description provides no initial contents and makes no zero-initialization guarantee.
 * The application must establish the data before an operation reads it. The device checks
 * capacity limits and supported usage combinations when creating the allocation.
 *
 * @throws IllegalArgumentException if the capacity is not positive or no usage is specified.
 */
class BufferDescription(
    val label: String,
    val sizeBytes: Size,
    usage: Set<BufferUsage>,
) {
    /**
     * A snapshot of the requested roles. Changes to the supplied set do not change this request,
     * and the exposed set cannot be modified.
     */
    val usage: Set<BufferUsage> = Collections.unmodifiableSet(usage.toSet())

    init {
        require(sizeBytes > 0) { "GPU buffer size must be positive" }
        require(this.usage.isNotEmpty()) { "GPU buffer requires at least one usage" }
    }
}
