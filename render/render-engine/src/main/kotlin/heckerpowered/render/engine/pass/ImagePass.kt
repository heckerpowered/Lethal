/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.color.Color
import heckerpowered.render.command.pass.*
import heckerpowered.render.engine.image.RenderImage
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.shader.program.MeshShader
import heckerpowered.render.engine.stage.RenderStageBuilder

/** Clears [target] to transparent black before drawing and stores the result for later use. */
fun replacementPass(label: String, target: RenderImage): RenderPassDescription {
    val operations = AttachmentOperations(AttachmentLoadOperation.Clear(Color.TransparentBlack), AttachmentStoreOperation.Store)
    val attachment = RenderPassAttachment(target.attachment, operations)
    return RenderPassDescription(label, null, listOf(attachment))
}

/** Preserves [target]'s existing color for compositing and stores the resulting image. */
fun loadedPass(label: String, target: RenderImage): RenderPassDescription {
    val attachment = RenderPassAttachment<Color>(target.attachment)
    return RenderPassDescription(label, null, listOf(attachment))
}

/**
 * Replaces [target] with [source] sampled over its full extent.
 *
 * The source's sampler controls filtering and the image sizes may differ. This is a fullscreen
 * shader draw rather than a texel-preserving transfer. Source and target must use distinct textures.
 */
fun RenderStageBuilder.copyImage(source: RenderImage, target: RenderImage, shader: MeshShader<RenderImage>) {
    require(source.view.texture !== target.view.texture) { "Copy-by-sampling cannot sample its output" }
    screen(replacementPass("copy image", target), shader.bind(source))
}

/**
 * Filters [source] with a weighted 3-by-3 neighborhood and draws it across [target].
 *
 * Sample offsets use source texel size, allowing the same filter to downsample or enlarge an image.
 * Replacement clears the destination first; other operators load its existing color. Source and
 * target must use distinct textures, and [composition] must already specify a concrete operator.
 */
fun RenderStageBuilder.tent(source: RenderImage, target: RenderImage, shader: MeshShader<RenderImage>, composition: CompositingMode = CompositingMode.Replace) {
    require(source.view.texture !== target.view.texture) { "Tent source and destination must be disjoint" }
    val description = if (composition == CompositingMode.Replace) replacementPass("tent replace", target)
    else loadedPass("tent composite", target)
    screen(description, shader.bind(source), composition)
}

/**
 * Samples [source] across the pass's render area using a concrete compositing operator.
 *
 * [description] determines whether the destination is loaded, cleared, or discarded before the
 * draw. It must not bind the source image as a color destination; resource validity and access
 * compatibility remain subject to the pass and RHI checks.
 */
fun RenderStageBuilder.composite(source: RenderImage, description: RenderPassDescription, composition: CompositingMode, shader: MeshShader<RenderImage>) {
    require(description.colorAttachments.filterNotNull().none { it.attachment === source.attachment })
    screen(description, shader.bind(source), composition)
}
