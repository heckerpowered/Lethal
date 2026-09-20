/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.target.RenderAttachment

/**
 * Associates an attachment with the load and store operations for one position in a render pass.
 *
 * The attachment identifies image data; [operation] determines whether this use starts with
 * existing values, a clear value, or undefined values, and whether its result remains needed.
 * Another pass can use the same attachment with different operations.
 *
 * The containing [RenderPassDescription] position selects the aspect. A depth position applies
 * these operations only to Depth, even if [attachment] also exposes Stencil. The same combined
 * attachment can appear in both positions with independent operations.
 *
 * `T` describes the clear value: color uses the RHI color value, depth uses [Float], and stencil
 * uses [UByte]. It does not change the underlying image format. Each use is bounded by the
 * pass's render area and selected layer count.
 */
data class RenderPassAttachment<out T>(
    val attachment: RenderAttachment,
    val operation: AttachmentOperations<T> = AttachmentOperations.Default,
)
