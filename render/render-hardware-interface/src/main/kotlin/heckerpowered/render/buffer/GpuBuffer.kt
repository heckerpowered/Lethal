/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.buffer

import heckerpowered.render.GpuResource
import heckerpowered.render.memory.Size

/**
 * Stores bytes that graphics and compute operations can read or write.
 *
 * A buffer does not assign an element type or data layout to its contents. Vertex input may
 * interpret a range as positions and texture coordinates, while a shader binding may interpret
 * another range as a block of parameters. The corresponding bindings and shader declarations
 * determine those interpretations.
 *
 * A single allocation can serve several roles when its usage permits them. For example, a
 * compute shader can write vertex data into a buffer that a later draw reads through vertex
 * input.
 *
 * The allocation's capacity and permitted uses remain fixed, while its contents may change.
 * [GpuBufferView] selects a range within that allocation.
 */
interface GpuBuffer : GpuResource {
    /**
     * Allocated capacity in bytes, rather than the amount of data currently used by the
     * application.
     */
    val sizeBytes: Size

    /**
     * Operations for which this allocation may be used.
     *
     * Multiple roles can be combined; this set does not describe the buffer's current binding
     * or establish ordering between operations that access it. Its contents remain unchanged
     * throughout the allocation's lifetime.
     */
    val usage: Set<BufferUsage>
}
