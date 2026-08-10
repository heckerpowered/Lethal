/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.opengl.ShaderName
import heckerpowered.render.opengl.function.OpenGLShaderFunctions
import heckerpowered.render.shader.ShaderModule
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.terminateOnFailure

internal class OpenGLShaderModule(
    private val shaderFunctions: OpenGLShaderFunctions,
    internal val name: ShaderName,
    override val stage: ShaderStage,
    override val entryPoint: String,
) : ShaderModule {
    private var closed = false
    override fun close() {
        if (closed) return

        closed = true
        terminateOnFailure { shaderFunctions.deleteShader(name) }
    }
}