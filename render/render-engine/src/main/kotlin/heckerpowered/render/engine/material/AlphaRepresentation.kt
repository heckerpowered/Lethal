/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.material

/**
 * Describes whether source RGB already includes the source alpha factor.
 *
 * Straight color stores RGB independently of alpha; premultiplied color stores `RGB * alpha`.
 * Shading and blending must agree on this representation to avoid multiplying alpha twice or
 * omitting it. Declaring a representation describes existing values; it does not convert them.
 */
enum class AlphaRepresentation {
    Straight,
    Premultiplied
}
