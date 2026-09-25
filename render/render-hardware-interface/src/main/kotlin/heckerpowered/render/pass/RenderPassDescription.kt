/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.color.Color
import heckerpowered.render.command.validateKnownImageDisjoint
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.target.RenderAttachment
import heckerpowered.render.target.validateMetadata
import heckerpowered.render.texture.TextureAspect
import java.util.*

/**
 * Chooses the images that a group of draws contributes to and how their contents are handled
 * before and after drawing.
 *
 * A scene can be built from separate draws for terrain, characters, and other objects. A render
 * pass lets these draws share the same color, depth, and stencil attachments. Pipelines and
 * resource bindings may change between draws without changing the attachments or repeating
 * their load operations.
 *
 * Each attachment use chooses how drawing begins: continue existing contents, clear to a known
 * value, or discard values that will be replaced. It also chooses whether the result must remain
 * available afterward. For example, a scene and an overlay can use the same color image:
 *
 * ```
 * scene pass
 *   color -> clear background -> draw terrain and objects -> store
 *   depth -> clear far depth  -> test and update depth    -> discard
 *
 * overlay pass
 *   color -> load scene color -> draw interface           -> store
 * ```
 *
 * The overlay adds to the scene rather than clearing it again. Depth is only working data in
 * this example; a later operation that reads the scene depth would require storing it instead.
 * Load and store choices belong to each use, not permanently to the underlying image.
 *
 * [renderArea] and [layerCount] select the pixels and layers affected by the pass. The commands
 * that draw geometry, bind pipelines, and supply shader resources are recorded separately
 * through [RenderPass]; this description provides their attachment setup, not the commands or
 * new image storage.
 *
 * [colorResolves] optionally names single-sample destinations produced from selected color
 * attachments at the end of drawing, before the source contents are stored or discarded.
 * Declaring these relations up front permits direct native recording without inspecting future
 * commands. They do not change which attachments receive fragment outputs.
 *
 * The pass boundary is logical, but no automatic grouping with later commands is promised.
 * A backend may group native work only when the requested content and access rules are preserved.
 *
 * @throws IllegalArgumentException if the attachment metadata, render area, layer count, sample
 * counts, depth clear value, or declared color resolves fail [validateAttachments].
 */
class RenderPassDescription(
    val label: String,

    /**
     * Region affected in each participating attachment layer.
     *
     * Use a smaller rectangle to update part of a larger image, such as one cell in a texture
     * atlas. Every bound attachment must cover this rectangle, but their complete dimensions
     * need not match. The pass begins with a [Viewport] covering this rectangle and with this
     * rectangle as its effective drawing clip. These defaults are established anew for each pass.
     *
     * [RenderPass.withViewport] changes the mapping for selected draws; [RenderPass.withScissor]
     * adds temporary drawing clips. Neither changes this region or the attachment load/clear/store
     * operations. For example, clipping a scrolling list does not clip the pass's background clear.
     */
    val renderArea: RenderArea,
    colorAttachments: List<RenderPassAttachment<Color>?> = emptyList(),

    /**
     * Supplies depth values for deciding which surfaces are visible.
     *
     * This position's load and store operations affect only [TextureAspect.Depth], even when
     * the attachment also exposes stencil. Binding it does not enable depth testing: the
     * pipeline controls the comparisons and depth writes used by each draw.
     */
    val depthAttachment: RenderPassAttachment<Float>? = null,

    /**
     * Supplies integer stencil marks for masking or classifying drawing regions.
     *
     * This position's load and store operations affect only [TextureAspect.Stencil]. It can
     * reference the same combined attachment as [depthAttachment] while choosing different
     * operations, such as keeping depth and clearing stencil for a new selection mask.
     * The pipeline and stencil reference determine how draws test and update the marks.
     */
    val stencilAttachment: RenderPassAttachment<UByte>? = null,

    /**
     * Number of layers used from the start of each attachment's exposed layer range.
     *
     * For example, if an attachment exposes texture layers 4 through 7, a count of 2 uses texture
     * layers 4 and 5. The pass does not select layers from the start of the complete texture.
     *
     * The default uses one layer. A larger count makes more layers available to supported layered
     * rendering; it does not repeat each draw for every layer or enable multiview by itself.
     */
    val layerCount: Int = 1,
    colorResolves: List<ColorAttachmentResolve> = emptyList(),
) {
    /**
     * Images that receive the fragment shader's color outputs, together with their load and
     * store operations for this pass.
     *
     * List indices correspond to fragment-output locations. A shader can write scene color to
     * location 0 and a second result, such as a normal image, to location 1. A null entry leaves
     * its location unbound without renumbering later entries: `[first, null, third]` binds
     * locations 0 and 2.
     *
     * Each bound attachment must expose [TextureAspect.Color]. The list is an unmodifiable copy
     * of the supplied selection, so changing the caller's list does not reconfigure this pass.
     */
    val colorAttachments: List<RenderPassAttachment<Color>?> =
        Collections.unmodifiableList(colorAttachments.toList())

    /**
     * Explicit color-source-to-destination relations completed as this pass ends.
     *
     * An empty list performs no resolve. Each entry names an existing color slot and a separate
     * destination; it neither changes the slot's attachment nor selects a final output implicitly.
     * At most one destination is declared for each slot. More destinations or later snapshots can
     * use standalone resolve when the source is retained and its storage supports those accesses.
     *
     * Resolves consume the final samples before source Discard takes effect. Store is needed only
     * when the original samples are also needed after this pass; it does not control whether the
     * resolve result is written. Destinations do not contribute to [attachmentSampleCount].
     *
     * The list is an unmodifiable snapshot. Backends validate native aliasing and prohibit accesses
     * to resolve destinations during the pass. A resolve-only texture need not acquire the RHI
     * ColorAttachment usage, even if a native implementation uses attachment machinery internally.
     */
    val colorResolves: List<ColorAttachmentResolve> =
        Collections.unmodifiableList(colorResolves.toList())

    /**
     * Common sample count required by the direct attachments, or null when none are bound.
     *
     * All bound color, depth, and stencil attachments must have the same sample count in this
     * model. Draws must use a matching rasterization count; [validateSampleCount] checks this
     * part of pipeline compatibility. Shader input textures and separate resolve destinations
     * do not participate in this count.
     *
     * A null result does not select single-sampled rendering. An attachmentless pass uses the
     * draw's rasterization configuration, subject to the device's attachmentless-rendering limits.
     */
    val attachmentSampleCount: SampleCount?
        get() = colorAttachments.firstNotNullOfOrNull { it?.attachment?.sampleCount }
            ?: depthAttachment?.attachment?.sampleCount
            ?: stencilAttachment?.attachment?.sampleCount

    init {
        validateAttachments()
    }

    /**
     * Checks aspects, image metadata, render bounds, layers, sample agreement, and color resolves.
     *
     * Backends can repeat these public metadata checks before recording. Identity and overlap of
     * native images, imported-resource availability, format support, and content scope still
     * require device knowledge and must not be inferred from Kotlin object equality.
     *
     * @throws IllegalArgumentException if the description contradicts the attachment metadata.
     */
    fun validateAttachments() {
        require(layerCount > 0) { "Render pass layer count must be positive" }
        colorAttachments.forEachIndexed { index, binding ->
            binding?.let { requireAspect(it.attachment, TextureAspect.Color, "color attachment $index") }
        }
        depthAttachment?.let { requireAspect(it.attachment, TextureAspect.Depth, "depth attachment") }
        stencilAttachment?.let { requireAspect(it.attachment, TextureAspect.Stencil, "stencil attachment") }

        var samples: SampleCount? = null
        forEachAttachment { position, attachment ->
            attachment.validateMetadata()
            renderArea.validateFor(attachment.width, attachment.height)
            require(layerCount <= attachment.arrayLayerCount) { "$label: $position exposes ${attachment.arrayLayerCount} layers, but the pass requires $layerCount" }
            
            val expected = samples
            require(expected == null || attachment.sampleCount == expected) { "$label: $position has ${attachment.sampleCount.value} samples; other direct attachments have ${expected?.value}" }
            samples = attachment.sampleCount
        }

        val depthLoad = depthAttachment?.operation?.load
        if (depthLoad is AttachmentLoadOperation.Clear) {
            require(depthLoad.value in 0.0F..1.0F) { "$label: depth clear value must be finite and between zero and one" }
        }
        validateColorResolves()
    }

    /**
     * Checks a draw's rasterization sample count against the direct attachments.
     *
     * This does not compare shader input textures or resolve destinations. With no attachments
     * there is no image count to match; the device must still validate attachmentless rendering.
     * Other pipeline compatibility requirements are checked by the draw implementation.
     *
     * @throws IllegalArgumentException if a direct attachment's sample count differs from [sampleCount].
     */
    fun validateSampleCount(sampleCount: SampleCount) {
        forEachAttachment { position, attachment ->
            require(attachment.sampleCount == sampleCount) { "$label: $position has ${attachment.sampleCount.value} samples, but the pipeline requires ${sampleCount.value}" }
        }
    }

    private fun validateColorResolves() {
        if (colorResolves.isEmpty()) return
        val sources = HashSet<Int>()
        colorResolves.forEach { resolve ->
            require(sources.add(resolve.colorAttachment)) { "$label: color attachment ${resolve.colorAttachment} has more than one pass-local resolve" }
            resolve.validateFor(this)
        }
        colorResolves.forEachIndexed { index, first ->
            for (otherIndex in index + 1 until colorResolves.size) {
                validateKnownImageDisjoint(
                    first.destination,
                    colorResolves[otherIndex].destination,
                    "$label: pass-local resolve destinations select overlapping contents",
                )
            }
        }
    }

    private fun requireAspect(attachment: RenderAttachment, aspect: TextureAspect, position: String) {
        require(aspect in attachment.aspects) { "$label: $position does not expose $aspect" }
    }

    private inline fun forEachAttachment(action: (String, RenderAttachment) -> Unit) {
        colorAttachments.forEachIndexed { index, binding ->
            binding?.let { action("color attachment $index", it.attachment) }
        }
        depthAttachment?.let { action("depth attachment", it.attachment) }
        stencilAttachment?.let { action("stencil attachment", it.attachment) }
    }
}
