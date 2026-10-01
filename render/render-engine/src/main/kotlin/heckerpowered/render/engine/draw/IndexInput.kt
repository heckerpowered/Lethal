/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * The concrete index binding consumed by one prepared indexed draw.
 *
 * This is intentionally a near-RHI value: [view] and [format] translate mechanically to
 * `RenderPass.bindIndexBuffer`. Draw-relative selection remains in [IndexedDrawArguments].
 * The view borrows its buffer and does not snapshot index contents or extend resource lifetime.
 */
internal data class IndexInput(
    val view: GpuBufferView,
    val format: IndexFormat,
) {
    init {
        format.validateBinding(view)
    }
}
