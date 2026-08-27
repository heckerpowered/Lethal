/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.color

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.texture.TextureFormat

/**
 * Describes one indexed color output expected by a render pipeline.
 *
 * The position of this state in [RenderPipelineDescription.colorTargets] determines its target
 * index. A fragment-shader output at location `n` is processed by color target `n`.
 *
 * For each fragment, the target is conceptually processed as follows:
 *
 * ```
 * fragment output + existing attachment value
 *     -> optional blending
 *     -> component write mask
 *     -> representation and storage defined by the target format
 * ```
 *
 * This state declares pipeline compatibility and write behavior. It does not reference or own the
 * attachment itself. Attachment binding, clearing, loading, storing, and multisample resolving are
 * configured when rendering.
 */
data class ColorTargetState(
    /**
     * Exact format required for the color attachment at this target index.
     *
     * The format determines the stored representation and any conversion between stored and
     * shader-visible color values. The attached texture view must expose this format.
     *
     * Being a color format does not guarantee that every graphics device supports rendering,
     * blending, or multisampling with it. Such capabilities are checked when creating or using the
     * pipeline.
     */
    val format: TextureFormat,

    /**
     * Blending applied independently to this color target.
     *
     * `null` disables blending, causing the fragment output to replace every destination component
     * enabled by [writeMask].
     *
     * Blending does not initialize the destination value. If the blend equation depends on existing
     * attachment contents, rendering must make those contents valid through a clear, load, or
     * earlier write.
     */
    val blend: BlendState? = null,

    /**
     * Components of the final color result that may be stored.
     *
     * The mask is applied after blending. A disabled component preserves its destination value, but
     * that value may still participate in calculating enabled components.
     */
    val writeMask: ColorWriteMask = ColorWriteMask.All,
) {
    init {
        require(format.isColor) { "$format cannot be used as a color target because it is not a color format" }
    }
}