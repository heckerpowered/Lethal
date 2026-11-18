/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.tonemapping.aces

import heckerpowered.render.engine.color.Chromaticities

/**
 * Target display peak and limiting RGB primaries for the ACES 2.0 output transform.
 *
 * The input and reach gamuts remain ACES AP0 and AP1. The generated output is display-linear RGB
 * relative to 100 nits; choosing a display encoding or presentation mode is a separate operation.
 * Construction only stores this configuration. Generating the transform rejects peaks outside
 * 1 through 10000 nits and chromaticities outside the transform's mathematical domain rather than
 * clamping them to a different display configuration.
 *
 * @property peakLuminanceNits Target peak luminance in cd/m² (nits), not a relative RGB multiplier.
 * @property limitingPrimaries Output RGB primaries and reference white, without a transfer function.
 */
data class Aces20Configuration(val peakLuminanceNits: Float = 100f, val limitingPrimaries: Chromaticities = Chromaticities.Rec709)