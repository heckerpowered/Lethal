/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.buffer

import heckerpowered.render.memory.Size

/**
 * Selects a contiguous byte range of a [GpuBuffer].
 *
 * Views allow several independently used regions to share one allocation. For example, meshes
 * packed into a common buffer can each be passed around as a view of their own byte range.
 *
 * The selected range is `[offsetBytes, offsetBytes + sizeBytes)` in [buffer]. Byte zero of the
 * selected region corresponds to [offsetBytes] in the complete buffer.
 *
 * Creating a view does not copy the contents. Overlapping views select the same underlying
 * bytes rather than independent snapshots.
 *
 * Empty ranges are representable, including a range at the end of the buffer. An operation
 * consuming the view may impose additional size or alignment requirements.
 *
 * @throws IllegalArgumentException if the offset or size is negative, or the range extends
 * beyond the buffer's capacity.
 */
data class GpuBufferView(
    val buffer: GpuBuffer,
    val offsetBytes: Size,
    val sizeBytes: Size,
) {
    init {
        requireContainedRange(buffer.sizeBytes, offsetBytes, sizeBytes)
    }

    /**
     * Selects a region within this view, keeping the same underlying buffer.
     *
     * [relativeOffsetBytes] is measured from this view's beginning. For example, selecting
     * 32 bytes at relative offset 16 from a view that begins at buffer offset 1024 produces
     * a view of buffer bytes `[1040, 1072)`.
     *
     * The result must fit within this view, even when the underlying buffer has additional
     * capacity. Its [offsetBytes] is still measured from the beginning of the complete buffer.
     *
     * @throws IllegalArgumentException if either argument is negative or the selected region
     * extends beyond this view.
     */
    fun subView(relativeOffsetBytes: Size, sizeBytes: Size): GpuBufferView {
        requireContainedRange(this.sizeBytes, relativeOffsetBytes, sizeBytes)
        return GpuBufferView(buffer, offsetBytes + relativeOffsetBytes, sizeBytes)
    }
}

/**
 * Exposes the complete buffer as a view of the same storage.
 */
fun GpuBuffer.asView(): GpuBufferView = GpuBufferView(this, 0, sizeBytes)

private fun requireContainedRange(capacityBytes: Size, offsetBytes: Size, sizeBytes: Size) {
    require(offsetBytes >= 0) { "Buffer view offset must be non-negative" }
    require(sizeBytes >= 0) { "Buffer view size must be non-negative" }
    require(offsetBytes <= capacityBytes) { "Buffer view offset exceeds its containing range" }

    // Subtraction avoids overflowing an invalid end offset while checking the range.
    require(sizeBytes <= capacityBytes - offsetBytes) {
        "Buffer view extends beyond its containing range"
    }
}
