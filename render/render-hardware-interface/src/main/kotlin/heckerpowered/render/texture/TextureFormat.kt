/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

/**
 * Identifies the exact formatted representation of a texture texel.
 *
 * Each entry fixes whether the texel contains color, depth, or stencil data, as well as its
 * component order, component bit widths, numeric interpretation, and any color transfer encoding
 * applied by formatted access. Formats are not freely assembled from independent properties, so
 * every [TextureFormat] value denotes one valid concrete format.
 *
 * Selecting a format requests these exact semantics. An implementation must report an unsupported
 * format or usage rather than silently substitute a format with different component widths,
 * numeric behavior, or color encoding.
 *
 * These guarantees describe the formatted texel representation, not the physical image layout.
 * Tiling, row pitch, alignment, padding, and total memory allocation remain device-dependent.
 *
 * ### Color component semantics
 *
 * Component names such as `R`, `G`, `B`, and `A` identify logical component slots rather than the
 * application-level meaning of the stored data. An `R` format may therefore store a height, mask,
 * luminance, or any other scalar value.
 *
 * When a color texel is exposed through a four-component interface, missing `G` and `B` components
 * are zero and a missing `A` component is one. For example, an `R` format is exposed as
 * `(r, 0, 0, 1)`, while an `Rg` format is exposed as `(r, g, 0, 1)`. When writing to a format with
 * fewer than four components, only the components present in the format are stored.
 *
 * ### Format capabilities
 *
 * The presence of a format in this enumeration does not guarantee that every graphics device
 * supports every use of it. Sampling, filtering, rendering, blending, storage access, transfers,
 * and multisampling may impose additional capability requirements.
 */
enum class TextureFormat(private val kind: Kind) {
    /**
     * One 8-bit unsigned-normalized red component.
     *
     * Stored integer values from `0` through `255` are exposed as floating-point values from `0.0`
     * through `1.0`.
     *
     * This format is commonly used for masks, monochrome data, coverage values, and other
     * single-component texture data.
     */
    R8UnsignedNormalized(Kind.Color),

    /**
     * Two 8-bit unsigned-normalized components in red-green order.
     *
     * Each component stores a value in the range `0.0` through `1.0`. This format is useful for
     * paired masks, two-component lookup data, or vectors encoded into an unsigned range.
     */
    Rg8UnsignedNormalized(Kind.Color),

    /**
     * Four 8-bit unsigned-normalized components in red-green-blue-alpha order.
     *
     * No sRGB transfer conversion is applied. This format is suitable for non-color data and for
     * color values that should remain in their stored numeric representation during ordinary
     * texture access.
     */
    Rgba8UnsignedNormalized(Kind.Color),

    /**
     * Four 8-bit components in red-green-blue-alpha order with sRGB-encoded color components.
     *
     * Red, green, and blue are stored using the nonlinear sRGB transfer encoding. Alpha remains an
     * ordinary unsigned-normalized value and is never sRGB encoded or decoded.
     *
     * Unlike [Rgba8UnsignedNormalized], this format distinguishes between its stored representation
     * and the linear color values used by rendering operations:
     *
     * - raw transfers operate on stored sRGB-encoded bytes;
     * - formatted color accesses operate on linear RGB values.
     *
     * ### Why use sRGB encoding?
     *
     * Lighting, interpolation, filtering, and blending should operate on linear colors. However,
     * storing linear colors directly in only eight bits provides relatively little precision in dark
     * ranges, where quantization is especially visible.
     *
     * sRGB encoding assigns more of the available byte values to dark and mid-range colors. It
     * therefore preserves visible color more effectively than 8-bit linear storage without increasing
     * the four-byte texel size. A higher-precision linear format such as `Rgba16Float` can store linear
     * color directly, but requires more storage and memory bandwidth.
     *
     * ### Writing this format
     *
     * | Write path | RHI conversion | Caller responsibility |
     * | --- | --- | --- |
     * | Host or buffer upload | None | Supply sRGB-encoded RGB bytes. Encode first if the source values are linear. |
     * | Raw texture copy | None | Ensure the copied representation is appropriate for the destination format. |
     * | Fragment-shader output | Linear RGB to sRGB | Output linear RGB; do not encode it manually. |
     * | Color-attachment clear | Linear RGB to sRGB | Supply a linear clear color. |
     * | Blended attachment write | Decode destination, blend linearly, then encode | Supply linear source RGB. |
     *
     * ### Reading this format
     *
     * | Read path | RHI conversion | Value observed by the caller |
     * | --- | --- | --- |
     * | Sampled shader access | sRGB to linear RGB | The shader receives linear RGB. |
     * | Raw texture readback | None | The caller receives stored sRGB-encoded bytes. |
     * | Raw texture copy | None | The encoded representation is copied unchanged. |
     *
     * A normal rendering path therefore keeps shader calculations linear automatically:
     *
     * ```
     * sRGB asset bytes
     *     -> raw upload
     *     -> stored sRGB representation
     *     -> automatic decoding during sampled access
     *     -> linear shader calculations and blending
     *     -> automatic encoding during attachment writes
     *     -> stored sRGB representation
     * ```
     *
     * Callers only need to convert values when crossing a raw-transfer boundary with values in the
     * wrong representation:
     *
     * - encode linear RGB before uploading it as raw data;
     * - decode readback bytes before using them in linear CPU calculations.
     *
     * Do not manually decode sampled values or encode fragment-shader outputs. Doing so would apply
     * the transfer function twice.
     *
     * For comparison, the stored byte value `128` has different shader-visible meanings:
     *
     * ```
     * Rgba8UnsignedNormalized:
     *     128 / 255 ~= 0.502 linear
     *
     * Rgba8UnsignedNormalizedSrgb:
     *     128 / 255 -> sRGB decoding -> approximately 0.216 linear
     * ```
     *
     * Use this format for sRGB-encoded visible color content such as base-color or albedo textures,
     * photographs, sprites, user-interface artwork, and display-oriented color attachments.
     *
     * Use [Rgba8UnsignedNormalized] for already-linear colors and non-color data such as normal maps,
     * roughness, metallic, ambient occlusion, masks, and lookup data.
     *
     * Automatic encoding and decoding do not make storage lossless. Values are still quantized to
     * eight bits whenever they are stored.
     */
    Rgba8UnsignedNormalizedSrgb(Kind.Color),

    /**
     * Four 8-bit unsigned-normalized components in blue-green-red-alpha storage order.
     *
     * Shader-visible color components retain their logical red-green-blue-alpha meanings; the
     * format name describes their texel storage and transfer order.
     *
     * This format is commonly encountered in presentation surfaces and externally supplied
     * textures, but it may also be used for ordinary textures when the graphics device supports the
     * requested usage.
     */
    Bgra8UnsignedNormalized(Kind.Color),

    /**
     * Four 8-bit components in blue-green-red-alpha storage order with sRGB-encoded color
     * components.
     *
     * Red, green, and blue use the nonlinear sRGB encoding, while alpha remains an ordinary
     * unsigned-normalized value. Its conversion behavior otherwise matches
     * [Rgba8UnsignedNormalizedSrgb].
     *
     * This format is particularly common for presentation surfaces whose native component order is
     * blue-green-red-alpha.
     */
    Bgra8UnsignedNormalizedSrgb(Kind.Color),

    /**
     * A single-channel format that stores one 16-bit floating-point red component per texel.
     *
     * Unlike an unsigned-normalized format, this format can represent negative values and values
     * greater than one. Its largest finite magnitude is `65,504`, but its precision is lower than
     * that of a 32-bit float and varies with the magnitude of the stored value. Values that cannot
     * be represented exactly are rounded when stored.
     *
     * Typical uses include HDR luminance, height fields, and scalar intermediate data that requires
     * more range than [R8UnsignedNormalized].
     *
     * The encoded texel size is 2 bytes.
     */
    R16Float(Kind.Color),

    /**
     * A two-channel format that stores 16-bit floating-point red and green components per texel.
     *
     * Both components have the same range and precision characteristics as [R16Float]. Typical uses
     * include motion vectors, two-dimensional simulation fields, and other signed vector data that
     * does not require four components.
     *
     * The encoded texel size is 4 bytes.
     */
    Rg16Float(Kind.Color),

    /**
     * A four-channel format that stores 16-bit floating-point red, green, blue, and alpha components
     * per texel.
     *
     * This format is commonly used for linear HDR colors and intermediate render results such as
     * lighting, bloom, and post-processing buffers. It preserves negative values and values greater
     * than one instead of restricting every component to the `[0, 1]` range.
     *
     * Compared with [Rgba8UnsignedNormalized], this format provides substantially greater range and
     * precision but uses twice as much storage and memory bandwidth. When used for color, its
     * components represent linear values; no sRGB transfer encoding is applied.
     *
     * The encoded texel size is 8 bytes.
     */
    Rgba16Float(Kind.Color),

    /**
     * A single-component format that stores one 32-bit floating-point `R` value per texel.
     *
     * Compared with [R16Float], this format provides substantially greater range and precision.
     * Finite values extend to approximately `3.4e38` in magnitude, with about seven significant
     * decimal digits of precision. It supports negative values and values greater than one, and
     * applies no sRGB color encoding.
     *
     * This format is intended for scalar data where rounding to [R16Float] would be numerically
     * significant, such as linear depth explicitly stored as color data, large-range height data,
     * or high-precision compute intermediates.
     *
     * Using 32-bit floating-point values in shader calculations does not by itself require this
     * storage format. Values stored in [R16Float] are converted to shader floating-point values when
     * read, so this format should be preferred only when the stored data itself requires 32-bit
     * range or precision.
     *
     * The encoded texel size is 4 bytes.
     */
    R32Float(Kind.Color),

    /**
     * A two-component format that stores 32-bit floating-point `R` and `G` values per texel.
     *
     * Both components have the same range and precision as [R32Float]. Compared with [Rg16Float],
     * this format provides substantially greater precision and range but uses twice as much storage
     * per texel. It applies no sRGB color encoding.
     *
     * Typical uses include high-precision two-dimensional vector fields, pairs of simulation values,
     * and complex-number data where rounding either component to 16-bit floating point would be
     * numerically significant.
     *
     * [Rg16Float] is usually sufficient for motion vectors and ordinary post-processing data.
     * This format should be preferred only when the stored values themselves require 32-bit range or
     * precision.
     *
     * The encoded texel size is 8 bytes.
     */
    Rg32Float(Kind.Color),

    /**
     * A four-component format that stores 32-bit floating-point `R`, `G`, `B`, and `A` values per
     * texel.
     *
     * Every component has the same range and precision as [R32Float]. When used for color, the
     * components represent linear values and no sRGB color encoding is applied.
     *
     * This format is intended for four-component data where repeated storage at 16-bit precision
     * would introduce unacceptable rounding error, such as high-precision simulation state,
     * numerical accumulation, or compute intermediates spanning a large dynamic range.
     *
     * Most real-time HDR rendering, including lighting, bloom, and post-processing buffers, should
     * prefer [Rgba16Float]. This format provides greater precision but doubles the storage required
     * by [Rgba16Float] and uses four times as much storage as [Rgba8UnsignedNormalized].
     *
     * The encoded texel size is 16 bytes.
     */
    Rgba32Float(Kind.Color),

    /**
     * Stores one 24-bit unsigned-normalized depth component per texel.
     *
     * The stored integer range is mapped uniformly to `[0.0, 1.0]`, providing 16,777,216 distinct
     * depth values. Values produced by depth clears and fragment depth writes are automatically
     * quantized to this representation; sampling the texture returns the corresponding normalized
     * floating-point value.
     *
     * This format is a conventional choice for ordinary scene depth buffers when 24-bit fixed-point
     * precision is sufficient. Compared with 16-bit depth, it greatly reduces visible depth
     * quantization without requiring the additional precision and range characteristics of a
     * floating-point format.
     *
     * Although its encoded depth component contains 24 bits, this does not guarantee a smaller
     * physical allocation or lower bandwidth than [Depth32Float], because a device may store it in a
     * wider aligned representation. Choose it primarily for its precision and format requirements,
     * not as a guaranteed memory optimization.
     *
     * The normalized representation is uniform in stored depth, but perspective projection generally
     * makes that precision nonuniform in view-space distance. Choosing sensible near and far clipping
     * planes therefore remains important. Prefer [Depth32Float] when a large depth range, reverse
     * depth, or depth-sensitive post-processing requires greater effective precision.
     *
     * This format contains only a depth aspect and no color or stencil components.
     */
    Depth24UnsignedNormalized(Kind.Depth),

    /**
     * Stores one 32-bit floating-point depth component per texel.
     *
     * Unlike an unsigned-normalized depth format, this format does not distribute its representable
     * values uniformly across the depth interval. Floating-point values become progressively denser
     * toward zero, while depth attachment writes are rounded to the nearest representable value.
     * Sampling the texture returns the stored floating-point depth value.
     *
     * This format is intended for rendering that benefits from greater effective depth precision. It
     * is especially well suited to reverse-depth projection, where distant geometry is mapped toward
     * zero. The increasing floating-point precision in that region counteracts the precision loss
     * normally caused by perspective projection and allows very large view-space depth ranges to be
     * represented more reliably.
     *
     * It is also useful when later passes sample depth to reconstruct positions, detect surface
     * discontinuities, or perform other depth-sensitive calculations, because reduced quantization
     * error makes those calculations more stable.
     *
     * This format does not by itself correct an unsuitable projection or poorly chosen clipping
     * planes. For a conventional scene depth buffer whose precision requirements are already met by
     * [Depth24UnsignedNormalized], the 24-bit format remains a sufficient and simpler requirement.
     * Neither format guarantees a particular physical allocation size or bandwidth cost.
     *
     * This format contains only a depth aspect and no color or stencil components.
     */
    Depth32Float(Kind.Depth);

    /** Whether this format represents ordinary color components. */
    val isColor: Boolean
        get() = kind == Kind.Color

    /** Whether this format contains a depth component. */
    val hasDepth: Boolean
        get() = kind == Kind.Depth

    private enum class Kind {
        Color,
        Depth
    }
}