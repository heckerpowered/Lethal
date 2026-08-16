/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

/**
 * Identifies the exact storage format of texture texels.
 *
 * Each entry fixes the component order, component bit widths, numeric interpretation, and any
 * color encoding applied by texture operations. Formats are not freely assembled from independent
 * properties, so every representable [TextureFormat] corresponds to a valid concrete format.
 *
 * The presence of a format in this enumeration does not guarantee that every graphics device
 * supports every use of it. Sampling, filtering, rendering, blending, storage access, and
 * multisampling may impose additional capability requirements.
 */
enum class TextureFormat {
    /**
     * One 8-bit unsigned-normalized red component.
     *
     * Stored integer values from `0` through `255` are exposed as floating-point values from `0.0`
     * through `1.0`.
     *
     * This format is commonly used for masks, monochrome data, coverage values, and other
     * single-component texture data.
     */
    R8UnsignedNormalized,

    /**
     * Two 8-bit unsigned-normalized components in red-green order.
     *
     * Each component stores a value in the range `0.0` through `1.0`. This format is useful for
     * paired masks, two-component lookup data, or vectors encoded into an unsigned range.
     */
    Rg8UnsignedNormalized,

    /**
     * Four 8-bit unsigned-normalized components in red-green-blue-alpha order.
     *
     * No sRGB transfer conversion is applied. This format is suitable for non-color data and for
     * color values that should remain in their stored numeric representation during ordinary
     * texture access.
     */
    Rgba8UnsignedNormalized,

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
    Rgba8UnsignedNormalizedSrgb,

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
    Bgra8UnsignedNormalized,

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
    Bgra8UnsignedNormalizedSrgb,
}