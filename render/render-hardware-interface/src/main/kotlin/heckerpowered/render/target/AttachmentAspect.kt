/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.target

/**
 * Selects which contents of an attachment an operation acts on: color, depth, or stencil.
 *
 * An attachment's format determines which aspects it contains. A color format contains [Color],
 * a depth-only format contains [Depth], and a combined depth/stencil format contains both
 * [Depth] and [Stencil].
 *
 * Selecting an aspect allows those contents to be handled separately. For example, a scene pass
 * may use depth to determine visibility and stencil to mark a selected object. A later pass may
 * tint the marked region without needing the original depth values. Discarding only [Depth]
 * gives up those values while preserving the stencil marks.
 *
 * The aspect selects the contents being addressed, not what happens to them. That behavior is
 * determined by the operation receiving this value.
 */
enum class AttachmentAspect {
    /**
     * Selects all color components present in the attachment's format, including alpha when
     * present. Individual red, green, blue, and alpha components are not separate aspects.
     *
     * "Color" describes the format category, not the application's interpretation of its values.
     * An `R32Float` image storing distances or a mask still has a color aspect, not a depth aspect.
     */
    Color,

    /**
     * Selects the depth values used by depth testing, without selecting any stencil values stored
     * in the same attachment.
     */
    Depth,

    /**
     * Selects the stencil values used by stencil testing, without selecting any depth values
     * stored in the same attachment.
     */
    Stencil,
}