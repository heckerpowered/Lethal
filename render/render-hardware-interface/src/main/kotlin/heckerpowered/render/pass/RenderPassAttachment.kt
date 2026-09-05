/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.target.RenderAttachment


/**
 * Associates an attachment with the load and store operations chosen for one render pass.
 *
 * The same attachment can be paired with different operations in different passes. For example,
 * a scene pass can clear it before drawing, while a later overlay pass loads the existing image.
 */
data class RenderPassAttachment<out T>(
    val attachment: RenderAttachment,
    val operation: AttachmentOperations<T> = AttachmentOperations.Default,
)