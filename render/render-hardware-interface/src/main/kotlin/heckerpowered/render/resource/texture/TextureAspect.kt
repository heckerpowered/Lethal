/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

/**
 * Selects which image data a view exposes or an image operation addresses.
 *
 * An ordinary color texture exposes one color image. A combined depth-stencil texture instead
 * provides two kinds of data at the same image positions: depth values used to compare surface
 * visibility, and integer stencil marks used to identify regions for later drawing.
 *
 * Keeping both in one texture does not make them one indivisible value. For example, a scene
 * can record depth while marking a selected object's pixels in stencil. A later depth-based
 * effect needs only the depth values. Resetting the selection mask instead needs to clear the
 * stencil marks without clearing the depth image.
 *
 * The format describes which parts exist and how their values are represented. An aspect
 * selection describes which of those parts the current view or operation addresses:
 *
 * ```
 * same depth-stencil texture, mip level, and array layers
 *     {Depth}          -> the depth image
 *     {Stencil}        -> the stencil image
 *     {Depth, Stencil} -> both parts, for an operation that accepts them together
 * ```
 *
 * These selections refer to the existing data; they do not create separate image storage or
 * copy the contents. Selecting depth from a combined format does not remove its stencil data.
 * An operation must support the requested selection; exposing both parts does not imply that
 * a single shader binding can read both together.
 */
enum class TextureAspect {
    /**
     * Selects the color data, including every component present in the color format.
     *
     * An RGBA image has one color aspect, not separate red, green, blue, and alpha aspects.
     * This also covers non-color application data stored in color formats: a density field
     * stored in `R16Float` still uses this aspect.
     */
    Color,

    /**
     * Selects the depth values used to compare which surfaces are in front of others.
     *
     * Select this alone when a view should expose rendered depth to a depth-reading operation,
     * rather than expose both parts of a combined depth-stencil texture. The stencil values
     * remain in the source texture but are not selected by this view.
     */
    Depth,

    /**
     * Selects the integer stencil marks used to mask or classify image regions.
     *
     * For example, rendering can mark selected-object pixels with 1 and leave other pixels at 0;
     * a later stencil test can restrict drawing using those marks. A stencil-only image operation
     * selects this part of combined storage without selecting the depth values at the same positions.
     * The meanings of the integer marks are chosen by the application.
     */
    Stencil,
}