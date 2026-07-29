/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

/**
 * OpenGL context capabilities required by the RHI backend.
 */
data class OpenGLContextCapabilities(
    val supportsOpenGL21: Boolean,
    val framebufferAPI: OpenGLFramebufferAPI?,
    val supportsFloatingPointRenderTargets: Boolean,
    val supportsUnclampedColorOutput: Boolean,
    val supportsUniformBuffers: Boolean,
)

/**
 * OpenGL entry-point family used for framebuffer operations.
 */
enum class OpenGLFramebufferAPI {
    Core,
    ARB,
    EXT,
}
