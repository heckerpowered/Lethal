/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.postprocessing.tonemapping

/**
 * Selects whether the output is linear display light or sRGB-encoded display values.
 */
enum class ToneMappingOutputEncoding {
    Linear,
    Srgb,
}
