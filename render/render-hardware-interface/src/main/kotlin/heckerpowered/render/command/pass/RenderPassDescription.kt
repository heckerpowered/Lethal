/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command.pass

import heckerpowered.render.color.Color
import heckerpowered.render.command.validateKnownImageDisjoint
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.target.validateMetadata
import heckerpowered.render.resource.texture.TextureAspect
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
 * [renderArea] and [layerCount] select the pixels and layers affected by the pass. The area defaults
 * to the common extent of the direct attachments; attachmentless passes must specify it. The commands
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
 * @property depthAttachment Supplies depth values for deciding which surfaces are visible.
 *
 * This position's load and store operations affect only [TextureAspect.Depth], even when
 * the attachment also exposes stencil. Binding it does not enable depth testing: the
 * pipeline controls the comparisons and depth writes used by each draw.
 *
 * @property stencilAttachment Supplies integer stencil marks for masking or classifying drawing regions.
 *
 * This position's operations affect only [TextureAspect.Stencil]. It can reference the same
 * combined attachment as [depthAttachment] while choosing independent load/store operations.
 *
 * @property layerCount Number of layers used from the start of each attachment's exposed range. For example,
 * a count of two on a selection of layers four through seven uses layers four and five.
 * More layers do not repeat each draw or enable multiview automatically.
 *
 * @throws IllegalArgumentException if the attachment metadata, render area, layer count, sample
 * counts, depth clear value, or declared color resolves fail [validateAttachments].
 */
class RenderPassDescription(val label: String, renderArea: RenderArea? = null, colorAttachments: List<RenderPassAttachment<Color>?> = emptyList(), val depthAttachment: RenderPassAttachment<Float>? = null, val stencilAttachment: RenderPassAttachment<UByte>? = null, val layerCount: Int = 1, colorResolves: List<ColorAttachmentResolve> = emptyList()) {
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
     * Concrete region affected in every participating attachment layer.
     *
     * Omitting the constructor argument, or passing null, selects the largest upper-left-origin
     * rectangle covered by all direct color, depth, and stencil attachments: the minimum width
     * and minimum height. Equally sized attachments therefore use their whole extent. Different
     * sizes use only their common extent, not the extent of an arbitrarily selected attachment.
     * Resolve destinations do not determine this default. An attachmentless pass must supply an
     * explicit region because no image defines its dimensions.
     *
     * The default is resolved once during construction; backends always receive a non-null area.
     * An explicit area must fit every attachment and is never silently clipped. Use it for an
     * atlas cell or a split-screen region. The pass starts with a [Viewport] matching this area
     * and this area as its effective clip.
     *
     * [RenderPass.withViewport] changes selected draws' mapping, and [RenderPass.withScissor]
     * adds temporary clips. Neither changes attachment load, clear, or store operations.
     */
    val renderArea: RenderArea = renderArea ?: commonAttachmentArea()

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

    private fun commonAttachmentArea(): RenderArea {
        val first = colorAttachments.firstNotNullOfOrNull { it?.attachment } ?: depthAttachment?.attachment ?: stencilAttachment?.attachment
        requireNotNull(first) { "$label: an attachmentless pass requires an explicit render area" }

        var width = first.width
        var height = first.height
        forEachAttachment { _, attachment ->
            width = minOf(width, attachment.width)
            height = minOf(height, attachment.height)
        }
        return RenderArea(0, 0, width, height)
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