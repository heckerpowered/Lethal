/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.primitive

import heckerpowered.render.memory.Size
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Determines how an index-buffer range stores vertex indices for indexed drawing.
 *
 * A quad can store four vertices once and describe its two triangles with indices
 * `0, 1, 2, 2, 3, 0`. The indices select elements of the bound per-vertex streams; they are
 * not byte offsets. This format tells the draw how many bytes to read for each index.
 *
 * Wider indices can address more vertices but increase the size of the index data. A mesh
 * can use small local indices even inside a large vertex buffer: the draw's signed base-vertex
 * offset relocates those indices after they have been read.
 *
 * When primitive restart is enabled, [restartIndex] separates strips instead of selecting a
 * vertex. The comparison uses the stored index before adding the base-vertex offset, so moving
 * a mesh does not change where its strips break. Without restart, the same value is an index.
 *
 * Support for an index width and its topology/restart combination is device-dependent. Merely
 * listing a format here does not require a backend to support it or silently convert index data.
 *
 * @see [Vulkan index types](https://docs.vulkan.org/refpages/latest/refpages/source/VkIndexType.html)
 */
enum class IndexFormat(val sizeInBytes: Int, val restartIndex: UInt) {
    /**
     * Stores each index in one unsigned byte, with values from 0 through 255.
     *
     * Useful for small meshes; 255 is reserved for restart when restart is enabled. Eight-bit
     * index support is optional on some backends, so use a wider supported format when needed.
     */
    Uint8(1, 0xFFu),

    /**
     * Stores each index in two bytes, with unsigned values from 0 through 65,535.
     *
     * A common choice for meshes with modest local vertex counts. With restart enabled, 65,535
     * marks a break rather than a vertex. A base-vertex offset can locate the mesh elsewhere in
     * a larger vertex stream without widening the stored indices.
     */
    Uint16(2, 0xFFFFu),

    /**
     * Stores each index in four bytes as an unsigned 32-bit value.
     *
     * Use this when a mesh needs local indices beyond the 16-bit range. Values with the high bit
     * set remain unsigned indices, not negative Kotlin `Int` values. The restart marker is
     * `0xFFFFFFFF`, and is tested before the draw's signed base-vertex offset is applied.
     */
    Uint32(4, 0xFFFFFFFFu);

    /**
     * Checks that [view] describes a non-empty, tightly packed range of indices in this format.
     *
     * The byte offset and length must both be multiples of [sizeInBytes]. Requiring complete
     * indices prevents an ambiguous trailing partial element. Device ownership, backing-address
     * alignment, native limits, and support for this format are checked by the backend.
     *
     * @throws IllegalArgumentException if index usage, alignment, or range size is invalid.
     */
    fun validateBinding(view: GpuBufferView) {
        require(BufferUsage.Index in view.buffer.usage) { "Index binding requires BufferUsage.Index" }
        require(view.sizeBytes > 0) { "Index binding requires a non-empty buffer range" }
        require(view.offsetBytes % sizeInBytes == 0L) {
            "Index binding offset must be aligned to $sizeInBytes bytes"
        }
        require(view.sizeBytes % sizeInBytes == 0L) {
            "Index binding size must contain a whole number of $sizeInBytes-byte indices"
        }
    }

    /**
     * Checks the index elements selected by a draw and returns their absolute byte offset in
     * [view]'s buffer.
     *
     * [firstIndex] counts index elements from the start of the bound view. For example, a
     * 16-bit view starting at byte 1024 with `firstIndex = 3` starts reading at byte 1030.
     * [indexCount] includes restart markers: they consume index entries even though they do
     * not fetch vertices. A zero count may select the end of the view.
     *
     * This checks the declared index range even for a draw with zero instances. It does not
     * inspect index values or prove that their eventual vertex fetches are in bounds.
     *
     * @throws IllegalArgumentException if the binding is invalid, an argument is negative, or
     * the requested index range exceeds the view.
     */
    fun validateDrawRange(view: GpuBufferView, indexCount: Int, firstIndex: Int = 0): Size {
        validateBinding(view)
        require(indexCount >= 0) { "Index count must not be negative" }
        require(firstIndex >= 0) { "First index must not be negative" }

        val availableIndices = view.sizeBytes / sizeInBytes
        val first = firstIndex.toLong()
        require(first <= availableIndices) { "First index exceeds the bound index range" }
        require(indexCount.toLong() <= availableIndices - first) { "Indexed draw exceeds the bound index range" }

        // Containment proves both the product and the absolute offset fit in the buffer.
        return view.offsetBytes + first * sizeInBytes
    }
}
