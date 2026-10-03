/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.shader.program

import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderStage

internal fun readClasspathShaderSource(path: String): String = checkNotNull(MeshShader::class.java.getResourceAsStream(path)) { "Missing shader classpath resource: $path" }
    .use { it.reader(Charsets.UTF_8).readText() }

internal fun shaderModules(vertexOrigin: String, fragmentOrigin: String, includes: Map<String, String> = emptyMap(), readSource: (String) -> ShaderSource): List<CanonicalShaderModule> =
    listOf(ShaderStage.Vertex to vertexOrigin, ShaderStage.Fragment to fragmentOrigin).map { [stage, origin] ->
        CanonicalShaderModule(ShaderModuleDescription(stage, readSource(origin)), origin, includes)
    }
