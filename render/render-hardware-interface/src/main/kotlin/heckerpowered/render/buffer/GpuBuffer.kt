/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.buffer

import heckerpowered.render.BufferUsage
import heckerpowered.render.GpuResource

/**
 * A fixed-size region of byte storage accessible to graphics operations.
 *
 * A buffer has no intrinsic element type. Vertex input, index interpretation, and shader data
 * layouts are supplied by the operations that use it, so the same storage may serve several
 * roles when its usage permits them.
 *
 * The capacity and permitted uses remain unchanged throughout the buffer's lifetime; its contents
 * may change. Data transfer and binding are performed through the graphics command API rather
 * than through this interface.
 *
 * GPU access does not imply CPU addressability or a particular physical memory location. Closing
 * the buffer ends the usable lifetime of every slice that references it.
 */
interface GpuBuffer : GpuResource {
    /** Allocated capacity in bytes, not the amount of initialized or currently used data. */
    val sizeBytes: Long

    /**
     * Operations permitted for this allocation.
     *
     * This set is a stable snapshot. It does not describe the current binding, a memory-placement
     * preference, or permission to access the storage directly from the CPU.
     */
    val usage: Set<BufferUsage>
}