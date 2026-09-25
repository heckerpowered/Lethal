/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

/**
 * Determines how a selected region of texture storage is addressed by its users.
 *
 * [TextureDimension] describes the spatial grid in storage: a line, an image, or a volume.
 * It does not say whether a user sees one image, an array of images, or six faces addressed by
 * direction. A view supplies that interpretation without copying or rearranging the texels.
 *
 * For example, one cube-compatible two-dimensional texture can contain six square array layers:
 *
 * ```
 * select layer 2          -> TwoDimensional      -> one picture, addressed by (u, v)
 * select layers 0..5      -> TwoDimensionalArray -> six pictures, addressed by (u, v, layer)
 * select layers 0..5      -> Cube                -> one environment, addressed by direction
 * ```
 *
 * All three views reference the same two-dimensional storage. This is why view dimensions are
 * separate from storage dimensions: the selected layers can participate in different access
 * patterns without changing the resource that holds them.
 *
 * [TwoDimensional] is the ordinary view of a picture. Array, cube, and volume views require an
 * explicit choice; neither six selected layers nor cube-compatible storage automatically selects
 * [Cube]. The view must match the texture's storage dimension and the interface that consumes it.
 *
 * The sample count remains a separate property of [GpuTexture]. Two-dimensional and
 * two-dimensional-array views may expose multisampled storage; choosing a view does not resolve it.
 */
enum class TextureViewDimension {
    /**
     * Exposes one one-dimensional image, addressed along a single axis.
     *
     * For example, a health-color ramp maps a health fraction to a color along a gradient.
     * This view selects one layer of one-dimensional storage. A one-row two-dimensional image
     * remains [TwoDimensional]; an axis of length one does not remove it from the image's type.
     */
    OneDimensional,

    /**
     * Exposes an array of independent one-dimensional images.
     *
     * A shader selects a curve or gradient by its array index, then a position along that image.
     * For example, one layer can contain a health-color ramp and another a temperature-color
     * ramp. Filtering interpolates along the selected ramp, not between different array layers.
     *
     * The selected layer count may be one. It still presents an array interface with layer zero,
     * rather than becoming [OneDimensional].
     */
    OneDimensionalArray,

    /**
     * Exposes one two-dimensional image, addressed along horizontal and vertical texture axes.
     *
     * This is the normal view for material pictures, scene color, or a depth image. It selects
     * exactly one array layer from two-dimensional storage, so it can also expose one image of
     * a material array or one face of a cube-compatible texture.
     *
     * A view may select several mip levels for texture access. Using it as a render attachment
     * requires a single selected mip level. Two-dimensional slices of volume storage require
     * a separate slice-selection contract and are not represented by this dimension here.
     */
    TwoDimensional,

    /**
     * Exposes several independent two-dimensional images through one array interface.
     *
     * For example, layers containing stone, wood, and grass can share one resource binding.
     * The shader chooses a layer and then a position within that picture. The layer coordinate
     * selects an image; it is not a third spatial axis interpolated like a volume coordinate.
     *
     * Each selected mip contains the same number of layers. A one-layer selection still has
     * an array interface. With cube-compatible storage, this form addresses the selected faces
     * as numbered pictures rather than selecting them by direction.
     */
    TwoDimensionalArray,

    /**
     * Interprets six square images as the faces of one environment addressed by direction.
     *
     * A sky lookup can use the viewing direction, while a reflection lookup can use the reflected
     * direction. Cube sampling selects a face and a position on that face, so the shader does not
     * have to choose a layer and calculate its two-dimensional coordinates explicitly.
     *
     * This selects exactly six consecutive layers of single-sampled, cube-compatible
     * two-dimensional storage. Relative to the first selected layer, their order is:
     *
     * ```
     * layer 0 -> +X    layer 1 -> -X
     * layer 2 -> +Y    layer 3 -> -Y
     * layer 4 -> +Z    layer 5 -> -Z
     * ```
     *
     * ### Face orientation
     *
     * A finite, nonzero direction `(x, y, z)` selects the face along its largest-magnitude
     * component. Let `major = max(abs(x), abs(y), abs(z))`. The following numerators determine
     * the normalized face coordinates: `u = (horizontal / major + 1) / 2` and
     * `v = (vertical / major + 1) / 2`.
     *
     * | Face | Horizontal numerator | Vertical numerator |
     * | --- | --- | --- |
     * | +X | -z | -y |
     * | -X | +z | -y |
     * | +Y | +x | +z |
     * | -Y | +x | -z |
     * | +Z | +x | -y |
     * | -Z | -x | -y |
     *
     * Face coordinates increase with the texel indices along that face. For a face of size
     * `size`, texel `(column, row)` is centered at `((column + 0.5) / size, (row + 0.5) / size)`.
     * For example, `(0, 0, 1)` selects the center of +Z, and `(1, 0, 2)` selects `(0.75, 0.5)`
     * on +Z. This convention fixes the relationship between face data and cube directions;
     * it does not choose the application's world axes or rotate uploaded images.
     *
     * At an exact tie between major components, face selection may use a backend-specific
     * deterministic rule. A zero direction has no defined cube lookup. Filtering and mip
     * selection are supplied separately by the sampler.
     */
    Cube,

    /**
     * Exposes an array of cube environments, each formed from six consecutive faces.
     *
     * This is useful for reflection probes: a shader selects a probe by array index and looks
     * up a direction within that probe. The probe index selects a whole cube, not one face.
     *
     * [GpuTextureView.arrayLayerCount] counts faces and must be a positive multiple of six.
     * Twelve selected layers therefore expose two cubes. Cube index `i` uses relative layers
     * `6 * i` through `6 * i + 5`, in the face order and orientation defined by [Cube].
     *
     * Cube indices start at zero within the view even when [GpuTextureView.baseArrayLayer] is
     * nonzero. Selecting six layers gives a one-element cube array, not the [Cube] interface.
     * Cube-array support is checked separately from the texture's basic cube compatibility.
     */
    CubeArray,

    /**
     * Exposes a volume addressed along three continuous spatial axes.
     *
     * A density field uses three coordinates to find a position inside smoke. A three-dimensional
     * color lookup table can instead use input red, green, and blue as its axes. With compatible
     * filtering, interpolation can involve neighboring positions along all three axes.
     *
     * Each selected mip includes its complete depth extent. Unlike a two-dimensional array,
     * that extent shrinks at lower mip levels. A 64-by-64-by-16 volume becomes 32-by-32-by-8
     * at the next level; sixteen independent pictures would remain sixteen pictures.
     *
     * The source must be three-dimensional. The array-layer range is fixed to base zero and
     * count one; it does not select z slices of the volume.
     */
    ThreeDimensional,
}
