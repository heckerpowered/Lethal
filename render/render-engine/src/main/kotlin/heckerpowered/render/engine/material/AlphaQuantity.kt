/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.material

/**
 * Distinguishes opacity from a numerical effect contribution carried in the last image channel.
 *
 * Coverage participates in source-over and uses the declared RGB alpha representation. Signal
 * channels may exceed one and can be filtered or added numerically; their alpha is never opacity.
 * This declaration does not convert texels or prove their initialization.
 */
enum class AlphaQuantity {
    Coverage,
    Signal,
}
