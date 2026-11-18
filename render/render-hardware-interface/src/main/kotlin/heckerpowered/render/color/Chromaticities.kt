/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.color

/**
 * Chromaticities of an RGB color space's three primaries and reference white.
 *
 * Together these define the relative linear RGB-to-XYZ transform when the primaries form an
 * invertible basis and the white point permits normalization. This value does not validate those
 * conditions, specify an absolute luminance, or select a transfer function such as the sRGB curve.
 * Two encodings can therefore share these chromaticities without sharing encoded RGB values.
 *
 * @property red Chromaticity of the red primary.
 * @property green Chromaticity of the green primary.
 * @property blue Chromaticity of the blue primary.
 * @property white Reference white used to normalize the primary contributions.
 */
data class Chromaticities(val red: Chromaticity, val green: Chromaticity, val blue: Chromaticity, val white: Chromaticity) {
    companion object {
        /** BT.709 primaries and D65 white, also used by sRGB; no encoding curve is implied. */
        val Rec709 = Chromaticities(Chromaticity(.64, .33), Chromaticity(.30, .60), Chromaticity(.15, .06), Chromaticity(.3127, .3290))
    }
}
