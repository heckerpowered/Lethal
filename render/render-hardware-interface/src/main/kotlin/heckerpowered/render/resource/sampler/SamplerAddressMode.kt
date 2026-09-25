/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.sampler

/**
 * Determines how sampling obtains texels beyond an image edge.
 *
 * Normalized coordinates span an image axis from 0 to 1, but coordinates may extend outside
 * that interval. Filtering near an edge may also need neighboring texels beyond the image.
 * The address mode determines whether those accesses wrap around or continue using edge data.
 *
 * [SamplerDescription] selects a mode independently for each texture-coordinate axis.
 */
enum class SamplerAddressMode {
    /**
     * Repeats the image across successive unit intervals of the texture coordinate.
     *
     * For example, coordinates 0.25 and 1.25 refer to corresponding positions in successive
     * copies of the image, while -0.25 corresponds to 0.75. This allows a small texture to tile
     * across a larger surface without duplicating its stored texels.
     *
     * Linear filtering across the wrap boundary can combine texels from opposite edges of the
     * image. Those edges should match when a continuous visual pattern is intended.
     */
    Repeat,

    /**
     * Extends the outermost texels beyond the image edge.
     *
     * Accesses past the left or right edge continue using the nearest edge column; the same
     * rule applies along other axes. Filtering therefore does not wrap to the opposite side.
     *
     * This is useful for sampling a complete image or a post-processing target without mixing
     * pixels from opposite edges. It clamps to the image exposed by the view, not to an arbitrary
     * rectangular sprite region packed inside a texture atlas.
     */
    ClampToEdge,
}
