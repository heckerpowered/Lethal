/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.index

import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Selects existing GPU bytes interpreted as tightly packed indices in [format].
 *
 * Construction checks index usage, a non-empty range, and byte offset and length alignment to
 * the index width. It does not inspect index values, establish device format support, or prove
 * that the vertices selected by those values exist. The view keeps the same storage rather
 * than copying its contents; its resource must remain valid through GPU completion.
 */
data class IndexSelection(
    val view: GpuBufferView,
    val format: IndexFormat,
) {
    init {
        format.validateBinding(view)
    }
}
