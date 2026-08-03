/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * Format used by a [ShaderBinary].
 *
 * Binary formats describe shader-code representations rather than cache provenance. Backend-native
 * program or pipeline caches that contain linked pipeline state are separate from shader-module
 * binaries.
 */
enum class ShaderBinaryFormat {
    /**
     * Standard Portable Intermediate Representation for Vulkan.
     *
     * SPIR-V is an intermediate representation rather than guaranteed native GPU machine code.
     * A backend may still validate, specialize, translate, or compile it.
     */
    SpirV,
}