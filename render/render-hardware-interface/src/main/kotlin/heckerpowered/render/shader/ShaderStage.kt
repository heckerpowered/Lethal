/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * Shader stage executed by a graphics pipeline.
 *
 * Each [ShaderModule] belongs to exactly one stage. This is intentionally a single value rather
 * than a bit mask; stage masks used for visibility or synchronization are separate concepts.
 */
enum class ShaderStage {
    /**
     * Processes vertex input and produces per-vertex outputs for subsequent pipeline stages.
     */
    Vertex,

    /**
     * Processes rasterized fragments and produces color, depth, or stencil results.
     */
    Fragment,
}
