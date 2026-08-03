/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * Source language used by a [ShaderSource].
 *
 * The presence of a language in this enumeration does not by itself guarantee that every graphics
 * backend supports it. Unsupported languages must be rejected during shader-module creation.
 */
enum class ShaderLanguage {
    /**
     * OpenGL Shading Language source code.
     */
    Glsl,
}