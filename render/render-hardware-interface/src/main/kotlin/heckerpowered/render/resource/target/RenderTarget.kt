/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.target

/**
 * Provides a reusable selection of attachments for passes that draw into the same destination.
 *
 * Each pass chooses its own attachment operations. Reusing a target therefore does not require
 * reusing the load and store choices of an earlier pass.
 *
 * A target describes a fixed attachment selection. It must not silently switch to different images
 * when a presentation frame advances or the underlying output is recreated.
 */
interface RenderTarget {
    val colorAttachments: List<RenderAttachment>
    val depthAttachment: RenderAttachment?
    val stencilAttachment: RenderAttachment?
}