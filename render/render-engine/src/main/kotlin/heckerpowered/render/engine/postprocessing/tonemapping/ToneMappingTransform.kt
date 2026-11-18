/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.postprocessing.tonemapping

/**
 * Selects the ACES output transform applied to exposed scene radiance.
 */
enum class ToneMappingTransform {
    /**
     * Reference RRT and sRGB ODT retained for comparisons with ACES 2.0.
     */
    Aces13,

    /**
     * Analytical ACES 2.0 Output Transform for a 100-nit Rec.709 D65 display.
     * Hue tables control chroma compression and gamut mapping; they are not a 3D color LUT
     * that limits the input radiance range. The array and texture variants hold the same FP32 values.
     *
     * [Aces20] generates matching hue data and shader parameters from its display configuration.
     * The runtime equations are derived from OpenColorIO 2.6.0; its BSD license is retained.
     */
    Aces20,
}

