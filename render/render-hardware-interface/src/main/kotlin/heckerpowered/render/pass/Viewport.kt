/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

/**
 * Maps normalized geometry coordinates into a rectangle of framebuffer coordinates.
 *
 * For example, two views of a scene can share one color attachment: draw one using a viewport
 * covering its left half and the other using a viewport covering its right half. Changing the
 * viewport moves and scales the geometry; changing a [ScissorRectangle] only clips its coverage.
 *
 * In the RHI coordinate convention, normalized (-1, -1) maps to (x, y), and (1, 1) maps to
 * (x + width, y + height). Coordinates have an upper-left origin, with y increasing downward.
 * Positions and dimensions may have fractional pixel values. [minDepth] receives normalized
 * depth zero, and [maxDepth] receives normalized depth one.
 *
 * A viewport is not an attachment-access boundary. It may extend beyond the pass's [RenderArea];
 * the render area and active scissors still limit rasterized attachment coverage. Device limits
 * on viewport coordinates, dimensions, and precision are checked when the viewport is used.
 *
 * @throws IllegalArgumentException if a coordinate or extent is non-finite, an extent is not
 * positive, an end coordinate is non-finite, or a depth endpoint is outside `[0, 1]`.
 *
 * @see [Vulkan VkViewport](https://docs.vulkan.org/refpages/latest/refpages/source/VkViewport.html)
 * @see [OpenGL viewport arrays](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_viewport_array.txt)
 */
data class Viewport(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,

    /** Value assigned to normalized depth zero; it need not be the smaller endpoint. */
    val minDepth: Float = 0.0F,

    /**
     * Value assigned to normalized depth one.
     *
     * Reversed or equal endpoints are representable. They change the depth mapping, not the
     * pipeline's depth comparison or the attachment's clear value. Device support is checked
     * separately; an implementation must not silently reorder the endpoints.
     */
    val maxDepth: Float = 1.0F,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Viewport origin must be finite" }
        require(width.isFinite() && height.isFinite() && width > 0.0F && height > 0.0F) { "Viewport dimensions must be finite and positive" }
        require((x + width).isFinite() && (y + height).isFinite()) { "Viewport end coordinates must be finite" }
        require(minDepth in 0.0F..1.0F && maxDepth in 0.0F..1.0F) { "Viewport depth endpoints must be finite and between zero and one" }
    }

    companion object {
        /** Creates the default mapping for a pass: its render-area rectangle and depth `[0, 1]`. */
        fun from(area: RenderArea): Viewport = Viewport(
            x = area.x.toFloat(),
            y = area.y.toFloat(),
            width = area.width.toFloat(),
            height = area.height.toFloat(),
        )
    }
}
