/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.tonemapping.aces

/** Packing of generated cusp J/M/inverse-gamma and reach M values into one RGBA32F row. */
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
     * Lookup shaders sample texel centers using `(index + 0.5) / 512.0`. Keep that divisor
     * in sync if the storage width changes; padding never participates in hue interpolation.
     */
    const val TEXTURE_WIDTH = 512
    const val BYTES_PER_TEXEL = 4 * Float.SIZE_BYTES
}
