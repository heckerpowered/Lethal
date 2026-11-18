/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.postprocessing.tonemapping.aces

import heckerpowered.render.engine.color.Chromaticities
import heckerpowered.render.engine.color.Chromaticity

/**
 * AP0 chromaticities for the scene-linear ACES2065-1 input to the output transform.
 * Coordinates are CIE xy, including the shared ACES white point (0.32168, 0.33767).
 * The blue primary's negative y is intentional; these are mathematical primaries,
 * not display primaries to be clamped into a physical display gamut.
 */
internal val AcesAp0 = Chromaticities(Chromaticity(.7347, .2653), Chromaticity(.0, 1.0), Chromaticity(.0001, -.0770), Chromaticity(.32168, .33767))

/**
 * AP1 chromaticities used by this output transform to construct its reach gamut.
 * Coordinates are CIE xy with the same ACES white point as [AcesAp0]. AP1 defines
 * scene-linear ACEScg, but here its role is to bound reachable colorfulness; it does
 * not replace the configured limiting-display primaries or specify a transfer function.
 */
internal val AcesAp1 = Chromaticities(Chromaticity(.713, .293), Chromaticity(.165, .830), Chromaticity(.128, .044), Chromaticity(.32168, .33767))
