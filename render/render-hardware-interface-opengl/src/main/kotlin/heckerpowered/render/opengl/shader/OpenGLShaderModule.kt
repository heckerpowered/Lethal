/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.opengl.OpenGLGraphicsDevice
import heckerpowered.render.opengl.ShaderName
import heckerpowered.render.opengl.function.deleteShader
import heckerpowered.render.shader.ShaderBinary
import heckerpowered.render.shader.ShaderModule
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.terminateOnFailure

internal class OpenGLShaderModule(
    private val owner: OpenGLGraphicsDevice,
    private var name: ShaderName,
    description: ShaderModuleDescription,
) : ShaderModule {
    override val stage = description.stage
    override val entryPoint = description.entryPoint
    val label = description.label
    internal val isSpirV = description.code is ShaderBinary

    context(requester: OpenGLGraphicsDevice)
    fun requireShader(): ShaderName {
        require(requester === owner) { "Shader module '$label' belongs to another graphics device" }
        owner.checkAccess()
        check(name != ShaderName.None) { "Shader module '$label' is closed" }
        return name
    }

    override fun close() = terminateOnFailure {
        if (name == ShaderName.None) return@terminateOnFailure
        context(owner.functions) {
            owner.checkAccess()
            deleteShader(name)
            name = ShaderName.None
        }
    }
}
