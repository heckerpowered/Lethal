/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.opengl.shader.OpenGLShaderBindingKind
import heckerpowered.render.shader.ShaderStage
import java.nio.ByteBuffer
import kotlin.test.*

class OpenGLLegacyUniformTest {
    @Test
    fun ordinaryUniformsRetainCompleteLayoutsAndCanonicalReflection() {
        val source = """
            #version 450
            layout(set=3,binding=5,std140) uniform Values {
                layout(offset=0) float first;
                layout(offset=32) vec3 second;
                layout(row_major) mat4 matrix;
                float unused;
            } values;
            layout(location=0) out vec4 color;
            void main(){color=values.matrix*vec4(values.second,values.first);}
        """.trimIndent()
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(source, "layout.frag", ShaderStage.Fragment)
            val original = bytes(shader.spirV())
            val reflection = shaderInterfaceArtifact(shader).encode()
            val artifact = lowerToOpenGL(shader)
            val native = lowerOpenGLTarget(shader, 140)
            val plain = lowerOpenGLTarget(shader, 120)
            assertEquals(native, artifact.copy(plainUniformSource = null))
            assertEquals(native.bindings, plain.bindings)
            assertEquals(plain.source, artifact.plainUniformSource)
            assertContains(plain.source, "uniform rhi_block_3_5 rhi_uniform_3_5;")
            for (ordinal in 0..3) assertContains(plain.source, "rhi_block_3_5_member_$ordinal;")
            assertContains(plain.bindings.single().blockSignature, "32:3:1:0:false")
            assertContains(plain.bindings.single().blockSignature, "48:4:4:16:true")
            assertContentEquals(original, bytes(shader.spirV()))
            assertContentEquals(reflection, shaderInterfaceArtifact(shader).encode())
            assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
        }
    }

    @Test
    fun versionOneFixtureKeepsNativeSourceAndHasNoOrdinaryAlternative() {
        val bytes = javaClass.getResourceAsStream("/heckerpowered/render/compiler/opengl-uniform-v1.rhigl")!!.use { it.readBytes() }
        assertEquals(1, ByteBuffer.wrap(bytes).getInt(4))
        val artifact = OpenGLShaderArtifact.decode(ByteBuffer.wrap(bytes))
        assertEquals(140, artifact.glslVersion)
        assertNull(artifact.plainUniformSource)
        assertEquals(artifact.source, artifact.coreSource)
        assertEquals(listOf(0 to 1, 2 to 3), artifact.bindings.map { it.set to it.binding })
        assertTrue(artifact.bindings.all { it.kind == OpenGLShaderBindingKind.UniformBuffer })
        assertContains(artifact.bindings[1].blockSignature, "16:4:4:16:true")
        assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
        assertContentEquals(bytes, artifact.encode())
        val versionTwoWithoutAlternative = bytes + ByteArray(4)
        ByteBuffer.wrap(versionTwoWithoutAlternative).putInt(4, 2)
        assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(versionTwoWithoutAlternative)))
    }

    @Test
    fun versionTwoRejectsUnknownSchemasTruncationAndInvalidAlternatives() {
        val artifact = uniformArtifact()
        val encoded = artifact.encode()
        assertFailsWith<IllegalArgumentException> { OpenGLShaderArtifact.decode(ByteBuffer.wrap(encoded.copyOf(encoded.size - 1))) }
        assertFailsWith<IllegalArgumentException> { OpenGLShaderArtifact.decode(ByteBuffer.wrap(encoded + byteArrayOf(0))) }
        val unknown = encoded.clone()
        ByteBuffer.wrap(unknown).putInt(4, 3)
        assertFailsWith<IllegalArgumentException> { OpenGLShaderArtifact.decode(ByteBuffer.wrap(unknown)) }
        for (invalid in listOf(
            artifact.copy(plainUniformSource = "\u0000"),
            artifact.copy(plainUniformSource = " "),
            artifact.copy(glslVersion = 120),
            artifact.copy(bindings = emptyList()),
        )) assertFailsWith<IllegalArgumentException> { OpenGLShaderArtifact.decode(ByteBuffer.wrap(invalid.encode())) }
    }

    @Test
    fun ordinaryAlternativeDoesNotExpandSupportedUniformShapes() {
        GlslCompiler().use { compiler ->
            for ((type, expression) in listOf("int" to "vec4(value)", "vec4[2]" to "value[0]", "mat3" to "vec4(value[0],1)")) {
                val field = if (type == "vec4[2]") "vec4 value[2];" else "$type value;"
                val shader = compiler.compile("#version 450\nlayout(set=0,binding=0,std140)uniform Values{$field};layout(location=0)out vec4 color;void main(){color=$expression;}", "unsupported.frag", ShaderStage.Fragment)
                assertFailsWith<IllegalArgumentException> { lowerToOpenGL(shader) }
            }
        }
        val shader = "#version 450\nlayout(location=0)out vec4 color;void main(){color=vec4(1);}"
        GlslCompiler().use { compiler -> assertNull(lowerToOpenGL(compiler.compile(shader, "plain.frag", ShaderStage.Fragment)).plainUniformSource) }
    }

    private fun uniformArtifact(): OpenGLShaderArtifact = GlslCompiler().use { compiler ->
        lowerToOpenGL(compiler.compile("#version 450\nlayout(set=0,binding=0,std140)uniform Tint{vec4 value;}tint;layout(location=0)out vec4 color;void main(){color=tint.value;}", "codec.frag", ShaderStage.Fragment))
    }

    private fun bytes(value: ByteBuffer) = ByteArray(value.remaining()).also { value.duplicate().get(it) }
}
