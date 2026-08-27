/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.depthstencil

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.texture.TextureFormat


/**
 * Declares the depth/stencil attachment expected by a render pipeline and controls how fragments
 * interact with its depth and stencil aspects.
 *
 * A depth aspect stores projected depth values used to determine which surfaces remain visible. A
 * stencil aspect stores small integer values that can label, mask, or count covered samples.
 * [depth] and [stencil] enable and configure these two kinds of processing independently.
 *
 * Typical configurations include:
 *
 * - Ordinary opaque 3D rendering normally uses a depth format with [depth] enabled and no stencil
 *   state.
 * - Outlines, portals, masks, and volume effects may use a combined depth/stencil format with both
 *   [depth] and [stencil] enabled.
 * - An overlay drawn inside an existing rendering scope may declare the attachment [format] while
 *   leaving both [depth] and [stencil] disabled.
 *
 * A non-null [DepthStencilState] always declares that a compatible attachment with exactly [format]
 * may be used by the pipeline. This remains true when both [depth] and [stencil] are `null`; those
 * values disable testing and updates without removing the attachment-format requirement. By
 * contrast, setting [RenderPipelineDescription.depthStencil] to `null` declares no depth/stencil
 * attachment requirement.
 *
 * This state does not create, own, clear, load, or store an attachment. Those operations belong to
 * the rendering scope in which the pipeline is used.
 */
data class DepthStencilState(
    /**
     * Exact format required for the depth/stencil attachment.
     *
     * The format must contain every aspect used by [depth] or [stencil]. An attachment used with the
     * pipeline must expose this exact format; implementations must not silently substitute another
     * depth precision or depth/stencil combination.
     *
     * The presence of a format in [TextureFormat] does not guarantee that every graphics device
     * supports using it as an attachment or with every sample count. Those capabilities are checked
     * during pipeline creation.
     */
    val format: TextureFormat,

    /**
     * Depth comparison and write behavior.
     *
     * `null` disables depth testing and depth writes. It does not remove a depth aspect already
     * present in [format], nor does it determine how that aspect is cleared, loaded, or stored.
     */
    val depth: DepthState? = null,

    /**
     * Stencil comparison and update behavior.
     *
     * `null` disables stencil testing and stencil updates. It does not remove a stencil aspect
     * already present in [format], nor does it determine how that aspect is cleared, loaded, or
     * stored.
     */
    val stencil: StencilState? = null,
) {
    init {
        require(format.hasDepth || format.hasStencil) { "$format cannot be used as a depth/stencil attachment" }
        require(depth == null || format.hasDepth) { "Depth state requires a format with a depth aspect, but $format has none" }
        require(stencil == null || format.hasStencil) { "Stencil state requires a format with a stencil aspect, but $format has none" }
    }
}