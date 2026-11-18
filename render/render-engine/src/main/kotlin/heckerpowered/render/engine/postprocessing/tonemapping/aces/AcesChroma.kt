/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.tonemapping.aces

import kotlin.math.max
import kotlin.math.pow

/**
 * Peak-dependent controls for JMh colorfulness, not RGB multipliers.
 * [saturation] and [saturationThreshold] shape low-colorfulness expansion;
 * [compression] shapes highlight compression. [scale] supplies the luminance factor
 * of the fitted AP1 cusp M used to normalize colorfulness before these operations.
 */
internal data class AcesChroma(
    val saturation: Float,
    val saturationThreshold: Float,
    val compression: Float,
    val scale: Float,
)

/**
 * Precomputes the display-dependent part of ACES 2.0 chroma compression.
 * [peak] is in cd/m²; [logPeak] must be `log10(peak / 100)` from the same tone scale.
 * The coefficients tune appearance and fit cusp geometry, rather than representing
 * universal physical constants. See the Academy's
 * [chroma model](https://docs.acescentral.com/system-components/output-transforms/technical-details/chroma-compression/)
 * and OCIO 2.6 `init_ChromaCompressParams`.
 */
internal fun chromaCompression(peak: Float, logPeak: Float): AcesChroma {
    // Expansion falls with log peak but retains a 0.2 floor. The threshold shrinks
    // with peak, changing the toe rather than imposing a fixed RGB cutoff.
    val saturation = max(.2f, 1.3f - (1.3f * .69f) * logPeak)
    val saturationThreshold = .5f / peak

    // Highlight compression grows per decade of peak above the 100-nit baseline.
    val compression = 2.4f + (2.4f * 3.3f) * logPeak

    // Luminance factor of the fitted AP1 cusp M curve; its hue factor is applied
    // separately in the shader. This factor alone is not a gamut boundary.
    val scale = (.03379f * peak).pow(.30596f) - .45135f

    return AcesChroma(saturation, saturationThreshold, compression, scale)
}
