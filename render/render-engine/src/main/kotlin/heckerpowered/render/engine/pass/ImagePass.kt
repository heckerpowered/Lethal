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
 * [shader] must sample the source and emit geometry covering the target with the intended copy
 * mapping. An arbitrary shader is accepted; this wrapper does not prove those properties. The
 * builtin copy shader uses the source sampler, rather than a texel-preserving image transfer.
 * Source and target must use distinct textures.
 */
fun RenderStageBuilder.copyImage(source: RenderImage, target: RenderImage, shader: MeshShader<RenderImage>) {
    require(source.view.texture !== target.view.texture) { "Copy-by-sampling cannot sample its output" }
    screen(replacementPass("copy image", target), shader.bind(source))
}

/**
 * Draws a replacement pass with [source] bound to [shader]. The shader must implement the desired
 * tent filter and cover the destination; the wrapper neither inspects its code nor forces coverage.
 */
fun RenderStageBuilder.tent(source: RenderImage, target: RenderImage, shader: MeshShader<RenderImage>) {
    tent(source, replacementPass("tent replace", target), shader, CompositingMode.Replace)
}

/** Draws into explicit attachment operations; [shader] must implement the requested filter. */
fun RenderStageBuilder.tent(source: RenderImage, description: RenderPassDescription, shader: MeshShader<RenderImage>, composition: CompositingMode) {
    require(description.colorAttachments.filterNotNull().none { it.attachment === source.attachment }) { "Tent source and destination must be disjoint" }
    screen(description, shader.bind(source), composition)
}

/**
 * Binds [source] to [shader] and draws using the selected compositing operator.
 * Sampling and coverage remain requirements on the supplied shader.
 *
 * [description] determines whether the destination is loaded, cleared, or discarded before the
 * draw. It must not bind the source image as a color destination; resource validity and access
 * compatibility remain subject to the pass and RHI checks.
 */
fun RenderStageBuilder.composite(source: RenderImage, description: RenderPassDescription, composition: CompositingMode, shader: MeshShader<RenderImage>) {
    require(description.colorAttachments.filterNotNull().none { it.attachment === source.attachment })
    screen(description, shader.bind(source), composition)
}
