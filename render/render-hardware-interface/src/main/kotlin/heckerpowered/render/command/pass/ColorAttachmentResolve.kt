/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command.pass

import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.validateKnownImageDisjoint
import heckerpowered.render.resource.texture.TextureStorage

/**
 * Arranges for a drawn color attachment to produce a separate single-sample image as its pass ends.
 *
 * For example, a scene can draw four samples per pixel into color slot 0 and resolve them into a
 * texture later sampled by tone mapping. Slot 0 still names the image receiving the draws; this
 * value explicitly names the other image receiving the resolve. Neither image becomes an implicit
 * "final output", and creating this value allocates no storage or records any drawing.
 *
 * Supply it in [RenderPassDescription.colorResolves] before entering the pass. The backend then
 * knows the destination when it establishes native rendering; it need not save all draws to discover
 * a later resolve. This is also the direct path for a memoryless source: consume its samples within
 * the producing scope and use [AttachmentStoreOperation.Discard] when the original samples are not
 * needed afterward. A backed source may instead use Store to keep both representations.
 *
 * The operation consumes the source's final samples before its store/discard boundary. It replaces
 * the selected destination contents; no destination load or clear is performed. The destination
 * must not otherwise be read or written during the pass, and must not overlap another resolve
 * destination or a directly used attachment. Unselected contents are preserved.
 *
 * The source is the pass's entire render area and its participating layers. The destination must
 * select the same x/y rectangle, width, height, and layer count, with a two-dimensional, single-sample
 * color image of the same format. Its mip and base layer may differ. This deliberately describes
 * attachment resolve, not a translated, scaled, or arbitrary subrectangle operation. Use standalone
 * resolve with a retained source when that more general range selection is needed.
 *
 * Resolving uses the backend's standard color resolve, not a caller-defined filter. Exact sample
 * weighting and rounding are implementation-dependent; linear-space sRGB combination is not promised.
 * Viewport and scissor affect the draws, not this resolve's rectangle. All consumed samples must
 * have defined contents; an unwritten pixel is not initialized merely by resolving it.
 *
 * @see <a href="https://docs.vulkan.org/refpages/latest/refpages/source/VkRenderingAttachmentInfo.html">Vulkan attachment resolve</a>
 */
data class ColorAttachmentResolve(
    /** Index in [RenderPassDescription.colorAttachments], not a texture layer or descriptor slot. */
    val colorAttachment: Int,

    /**
     * Existing image contents that receive the result.
     *
     * Texture-based selections require ResolveDestination, not the RHI ColorAttachment usage.
     * They use Backed storage so the result remains available outside the producing scope. Views
     * retain their original access restrictions; opaque attachments need equivalent device checks.
     */
    val destination: ImageRegion,
) {
    init {
        require(colorAttachment >= 0) { "Resolve color attachment index must be non-negative" }
    }

    /**
     * Selects the source from the pass rather than storing a second, potentially different source.
     *
     * Layer zero is the first layer exposed by the selected attachment, not necessarily layer zero
     * of its texture. The result retains that attachment for device identity and permission checks.
     *
     * @throws IllegalArgumentException if the slot is absent or the pass area/layers do not fit it.
     */
    fun sourceFor(description: RenderPassDescription): ImageRegion.Attachment {
        val attachment = requireNotNull(description.colorAttachments.getOrNull(colorAttachment)) {
            "Resolve source color attachment $colorAttachment is not bound"
        }.attachment
        val area = description.renderArea
        return ImageRegion.Attachment(
            attachment = attachment,
            arrayLayerCount = description.layerCount,
            x = area.x,
            y = area.y,
            width = area.width,
            height = area.height,
        )
    }

    /**
     * Checks this relation's public metadata without recording or retaining execution state.
     *
     * The source is an attachment: its ResolveSource permission and storage mode come from backend
     * resource records, not a cast to a texture interface. The backend must also check destination
     * support, all native aliases, content availability, and scope-compatible load/store operations.
     * For a memoryless source, Store cannot promise retained samples after its native scope ends.
     *
     * @throws IllegalArgumentException if the selections cannot describe a pass-local color resolve.
     */
    fun validateFor(description: RenderPassDescription) {
        val source = sourceFor(description)
        source.validateResolveTo(destination)
        require(source.x == destination.x && source.y == destination.y) { "Pass-local resolve uses the render area's coordinates at both endpoints" }
        val texture = when (val region = destination) {
            is ImageRegion.Texture -> region.texture
            is ImageRegion.View -> region.view.texture
            is ImageRegion.Attachment -> null
        }
        require(texture == null || texture.storage == TextureStorage.Backed) { "Pass-local resolve requires a backed destination" }
        validateKnownImageDisjoint(source, destination, "Resolve source and destination select overlapping contents")
    }
}
