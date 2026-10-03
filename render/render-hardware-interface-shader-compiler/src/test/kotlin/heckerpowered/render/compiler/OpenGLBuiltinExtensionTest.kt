/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.shader.ShaderStage
import java.nio.ByteBuffer
import kotlin.test.*

class OpenGLBuiltinExtensionTest {
    @Test
    fun legacyDrawBuiltinsRequireOnlyTheirActiveExtensions() {
        val cases = listOf(
            Triple("vertex", "float(gl_VertexIndex)", ""),
            Triple("instance", "float(gl_InstanceIndex)", ""),
            Triple("both", "float(gl_VertexIndex + gl_InstanceIndex)", ""),
            Triple("plain", "0.0", ""),
            Triple("unused", "0.0", "int unused() { return gl_VertexIndex + gl_InstanceIndex; }"),
        )
        GlslCompiler().use { compiler ->
            for ([name, position, unusedFunction] in cases) {
                val source = """
                    #version 450
                    $unusedFunction
                    void main() { gl_Position = vec4($position, 0, 0, 1); }
                """.trimIndent()
                val shader = compiler.compile(source, "$name.vert", ShaderStage.Vertex)
                val original = shader.spirV().bytes()
                val originalFacts = shaderInterfaceArtifact(shader).encode()
                val artifact = lowerToOpenGL(shader)
                val legacy = artifact.source
                val core = checkNotNull(artifact.coreSource)
                assertEquals(name in listOf("vertex", "both"), legacy.contains("#extension GL_EXT_gpu_shader4 : require"), name)
                assertEquals(name in listOf("instance", "both"), legacy.contains("#extension GL_ARB_draw_instanced : require"), name)
                assertEquals(name in listOf("instance", "both"), legacy.contains("#define gl_InstanceID gl_InstanceIDARB"), name)
                assertFalse(core.contains("#extension GL_EXT_gpu_shader4"), name)
                assertFalse(core.contains("#extension GL_ARB_draw_instanced"), name)
                assertFalse(core.contains("#define gl_InstanceID"), name)
                assertTrue(artifact.bindings.isEmpty(), name)
                assertTrue(artifact.pushConstants.isEmpty(), name)
                assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
                assertContentEquals(original, shader.spirV().bytes(), name)
                assertContentEquals(originalFacts, shaderInterfaceArtifact(shader).encode(), name)
            }
        }
    }

    private fun ByteBuffer.bytes(): ByteArray = ByteArray(remaining()).also { duplicate().get(it) }
}
