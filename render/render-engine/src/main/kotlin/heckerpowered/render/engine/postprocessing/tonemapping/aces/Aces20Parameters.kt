/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.tonemapping.aces

/**
 * Precomputed ACES 2.0 output-transform value, grouped by color appearance, tone, chroma and gamut.
 * Matrices and hue lookup data are immutable snapshots, so data copies can safely share them.
 *
 * Arithmetic follows the OCIO FP32 implementation; JVM transcendental functions and
 * matrix inversion need not reproduce native OCIO table bits exactly.
 */
internal data class Aces20Parameters(
    val inputAppearance: AcesAppearance,
    val limitingAppearance: AcesAppearance,
    val toneScale: AcesToneScale,
    val chroma: AcesChroma,
    val gamut: AcesGamut,
) {
    companion object {
        /**
         * Generates all model coefficients for one display target. AP0 is the input model;
         * AP1 defines reach, while the configured primaries define the limiting gamut.
         * Tone and chroma share the same log peak so their luminance-dependent fits agree.
         * Unsupported peaks and invalid mathematical domains are rejected, not remapped.
         */
        fun generate(configuration: Aces20Configuration): Aces20Parameters {
            val peak = configuration.peakLuminanceNits
            require(peak in 1f..10000f) { "ACES 2.0 peak luminance must be between 1 and 10000 nits" }

            val input = appearance(AcesAp0)
            val limit = appearance(configuration.limitingPrimaries)
            val reachAppearance = appearance(AcesAp1)
            val tone = toneScale(peak)
            require(tone.forwardLimit > 0f) { "Peak luminance produces a non-positive ACES scene exposure limit" }

            val gamut = gamutCompression(peak, input, limit, reachAppearance, tone)
            return Aces20Parameters(input, limit, tone, chromaCompression(peak, tone.logPeak), gamut)
        }
    }
}
