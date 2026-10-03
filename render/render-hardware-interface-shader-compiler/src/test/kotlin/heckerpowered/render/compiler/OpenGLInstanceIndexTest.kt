/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.shader.ShaderStage
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.test.*

class OpenGLInstanceIndexTest {
    @Test
    fun canonicalInstanceIndexUsesDrawLocalIdWithoutHiddenUniforms() {
        val source = """
            #version 450
            layout(location=0) in vec2 position;
            layout(location=0) out vec4 instanceColor;
            void main() {
                float ordinal = float(gl_InstanceIndex);
                gl_Position = vec4(position * .2 + vec2(-.75 + ordinal * .6, 0), 0, 1);
                instanceColor = vec4(ordinal / 2.0, 1.0 - ordinal / 2.0, 0, 1);
            }
        """.trimIndent()
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(source, "instance-index.vert", ShaderStage.Vertex)
            val original = shader.spirV().bytes()
            val originalFacts = shaderInterfaceArtifact(shader).encode()
            val originalReflection = shader.shaderInterface
            val core = lowerOpenGLTarget(shader, 140)
            val legacy = lowerOpenGLTarget(shader, 120)
            val artifact = lowerToOpenGL(shader)
            for (target in listOf(legacy, core)) {
                assertContains(target.source, "float(gl_InstanceID)")
                assertFalse(target.source.contains("SPIRV_Cross_BaseInstance"))
                assertFalse(target.source.contains("gl_BaseInstanceARB"))
                assertFalse(target.source.contains("uniform "))
                assertTrue(target.bindings.isEmpty())
                assertTrue(target.pushConstants.isEmpty())
                assertEquals(listOf(0), target.inputs.map { it.location })
                assertEquals(listOf(0), target.outputs.map { it.location })
            }
            assertEquals(legacy.source, artifact.source)
            assertEquals(core.source, artifact.coreSource)
            assertEquals(legacy, lowerOpenGLTarget(shader, 120))
            assertEquals(core, lowerOpenGLTarget(shader, 140))
            assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
            assertEquals(originalReflection, shader.shaderInterface)
            assertContentEquals(original, shader.spirV().bytes())
            assertContentEquals(originalFacts, shaderInterfaceArtifact(shader).encode())
        }
    }

    @Test
    fun builtinsRetainTheirExpectedArtifacts() {
        // Locked LWJGL 3.4.1 artifacts; fullscreen includes the required GLSL 1.20 VertexIndex extension.
        val expectedArtifacts = mapOf(
            "fullscreen.vert" to "5327dd194aa25cb12f10baf5cfafadb8bf8bb739f263adf4bd530cf1ce05d553",
            "copy.frag" to "2802c9a2eb901ea47aff73dc092d12e492c427a31b02b46325e5aabc8d991b1d",
            "tent.frag" to "8216bbab721d4edc36b6aa82bf4c0fde59415c63fe0fc03d7f5dcd5b35fbccc0",
            "brightness.frag" to "bfd4c74e53104f914dcee5e959a765332ba5227bb56d3063afe09432c9eacbc4",
        )
        GlslCompiler().use { compiler ->
            for ([name, expectedHash] in expectedArtifacts) {
                val resource = checkNotNull(javaClass.getResourceAsStream("/interface-fixtures/$name"))
                val source = resource.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val stage = if (name.endsWith(".vert")) ShaderStage.Vertex else ShaderStage.Fragment
                val shader = compiler.compile(source, name, stage)
                val original = shader.spirV().bytes()
                val originalFacts = shaderInterfaceArtifact(shader).encode()
                val artifact = lowerToOpenGL(shader)
                val encoded = artifact.encode()
                val actualHash = MessageDigest.getInstance("SHA-256").digest(encoded)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                assertEquals(expectedHash, actualHash, name)
                assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(encoded)))
                assertContentEquals(original, shader.spirV().bytes())
                assertContentEquals(originalFacts, shaderInterfaceArtifact(shader).encode())
            }
        }
    }

    private fun ByteBuffer.bytes(): ByteArray = ByteArray(remaining()).also { duplicate().get(it) }
}
