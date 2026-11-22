/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.tonemapping.aces

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Precomputed coefficients shared by the tone-scale shader and gamut construction.
 *
 * [n] and [nr] are display peak and reference luminance in cd/m². [g] controls contrast;
 * [t1] sets the shadow toe. [ct] is the grey calibration target divided by [nr].
 * [s2] uses the shader's luminance scale, whereas [m2] and [u2] are dimensionless.
 * [u2] normalizes the shoulder and [m2] is its pre-toe amplitude.
 *
 * [forwardLimit] bounds scene-linear RGB for gamut construction, not display luminance.
 * [inverseLimit] bounds normalized output luminance for inversion; [logPeak] is
 * log₁₀(n / nr), reused by chroma and gamut compression.
 */
internal data class AcesToneScale(
    val n: Float,
    val nr: Float,
    val g: Float,
    val t1: Float,
    val ct: Float,
    val s2: Float,
    val u2: Float,
    val m2: Float,
    val forwardLimit: Float,
    val inverseLimit: Float,
    val logPeak: Float,
)

/**
 * Prepares the ACES 2.0 shoulder and toe for a target display, without mapping pixels.
 *
 * The shader evaluates f = m2 × (Y / (Y + s2))ᵍ, then Yout = nr × f² / (f + t1).
 * Its input `Y` uses 100 units for scene-linear white, so [AcesToneScale.s2] includes
 * that scale. Other intermediate luminances are normalized by the 100-nit reference.
 *
 * Follows the Academy's [tone-scale initialization](https://github.com/aces-aswf/aces-core/blob/main/lib/Lib.Academy.Tonescale.ctl)
 * with the luminance-scaled `s2` used by [OpenColorIO 2.6](https://github.com/AcademySoftwareFoundation/OpenColorIO/blob/v2.6.0/src/OpenColorIO/ops/fixedfunction/ACES2/Transform.cpp).
 * The caller validates the display configuration; this function neither clamps nor validates it.
 *
 * @param peak Target display peak in cd/m², not an exposure multiplier.
 */
internal fun toneScale(peak: Float): AcesToneScale {
    val contrast = 1.15f
    val toe = .04f

    // Interpolate the scene-linear peak anchor in log display luminance: 128 at 100 nits,
    // 896 at 10000 nits. Values outside those anchors extrapolate rather than clamp.
    val peakSceneValue = 128f + (896f - 128f) * (ln(peak / 100f) / ln(10000f / 100f))
    val relativePeakLuminance = peak / 100f

    // Invert the toe h(f) = f² / (f + toe): solve f² - h*f - h*toe = 0
    // Its positive root is the pre-toe amplitude needed to reach relativePeakLuminance.
    val peakBeforeToe = .5f * (relativePeakLuminance + sqrt(relativePeakLuminance * (relativePeakLuminance + 4f * toe)))
    val initialShoulderNormalization = ((peakSceneValue / peakBeforeToe) / ((peakSceneValue / peakBeforeToe) + 1f)).pow(contrast)
    val initialShoulderAmplitude = peakBeforeToe / initialShoulderNormalization

    // Calibrate 18% greyBeforeToe from 10.013 nits, adding 14% of that base per
    // doubling of peak. Invert the toe and then the provisional shoulder to locate grey.
    val relativeGreyLuminance = 10.013f / 100f * (1f + ln(peak / 100f) / ln(2f) * .14f)
    val greyBeforeToe = .5f * (relativeGreyLuminance + sqrt(relativeGreyLuminance * (relativeGreyLuminance + 4f * toe)))
    val initialGreySceneValue = -(peakBeforeToe * (greyBeforeToe / initialShoulderAmplitude).pow(1f / contrast)) / ((greyBeforeToe / initialShoulderAmplitude).pow(1f / contrast) - 1f)
    val greyScale = .18f / initialGreySceneValue

    // greyScale shifts the horizontal scale toward the greyBeforeToe anchor. Convert it to Y units,
    // then normalize the final shoulder to reach peakBeforeToe at the peak anchor.
    val shoulderScale = greyScale * peakBeforeToe * 100f
    val shoulderNormalization = ((peakSceneValue / peakBeforeToe) / ((peakSceneValue / peakBeforeToe) + greyScale)).pow(contrast)
    return AcesToneScale(peak, 100f, contrast, toe, relativeGreyLuminance, shoulderScale, shoulderNormalization, peakBeforeToe / shoulderNormalization, 8f * peakSceneValue, peak / (shoulderNormalization * 100f), log10(peak / 100f))
}