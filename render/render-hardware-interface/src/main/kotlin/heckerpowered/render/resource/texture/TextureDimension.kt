/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

/**
 * Describes whether texels form a line, a two-dimensional image, or a three-dimensional volume.
 *
 * Most textures are ordinary pictures: a position has a horizontal and a vertical coordinate.
 * [TwoDimensional] expresses this familiar case and is the default in [TextureDescription].
 * [OneDimensional] instead describes a lookup along one axis, while [ThreeDimensional] gives
 * positions throughout a volume their own values.
 *
 * This is the shape of the texel grid, not the number of components stored at each position.
 * A one-dimensional gradient may store full RGBA colors, and a three-dimensional density field
 * may store just one floating-point value at each position.
 *
 * Array layers are separate images with the same shape. They do not add a spatial dimension:
 * sixteen two-dimensional pictures are an array, not a three-dimensional volume.
 */
enum class TextureDimension {
    /**
     * Arranges texels along a single axis, addressed by one position within each array layer.
     *
     * For example, a health-color lookup can store a gradient along 256 texels:
     *
     * ```
     * low health                                  full health
     *     red -> orange -> yellow -> yellow-green -> green
     * ```
     *
     * The health fraction selects a position along that gradient. Changing the stored colors
     * changes the mapping without changing the shader. Other uses include temperature-color
     * tables and precomputed functions of one variable.
     *
     * Width is the number of texels in the sequence. Height and depth are both one. A
     * two-dimensional texture that happens to have only one row remains two-dimensional;
     * dimensions are not inferred from which sizes exceed one.
     */
    OneDimensional,

    /**
     * Arranges texels in rows and columns, addressed by horizontal and vertical positions.
     *
     * This is the normal choice for material images, sprites, scene color, shadow maps, and
     * post-processing images. A brick texture used on a three-dimensional object is still a
     * two-dimensional picture; the surface provides the position to look up within it.
     *
     * For example, a 16-by-16 material texture has 16 columns and 16 rows in each array layer.
     * An array can hold several such images, with a separate layer index choosing between them.
     * Ordinary texture filtering does not blend across those array layers.
     *
     * Depth is one. A texture containing depth-test values is also commonly two-dimensional:
     * the format describes what is stored, while this dimension describes where it is stored.
     * Cube views interpret suitable groups of six square two-dimensional layers as faces,
     * rather than treating them as a three-dimensional volume.
     */
    TwoDimensional,

    /**
     * Arranges texels throughout a volume, addressed by three spatial coordinates.
     *
     * For example, a 64-by-64-by-32 smoke field can store density throughout a region. Two
     * positions with the same horizontal and vertical coordinates may still have different
     * densities because they lie at different depths within the volume.
     *
     * A three-dimensional color lookup table is another use: the input red, green, and blue
     * components select a position, whose stored value is the transformed color. The axes
     * therefore need not correspond to positions in the game world.
     *
     * The third axis is part of the image, not an index choosing an independent picture.
     * When the format and filtering mode support it, sampling can interpolate along this axis
     * as it does along the other two. Mip levels reduce all three spatial sizes:
     *
     * ```
     * volume:    64 x 64 x 32 -> 32 x 32 x 16 -> 16 x 16 x 8
     * 2D array:  32 images of 64 x 64 -> 32 images of 32 x 32
     * ```
     *
     * A volume has one array layer in this texture model. Its depth slices are not array layers
     * and must not be selected through an array-layer range.
     */
    ThreeDimensional,
}
