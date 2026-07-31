/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

/**
 * Creates an [OpenGLFunctions] view of the context current on the calling thread.
 *
 * Implementations must not inspect OpenGL state from their constructors. Context
 * capability discovery begins only when [create] is called.
 */
interface OpenGLFunctionsProvider {
    /**
     * Selects entry points for the context current on the calling thread.
     *
     * @throws IllegalStateException if no context is current or the mandatory
     * OpenGL function baseline is incomplete.
     */
    fun create(): OpenGLFunctions
}
