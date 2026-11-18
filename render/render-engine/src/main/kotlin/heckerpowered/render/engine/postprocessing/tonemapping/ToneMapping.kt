/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.postprocessing.tonemapping

/**
 * Chooses the exposure, ACES output transform and encoding for an isolated HDR effect.
 *
 * Input radiance is linear sRGB/Rec.709 with D65 white. Exposure scales that radiance before
 * the nonlinear output transform. Select [ToneMappingOutputEncoding.Linear] when the target
 * applies its own sRGB encoding, or [ToneMappingOutputEncoding.Srgb] to emit display code values.
 */
data class ToneMapping(
    val exposure: Float = 1F,
    val outputEncoding: ToneMappingOutputEncoding = ToneMappingOutputEncoding.Srgb,
    val transform: ToneMappingTransform = ToneMappingTransform.Aces20,
) {
    init {
        require(exposure.isFinite() && exposure > 0F)
    }
}
