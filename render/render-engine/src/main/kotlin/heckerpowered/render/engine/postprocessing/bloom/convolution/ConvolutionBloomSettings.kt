/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.bloom.convolution

/**
 * Controls the core/scatter decomposition and RGB redistribution of a bloom kernel.
 * The preparation follows the UE 5.8.2 contract and operates on the radiance supplied to convolution;
 * these settings do not apply exposure or choose a display encoding.
 *
 * The product intensity × convolutionIntensity × scatterDispersion controls the fraction of each
 * channel's kernel mass assigned to scatter. Preparation clamps the resulting scene/scatter fractions;
 * this product is not simply a final RGB multiplier.
 *
 * @property scale Kernel support relative to the source image's larger dimension, clamped to [0, 1]
 * during preparation. Resampled support is at least one pixel.
 * @property bufferScale Padding-support limit, also clamped to [0, 1]. A positive value limits support
 * to `min(scale, bufferScale)`; zero uses scale. Padding starts at half that support times source width,
 * before the extent's 4096-crossing restriction and power-of-two rounding. It does not resize the source.
 * @property intensity Overall scatter-allocation multiplier.
 * @property convolutionIntensity Additional convolution scatter-allocation multiplier.
 * @property scatterDispersion Additional scatter-allocation multiplier, not a spatial radius.
 * @property boostMinimum Luminance at which the source RGB boost begins, in the input radiance scale.
 * @property boostMaximum Cap on boosted luminance. Boost is bypassed when minimum is not below maximum.
 * @property boostMultiplier Slope above the minimum: boosted luminance is
 * min(boostMultiplier × (luminance − boostMinimum) + boostMinimum, boostMaximum).
 * RGB is scaled by the luminance ratio before convolution; values at or below the minimum are unchanged.
 */
data class ConvolutionBloomSettings(
    val scale: Double = 1.0,
    val bufferScale: Double = .133,
    val intensity: Double = .675,
    val convolutionIntensity: Double = 1.0,
    val scatterDispersion: Double = 1.0,
    val boostMinimum: Double = 7.0,
    val boostMaximum: Double = 15000.0,
    val boostMultiplier: Double = 15.0,
) {
    init {
        require(listOf(scale, bufferScale, intensity, convolutionIntensity, scatterDispersion, boostMinimum, boostMaximum, boostMultiplier).all { it.isFinite() && it.toFloat().isFinite() && it >= 0.0 })
        require((intensity * convolutionIntensity * scatterDispersion).isFinite())
    }
}
