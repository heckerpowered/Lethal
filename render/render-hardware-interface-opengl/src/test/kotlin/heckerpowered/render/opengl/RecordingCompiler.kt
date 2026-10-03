/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.shader.CanonicalShaderCompiler
import heckerpowered.render.shader.ShaderCompilation
import heckerpowered.render.shader.ShaderLanguage
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.reflection.ShaderInterfaceDescription
import heckerpowered.render.terminateOnFailure

internal class RecordingCompiler : CanonicalShaderCompiler {
    var calls = 0
    var closes = 0
    var failure: IllegalArgumentException? = null
    var closeFailure: AssertionError? = null
    var description: ShaderModuleDescription? = null
    var origin: String? = null
    var includes: Map<String, String>? = null
    val result = ShaderCompilation(
        ShaderModuleDescription(
            ShaderStage.Vertex,
            ShaderSource(
                ShaderLanguage.Glsl,
                "#version 450\nvoid main() {}",
                "canonical text",
            ),
        ),
        ShaderInterfaceDescription(
            emptyList(),
            emptyList(),
            emptyList(),
        ),
    )

    override fun compile(description: ShaderModuleDescription, origin: String, includes: Map<String, String>): ShaderCompilation {
        calls++
        this.description = description
        this.origin = origin
        this.includes = includes
        failure?.let { throw it }
        return result
    }

    override fun close(): Unit = terminateOnFailure {
        closes += 1
        closeFailure?.let { throw it }
    }
}
