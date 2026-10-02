/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Selects the buffer range and integer representation used to fetch a draw's indices.
 *
 * The binding translates to `RenderPass.bindIndexBuffer`. [IndexedDrawArguments] selects element
 * counts and offsets relative to [view]; the view's byte offset already selects its buffer region.
 * Binding alignment and index usage are checked at construction. Buffer contents and lifetime
 * remain those of the referenced resource.
 */
internal data class IndexInput(
    val view: GpuBufferView,
    val format: IndexFormat,
) {
    init {
        format.validateBinding(view)
    }
}
