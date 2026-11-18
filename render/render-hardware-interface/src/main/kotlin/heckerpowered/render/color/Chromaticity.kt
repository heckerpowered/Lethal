/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.color

/**
 * A CIE 1931 xy chromaticity: the proportions of X and Y in the total tristimulus value X + Y + Z.
 * The remaining proportion is z = 1 - x - y. Chromaticity describes color independently of its
 * luminance; reconstructing XYZ also requires a scale such as the Y luminance component.
 *
 * Values are stored without validation or clamping. Color-space definitions may use imaginary
 * primaries outside the physically realizable chromaticity region, including negative coordinates.
 * Consumers decide which coordinates are valid for their transforms.
 *
 * @property x X divided by X + Y + Z, not an RGB red component.
 * @property y Y divided by X + Y + Z, not the luminance Y itself.
 */
data class Chromaticity(val x: Double, val y: Double)
