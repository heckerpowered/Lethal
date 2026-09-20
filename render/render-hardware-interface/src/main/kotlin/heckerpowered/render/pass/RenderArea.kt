/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

/**
 * Selects the rectangular region of each attachment that a render pass operates on.
 *
 * For example, an atlas update can render into one rectangle of a larger image without clearing
 * or discarding the surrounding pixels. Every attachment must contain this rectangle, but their
 * complete dimensions need not match.
 *
 * Coordinates are integer pixel edges in the RHI's upper-left-origin framebuffer coordinates.
 * The region is `[x, x + width)` by `[y, y + height)`. It bounds attachment clear, load, store,
 * and drawing effects; it does not itself choose the viewport's clip-to-framebuffer transform.
 * Backends must keep drawing inside it, for example by intersecting it with the draw scissor.
 *
 * This bounds logical image effects, not the footprint of native memory transactions. Disjoint
 * rectangles do not by themselves establish that concurrent accesses to one image are safe.
 *
 * @throws IllegalArgumentException if an origin is negative, an extent is not positive, or an
 * exclusive end coordinate cannot be represented by a non-negative [Int].
 */
data class RenderArea(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(x >= 0 && y >= 0) { "Render area origin must not be negative" }
        require(width > 0 && height > 0) { "Render area dimensions must be positive" }
        require(width <= Int.MAX_VALUE - x && height <= Int.MAX_VALUE - y) { "Render area end coordinates exceed the supported integer range" }
    }

    /**
     * Checks that this rectangle fits without clipping in an image of the given dimensions.
     *
     * @throws IllegalArgumentException if the dimensions are not positive or any part is outside.
     */
    fun validateFor(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Attachment dimensions must be positive" }
        require(x <= width && y <= height) { "Render area origin is outside the attachment" }
        require(this.width <= width - x && this.height <= height - y) { "Render area exceeds the attachment dimensions" }
    }
}
