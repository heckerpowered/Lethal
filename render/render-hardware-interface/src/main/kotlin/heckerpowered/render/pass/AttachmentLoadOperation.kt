/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.RenderPassDescription
import heckerpowered.render.RenderTarget

/**
 * A [RenderPassDescription] assigns a load operation to each color attachment supplied by its
 * [RenderTarget], and separate load operations to the depth and stencil aspects of that target's
 * depth/stencil attachment.
 *
 * Each operation determines the corresponding contents when the render pass begins, before draw
 * commands are executed. [Load] preserves values produced earlier, [Clear] replaces them with a
 * specified value, and [Discard] leaves them undefined.
 *
 * [AttachmentStoreOperation] controls whether contents produced during the render pass remain
 * available after it ends.
 */
sealed interface AttachmentLoadOperation<out T> {
    /**
     * Preserves the aspect's existing contents as the initial contents of the render pass.
     *
     * Use this when rendering depends on values produced earlier, such as blending with existing color,
     * preserving pixels not covered by new drawing, or continuing depth or stencil testing.
     *
     * The existing contents must already be defined. Loading a newly created aspect or one previously
     * discarded does not initialize it.
     */
    data object Load : AttachmentLoadOperation<Nothing>

    /**
     * Indicates that the render pass does not care about the aspect's previous contents.
     *
     * Use this when every value that can affect the result will be established by the current pass,
     * such as an opaque full-target draw that independently replaces its color output. This may allow
     * the implementation to avoid preserving earlier contents.
     *
     * The initial contents are undefined and are chosen freely by the implementation. They must not be
     * read before being replaced with defined values; blending, depth testing, and stencil testing that
     * depend on existing attachment values count as reads.
     */
    data object Discard : AttachmentLoadOperation<Nothing>

    /**
     * Initializes the attachment aspect to [value] when the render pass begins.
     *
     * Use this when rendering requires a known initial value, such as a background color, the far depth
     * value, or an empty stencil mask.
     *
     * The clear occurs before draw commands and is not restricted by pipeline tests or write masks.
     * Clearing the depth or stencil aspect of a combined attachment does not modify the other aspect.
     */
    data class Clear<T>(val value: T) : AttachmentLoadOperation<T>
}