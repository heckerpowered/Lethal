/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.binding

/**
 * Describes the numeric values a shader receives from formatted image or texel-buffer reads.
 *
 * Storage formats and shader values are different concepts. An 8-bit normalized color and a
 * 16-bit floating-point color can both be read as floating-point values, while an integer
 * identifier must be read through an integer interface. This lets a binding describe the
 * required numeric category without fixing one particular sampled image format.
 *
 * The selected aspect matters: depth is read as floating point, while stencil is read as an
 * unsigned integer. A combined depth-stencil format therefore cannot determine this category
 * without knowing which aspect is exposed. Filtering and comparison sampling require separate
 * compatibility checks; choosing Float does not guarantee that either operation is supported.
 */
enum class TextureSampleType {
    /** Floating-point results, including normalized colors and depth values. */
    Float,

    /** Signed integer results, without normalization into a floating-point range. */
    SignedInteger,

    /** Unsigned integer results, such as stencil marks and unsigned identifiers. */
    UnsignedInteger,
}
