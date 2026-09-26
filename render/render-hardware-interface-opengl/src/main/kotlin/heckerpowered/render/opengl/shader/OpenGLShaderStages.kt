/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.opengl.OpenGLGraphicsDevice
import heckerpowered.render.opengl.ProgramName
import heckerpowered.render.shader.ShaderStages
import heckerpowered.render.terminateOnFailure

internal class OpenGLShaderStages(
    private val owner: OpenGLGraphicsDevice,
    private var program: ProgramName,
    modules: List<OpenGLShaderModule>,
    val label: String,
) : ShaderStages {
    private val modules = modules.toList()

    fun requireProgram(requester: OpenGLGraphicsDevice): ProgramName {
        require(requester === owner) { "Shader stages '$label' belong to another graphics device" }
        owner.checkAccess()
        check(program != ProgramName.None) { "Shader stages '$label' are closed" }
        // The public stage-combination contract borrows modules for its complete lifetime.
        modules.forEach { it.requireShader(owner) }
        return program
    }

    override fun close() = terminateOnFailure {
        if (program != ProgramName.None) {
            owner.checkAccess()
            owner.functions.deleteProgram(program)
            program = ProgramName.None
        }
    }
}
