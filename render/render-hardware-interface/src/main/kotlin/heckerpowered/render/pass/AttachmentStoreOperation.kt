/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.RenderPassDescription
import heckerpowered.render.RenderTarget

/**
 * A [RenderPassDescription] assigns a store operation to each color attachment supplied by its
 * [RenderTarget], and separate store operations to the depth and stencil aspects of that target's
 * depth/stencil attachment.
 *
 * When the render pass ends, [Store] keeps the resulting contents available to later work, while
 * [Discard] indicates that those contents are no longer needed and may become undefined. The store
 * operation does not affect attachment access during the render pass itself.
 *
 * [AttachmentLoadOperation] separately determines the contents available when the render pass
 * begins.
 */
enum class AttachmentStoreOperation {
    /**
     * Preserves the attachment contents after the render pass ends.
     *
     * Use this when a later render pass or operation needs the generated color, depth, or stencil
     * values.
     *
     * Storing does not initialize values that were already undefined and never overwritten during
     * the pass. It only preserves the final contents that the pass actually established.
     */
    Store,

    /**
     * Indicates that the implementation does not need to preserve this attachment aspect after the
     * render pass.
     *
     * Typical reasons for choosing [Discard] include:
     *
     * - The attachment is only an intermediate result, and preserving it may require unnecessary
     *   storage or memory traffic.
     * - Its contents will be cleared or completely overwritten before any later operation could
     *   observe them.
     * - It uses transient or memoryless storage whose contents are not intended to survive the render
     *   pass.
     * - Its result has already been consumed or transferred into the persistent output that matters.
     *   For example, a deferred renderer may consume a transient G-buffer while producing the lit
     *   color, and an MSAA resolve transfers the useful image into the resolve attachment.
     * - Preserving it would impose a stronger post-pass contract than the rendering algorithm
     *   requires.
     *
     * Choosing [Discard] does not skip rendering or attachment access within the render pass. It only
     * removes the requirement to preserve defined contents afterward.
     *
     * After the render pass, the aspect's contents are undefined and must not be read until a later
     * operation defines them again. [Discard] does not clear the attachment or erase its underlying
     * storage.
     */
    Discard,
}