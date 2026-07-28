/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.ShaderAssetLoader
import heckerpowered.render.ShaderModule
import heckerpowered.render.ShaderModuleDescription
import heckerpowered.render.ShaderSourceLanguage
import heckerpowered.render.ShaderStage
import heckerpowered.render.resolve
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

internal class OpenGLShaderModule(description: ShaderModuleDescription, assetLoader: ShaderAssetLoader) : ShaderModule {
    override val stage = description.stage
    private var shaderIdentifier = compile(description, description.source.resolve(assetLoader))

    fun identifier(): Int {
        check(shaderIdentifier >= 0) { "Shader module has already been closed" }
        return shaderIdentifier
    }

    override fun close() {
        if (shaderIdentifier < 0) return
        GL20.glDeleteShader(shaderIdentifier)
        shaderIdentifier = -1
    }

    private fun compile(description: ShaderModuleDescription, sourceText: String): Int {
        require(description.source.language == ShaderSourceLanguage.GLSL) { "OpenGL only accepts GLSL shader source" }
        require(description.source.entryPoint == "main") { "OpenGL GLSL shader entry point must be main" }

        val identifier = GL20.glCreateShader(description.stage.openGLIdentifier)
        GL20.glShaderSource(identifier, sourceText)
        GL20.glCompileShader(identifier)
        if (GL20.glGetShaderi(identifier, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE) return identifier

        val compilationLog = GL20.glGetShaderInfoLog(identifier, MAXIMUM_LOG_LENGTH)
        GL20.glDeleteShader(identifier)
        error("Failed to compile ${description.label}: $compilationLog")
    }

    private companion object {
        const val MAXIMUM_LOG_LENGTH = 32768
    }
}

private val ShaderStage.openGLIdentifier: Int
    get() = when (this) {
        ShaderStage.Vertex -> GL20.GL_VERTEX_SHADER
        ShaderStage.Fragment -> GL20.GL_FRAGMENT_SHADER
    }
