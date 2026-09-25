/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command.pass

/**
 * Restricts rasterized drawing to a rectangular part of the framebuffer without moving geometry.
 *
 * For example, an interface pass can clear its background and draw several panels. A scrolling
 * list should draw only inside its panel, while the next panel must remain unaffected. Use
 * [RenderPass.withScissor] around the list's draws; leaving that block restores the enclosing
 * clip without requiring a matching reset call.
 *
 * [RenderArea] belongs to the entire pass: it bounds the selected attachments' load, clear,
 * store, and drawing effects. A scissor is an additional limit for a group of draws inside
 * that pass. It does not change those attachment operations or start another pass. In particular,
 * a small scissor around a list does not shrink the pass's initial background clear.
 *
 * A draw's effective clip is the intersection of the render area and all enclosing scissors.
 * A child clip can narrow its parent's coverage but cannot reopen pixels excluded by the parent.
 * Unlike [Viewport], a scissor does not change where or at what scale geometry appears.
 *
 * Coordinates identify integer pixel edges in the same upper-left-origin framebuffer space as
 * [RenderArea], not relative to a parent clip or viewport. The selected pixels are
 * [x, x + width) by [y, y + height). Negative origins and rectangles extending outside the pass
 * are allowed; only their intersection with the pass can contribute coverage.
 *
 * A zero width or height admits no pixels. It does not mean clipping is disabled, and it does
 * not skip the recording callback. Scissoring is not a general restriction on shader resource
 * writes or a promise that all shader execution can be omitted.
 *
 * @throws IllegalArgumentException if width or height is negative.
 *
 * @see [Vulkan dynamic scissor](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdSetScissor.html)
 * @see [OpenGL scissor test](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_viewport_array.txt)
 */
data class ScissorRectangle(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(width >= 0 && height >= 0) { "Scissor dimensions must not be negative" }
    }

    val isEmpty: Boolean
        get() = width == 0 || height == 0

    /**
     * Selects pixels present in both rectangles. Touching edges do not share any pixels.
     *
     * An empty intersection returns [Empty]. Endpoints are computed in a wider representation
     * so an off-screen rectangle does not wrap around before it is clipped to the render area.
     * This operation does not translate either rectangle into the other's coordinate system.
     */
    fun intersect(other: ScissorRectangle): ScissorRectangle {
        if (isEmpty || other.isEmpty) return Empty

        val left = maxOf(x, other.x)
        val top = maxOf(y, other.y)
        val right = minOf(x.toLong() + width, other.x.toLong() + other.width)
        val bottom = minOf(y.toLong() + height, other.y.toLong() + other.height)
        if (right <= left.toLong() || bottom <= top.toLong()) return Empty

        // An intersection cannot be wider or taller than either input's non-negative Int extent.
        return ScissorRectangle(left, top, (right - left).toInt(), (bottom - top).toInt())
    }

    companion object {
        /** Canonical empty clip. Its origin has no effect because it contains no pixels. */
        val Empty = ScissorRectangle(0, 0, 0, 0)

        /** Selects the pass's complete drawing region before any additional scoped clipping. */
        fun from(area: RenderArea): ScissorRectangle =
            ScissorRectangle(area.x, area.y, area.width, area.height)
    }
}
