/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * A representation of shader code from which a [ShaderModule] can be created.
 *
 * Shader code may be provided as human-readable source or as a precompiled binary
 * representation. Implementations may still need to validate, translate, specialize, or compile
 * the supplied code before it can be used by a graphics pipeline.
 *
 * This type describes how the shader is represented, not where it was obtained. Code loaded from
 * a cache, resource file, generated at runtime, or received from another system uses the same
 * representation type.
 */
sealed interface ShaderCode {
    /**
     * Human-readable name used for diagnostics and debugging.
     *
     * Typical values include a resource path such as
     * `"mymod:shaders/bloom_downsample.frag"`, a generated variant name such as
     * `"Bloom downsample / high quality"`, or a cache-entry description.
     *
     * The label does not need to be unique and does not affect shader compilation or runtime
     * behavior.
     */
    val label: String
}

