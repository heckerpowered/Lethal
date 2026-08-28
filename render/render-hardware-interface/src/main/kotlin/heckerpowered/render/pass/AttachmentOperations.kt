/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.RenderPassDescription
import heckerpowered.render.RenderTarget

/**
 * Groups the attachment load and store operations configured by a [RenderPassDescription].
 *
 * A render pass description assigns one instance to each color attachment supplied by its
 * [RenderTarget], and separate instances to the depth and stencil aspects of that target's
 * depth/stencil attachment. [load] determines the contents available when the render pass begins,
 * while [store] determines whether contents produced during the pass remain available after it
 * ends.
 *
 * These choices apply to that render pass. Another render pass using the same render target may
 * assign different operations.
 *
 * `T` is the clear-value type accepted by [load]. It has no runtime significance when [load] is
 * [AttachmentLoadOperation.Load] or [AttachmentLoadOperation.Discard].
 */
data class AttachmentOperations<out T>(
    val load: AttachmentLoadOperation<T>,
    val store: AttachmentStoreOperation,
) {
    companion object {
        /**
         * Loads the attachment's existing contents when the render pass begins and preserves its
         * resulting contents after the pass ends.
         *
         * Use this for an attachment that follows the render pass's conventional preservation behavior.
         * Its existing contents must already be defined before the pass begins.
         */
        val Default: AttachmentOperations<Nothing> = AttachmentOperations(
            load = AttachmentLoadOperation.Load,
            store = AttachmentStoreOperation.Store,
        )
    }
}