/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.material

/**
 * Declares how existing RGBA values are interpreted at a pass boundary.
 * Association describes stored RGB, independently of whether alpha is coverage or a numeric
 * signal. This is a caller contract, not a conversion or a readback of pixels. A loaded coverage
 * declaration requires finite alpha in [0,1] and premultiplied RGB throughout the consumed region.
 * Discard still requires the caller to establish every subsequently consumed sample; this
 * declaration does not prove initialization, pixel coverage, or shader behavior.
 * It does not clamp HDR RGB to alpha or extend resource lifetime.
 */
data class ColorContent(
    val alphaQuantity: AlphaQuantity,
    val representation: AlphaRepresentation,
)
