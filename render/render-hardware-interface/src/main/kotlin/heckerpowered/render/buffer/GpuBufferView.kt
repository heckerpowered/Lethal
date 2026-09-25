/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.buffer

import heckerpowered.render.memory.NativeAddress
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
     * Checks the public preconditions for uploading host bytes into this selection.
     *
     * Upload access requires TransferDestination even when a later draw uses the same buffer
     * as uniform or vertex input. An empty selection reads nothing, so its source address may
     * be zero. This does not establish that a nonzero host address is readable; the caller of
     * the upload still has to supply at least [sizeBytes] valid bytes.
     *
     * Device identity, resource validity, recording scope, and execution support are checked
     * by the command implementation. This method does not read memory or record a command.
     *
     * @throws IllegalArgumentException if the usage is absent or a nonempty upload has a zero
     * source address.
     */
    fun validateUpload(sourceAddress: NativeAddress) {
        require(BufferUsage.TransferDestination in buffer.usage) { "Buffer upload destination requires TransferDestination usage" }
        require(sizeBytes == 0L || sourceAddress.rawValue != 0L) { "A nonempty buffer upload requires a nonzero source address" }
    }

    /**
     * Checks whether these byte selections can describe a buffer-to-buffer copy.
     *
     * Both lengths must match so the operation copies exactly what the caller selected, rather
     * than silently taking the shorter range. Adjacent ranges of the same buffer are disjoint;
     * a nonempty range copied onto itself is not. Empty selections access no bytes.
     *
     * Overlap can be established here when both views reference the same buffer. Different
     * wrappers are not proof of different allocations: the backend must also compare their
     * actual storage ranges. This check neither reads source contents nor establishes device
     * ownership, resource validity, support, or synchronization.
     *
     * @throws IllegalArgumentException if a required transfer usage is missing, the lengths
     * differ, or nonempty ranges of the same buffer overlap.
     */
    fun validateCopyTo(destination: GpuBufferView) {
        require(BufferUsage.TransferSource in buffer.usage) { "Buffer copy source requires TransferSource usage" }
        require(BufferUsage.TransferDestination in destination.buffer.usage) { "Buffer copy destination requires TransferDestination usage" }
        require(sizeBytes == destination.sizeBytes) { "Buffer copy ranges must have equal sizes: source=$sizeBytes, destination=${destination.sizeBytes}" }
        if (sizeBytes == 0L || buffer !== destination.buffer) return

        val distance = if (offsetBytes <= destination.offsetBytes)
            destination.offsetBytes - offsetBytes else
            offsetBytes - destination.offsetBytes

        require(distance >= sizeBytes) { "Buffer copy source and destination ranges overlap" }
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
    require(sizeBytes <= capacityBytes - offsetBytes) { "Buffer view extends beyond its containing range" }
}