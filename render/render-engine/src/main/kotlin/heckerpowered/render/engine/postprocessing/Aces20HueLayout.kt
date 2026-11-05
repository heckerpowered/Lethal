/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing

/**
 * Shared byte and texel layout of the ACES 2.0 hue lookup data and its GPU texture.
 * `/assets/render-engine/aces/hue_lookup_rgba32f.bin` contains [TEXEL_COUNT] little-endian
 * RGBA32F texels: RGB stores cusp J, M and inverse upper gamma; A stores reach M.
 *
 * Cusp and reach use different hue grids. Packing their values at the same texel index
 * does not align those grids: the lookup shaders fetch exact values with nearest sampling
 * and interpolate each table independently. The constant-array shader variants hold the
 * same FP32 values without a texture binding. Keep this layout, the binary data and both
 * tone-mapping/visibility shader variants in sync when regenerating the tables.
 */
internal object Aces20HueLayout {
    const val TEXEL_COUNT = 363

    /**
     * Storage width of the single-row RGBA32F ACES hue lookup texture. Its first
     * [TEXEL_COUNT] texels hold the 363 table entries; the remaining 149 are zero padding,
     * so the extra storage does not increase the table's hue resolution.
     *
     * 512 is the smallest power of two that holds all entries. The OpenGL backend rejects
     * non-power-of-two dimensions when the context provides neither OpenGL 2.0 nor
     * ARB_texture_non_power_of_two. Using 512 by 1 satisfies that dimension check;
     * RGBA32F storage still requires the backend's separate float-texture capabilities.
     *
     * The tone-mapping and visibility lookup shaders address texel centers with
     * `(index + 0.5) / 512.0`. Keep their divisor in sync if this storage width changes.
     */
    const val TEXTURE_WIDTH = 512
    const val BYTES_PER_TEXEL = 4 * Float.SIZE_BYTES
}
