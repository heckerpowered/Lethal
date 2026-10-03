/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.reflection.ShaderInterfaceArtifact
import org.lwjgl.util.spvc.Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.*

class GlslCompilerTest {
    @Test
    fun conditionalIncludesAndDefinesUseShadercPreprocessing() = sources { root ->
        root.resolve("common.glsl").writeText("vec4 position() { return vec4(1.0); }")
        val source = root.resolve("shader.vert")
        source.writeText("""
            #version 450
            #extension GL_GOOGLE_include_directive : require
            #if ENABLE_POSITION
            #include "common.glsl"
            #else
            #include "missing.glsl"
            #endif
            void main() { gl_Position = position(); }
        """.trimIndent())
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(root, source, ShaderStage.Vertex, mapOf("ENABLE_POSITION" to "1"))
            assertEquals(0x07230203, shader.spirV().int)
            assertFailsWith<IllegalStateException> { compiler.compile(root, source, ShaderStage.Vertex) }
        }
    }

    @Test
    fun nestedRelativeAndRootIncludesHaveDistinctSearchRoots() = sources { root ->
        val nested = Files.createDirectory(root.resolve("nested"))
        root.resolve("root.glsl").writeText("vec4 position() { return vec4(1.0); }")
        nested.resolve("local.glsl").writeText("#include <root.glsl>")
        val source = nested.resolve("shader.vert")
        source.writeText("#version 450\n#extension GL_GOOGLE_include_directive : require\n#include \"local.glsl\"\nvoid main() { gl_Position = position(); }")
        GlslCompiler().use { assertEquals(0x07230203, it.compile(root, source, ShaderStage.Vertex).spirV().int) }
    }

    @Test
    fun includesCannotEscapeRootAndRecursiveIncludesFail() = sources { root ->
        val source = root.resolve("shader.vert")
        val outside = root.parent.resolve("outside.glsl")
        outside.writeText("vec4 position() { return vec4(1.0); }")
        try {
            source.writeText("#version 450\n#extension GL_GOOGLE_include_directive : require\n#include \"../outside.glsl\"\nvoid main() { gl_Position = position(); }")
            GlslCompiler().use { compiler ->
                val failure = assertFailsWith<IllegalStateException> { compiler.compile(root, source, ShaderStage.Vertex) }
                assertContains(failure.message.orEmpty(), "outside the source root")
                root.resolve("cycle.glsl").writeText("#include \"cycle.glsl\"")
                source.writeText("#version 450\n#extension GL_GOOGLE_include_directive : require\n#include \"cycle.glsl\"\nvoid main() { gl_Position = vec4(0.0); }")
                assertFailsWith<IllegalStateException> { compiler.compile(root, source, ShaderStage.Vertex) }
            }
        } finally { Files.deleteIfExists(outside) }
    }

    @Test
    fun openGLLoweringPreservesVulkanBinaryAndRawLayout() = sources { root ->
        val source = root.resolve("shader.vert")
        source.writeText("""
            #version 450
            layout(location = 2) in vec3 position;
            layout(set = 1, binding = 3, std140) uniform Transform { mat4 matrix; } transform;
            layout(push_constant) uniform Params { vec4 offset; } params;
            void main() { gl_Position = transform.matrix * vec4(position, 1.0) + params.offset; }
        """.trimIndent())
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(root, source, ShaderStage.Vertex)
            val original = shader.spirV().bytes()
            assertEquals(2, shader.shaderInterface.inputs.single().location)
            val uniform = shader.shaderInterface.resources.single { it.kind == SPVC_RESOURCE_TYPE_UNIFORM_BUFFER }
            assertEquals(1, uniform.set)
            assertEquals(3, uniform.binding)
            assertEquals(64L, uniform.sizeBytes)
            assertEquals(16, uniform.members.single().matrixStrideBytes)
            val artifact = lowerToOpenGL(shader)
            assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
            assertEquals(1, artifact.bindings.single().set)
            assertEquals(3, artifact.bindings.single().binding)
            assertEquals(0, artifact.pushConstants.single().offsetBytes)
            assertContains(artifact.source, "gl_Position.y = -gl_Position.y")
            assertContains(artifact.source, "gl_Position.z")
            assertContentEquals(original, shader.spirV().bytes())
            assertTrue(shader.spirV().isReadOnly)
            assertEquals(ByteOrder.LITTLE_ENDIAN, shader.spirV().order())
        }
    }

    @Test
    fun targetConversionOrderCannotContaminateCoreFragmentOutput() = sources { root ->
        val source = root.resolve("shader.frag")
        source.writeText("""
            #version 450
            layout(location = 2) in vec2 uv;
            layout(location = 0) out vec4 color;
            layout(set = 1, binding = 4) uniform sampler2D image;
            layout(push_constant) uniform Fill { vec4 tint; } fill;
            void main() { color = texture(image, uv) * fill.tint; }
        """.trimIndent())
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(root, source, ShaderStage.Fragment)
            val original = shader.spirV().bytes()
            val direct140 = lowerOpenGLTarget(shader, 140)
            val forward120 = lowerOpenGLTarget(shader, 120)
            val forward140 = lowerOpenGLTarget(shader, 140)
            val reverse140 = lowerOpenGLTarget(shader, 140)
            val reverse120 = lowerOpenGLTarget(shader, 120)
            assertEquals(direct140, forward140)
            assertEquals(direct140, reverse140)
            assertEquals(forward120, reverse120)
            val artifact = lowerToOpenGL(shader)
            assertEquals(forward120.source, artifact.source)
            assertEquals(direct140.source, artifact.coreSource)
            assertEquals(forward120.inputs, direct140.inputs)
            assertEquals(forward120.outputs, direct140.outputs)
            assertEquals(forward120.bindings, direct140.bindings)
            assertEquals(forward120.pushConstants, direct140.pushConstants)
            val outputName = artifact.outputs.single().name
            assertTrue(Regex("(?m)^#version 140$").containsMatchIn(artifact.coreSource))
            assertTrue(Regex("\\bout\\s+vec4\\s+${Regex.escape(outputName)}\\s*;").containsMatchIn(artifact.coreSource))
            assertTrue(Regex("\\b${Regex.escape(outputName)}\\s*=").containsMatchIn(artifact.coreSource))
            assertFalse(Regex("\\b(gl_FragData|gl_FragColor|varying|attribute)\\b").containsMatchIn(artifact.coreSource))
            assertTrue(Regex("gl_Frag(Data\\[0\\]|Color)\\s*=").containsMatchIn(artifact.source))
            assertContains(artifact.coreSource, artifact.bindings.single().name)
            assertContains(artifact.coreSource, artifact.pushConstants.single().name)
            assertContentEquals(original, shader.spirV().bytes())
        }
    }

    @Test
    fun bothTargetsShareVertexMetadataAndKeepInstanceAndPushInputs() {
        val text = """
            #version 450
            layout(location=2) in vec3 position;
            layout(location=3) out vec2 coordinates;
            layout(push_constant) uniform Placement { vec4 offset; } placement;
            void main() {
                coordinates = position.xy;
                gl_Position = vec4(position + vec3(gl_InstanceIndex), 1.0) + placement.offset;
            }
        """.trimIndent()
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(text, "instance-metadata.vert", ShaderStage.Vertex)
            val canonical = shader.spirV().bytes()
            val legacy = lowerOpenGLTarget(shader, 120)
            val core = lowerOpenGLTarget(shader, 140)
            val artifact = lowerToOpenGL(shader)
            assertEquals(legacy.inputs, core.inputs)
            assertEquals(legacy.outputs, core.outputs)
            assertEquals(legacy.bindings, core.bindings)
            assertEquals(legacy.pushConstants, core.pushConstants)
            assertEquals(legacy.copy(coreSource = core.source), artifact)
            assertEquals(listOf(2), artifact.inputs.map { it.location })
            assertEquals(listOf(3), artifact.outputs.map { it.location })
            assertEquals(1, artifact.pushConstants.size)
            assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
            assertContentEquals(canonical, shader.spirV().bytes())
        }
    }

    @Test
    fun reflectedArrayStrideDistinguishesStd140AndStd430() = sources { root ->
        val source = root.resolve("shader.frag")
        GlslCompiler().use { compiler ->
            fun layout(standard: String): ShaderInterface {
                source.writeText("""
                    #version 450
                    layout(set=2, binding=5, $standard) readonly buffer Data {
                        vec2 values[2];
                        layout(offset=32) vec4 marker;
                    } data;
                    layout(location=0) out vec4 color;
                    void main() { color = vec4(data.values[1] + data.marker.xy, 0, 1); }
                """.trimIndent())
                return compiler.compile(root, source, ShaderStage.Fragment).shaderInterface
            }
            val std140 = layout("std140")
            val std430 = layout("std430")
            assertEquals(16, std140.resources.single().members.first().arrayStrideBytes)
            assertEquals(8, std430.resources.single().members.first().arrayStrideBytes)
            assertNotEquals(std140, std430)
        }
    }

    @Test
    fun nestedAndMultidimensionalBlockLayoutsAreExplicitlyRejected() = sources { root ->
        val source = root.resolve("shader.frag")
        GlslCompiler().use { compiler ->
            for (declaration in listOf("Inner value;", "vec4 value[2][2];")) {
                val access = if (declaration.startsWith("Inner")) "data.value.color" else "data.value[0][0]"
                source.writeText("""
                    #version 450
                    struct Inner { vec4 color; };
                    layout(set=0, binding=0, std430) readonly buffer Data { $declaration } data;
                    layout(location=0) out vec4 color;
                    void main() { color = $access; }
                """.trimIndent())
                val failure = assertFailsWith<IllegalArgumentException> { compiler.compile(root, source, ShaderStage.Fragment) }
                assertContains(failure.message.orEmpty(), "Nested structs and multidimensional")
            }
        }
    }

    @Test
    fun outputRootAliasesCannotWriteIntoSourceTree() = sources { root ->
        val alias = root.parent.resolve("output-link")
        Files.createSymbolicLink(alias, root)
        for (output in listOf(alias, alias.resolve("missing/child"))) {
            val failure = assertFailsWith<IllegalArgumentException> { main(arrayOf(root.toString(), output.toString())) }
            assertContains(failure.message.orEmpty(), "outside the shader source tree")
        }
        Files.list(root).use { assertEquals(0L, it.count()) }
    }

    @Test
    fun unresolvedParentTraversalCannotExposeSourceAlias() = sources { root ->
        root.resolve("shader.frag").writeText("#version 450\nlayout(location = 0) out vec4 color;\nvoid main() { color = vec4(1.0); }")
        val alias = root.parent.resolve("output-link")
        Files.createSymbolicLink(alias, root)
        val output = root.parent.resolve("missing/../output-link")
        val failure = assertFailsWith<IllegalArgumentException> { main(arrayOf(root.toString(), output.toString())) }
        assertContains(failure.message.orEmpty(), "must not contain parent traversal")
        assertFalse(Files.exists(root.parent.resolve("missing")))
        Files.list(root).use { assertEquals(1L, it.count()) }
    }

    @Test
    fun sourceRootAliasAndMissingBuildOutputStillCompile() = sources { root ->
        root.resolve("shader.frag").writeText("#version 450\nlayout(location = 0) out vec4 color;\nvoid main() { color = vec4(1.0); }")
        val alias = root.parent.resolve("input-link")
        Files.createSymbolicLink(alias, root)
        val output = root.parent.resolve("build/generated/shaders")
        main(arrayOf(alias.toString(), output.toString()))
        assertTrue(Files.isRegularFile(output.resolve("shader.frag.rhigl")))
        assertTrue(Files.isRegularFile(output.resolve("shader.frag.spv")))
        Files.list(root).use { assertEquals(1L, it.count()) }
    }

    @Test
    fun commandLineEmitsThreeArtifactsForTheSameShader() = sources { root ->
        root.resolve("shader.frag").writeText("#version 450\nlayout(location = 0) out vec4 color;\nvoid main() { color = vec4(1.0); }")
        val output = Files.createTempDirectory("rhi-shader-output")
        try {
            main(arrayOf(root.toString(), output.toString()))
            val artifact = OpenGLShaderArtifact.decode(ByteBuffer.wrap(Files.readAllBytes(output.resolve("shader.frag.rhigl"))))
            assertEquals(ShaderStage.Fragment, artifact.stage)
            val code = ByteBuffer.wrap(Files.readAllBytes(output.resolve("shader.frag.spv"))).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0x07230203, code.getInt(0))
            val facts = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(Files.readAllBytes(output.resolve("shader.frag.rhif"))))
            assertEquals(ShaderStage.Fragment, facts.stage)
            assertTrue(facts.matchesSpirV(code))
            assertEquals("color", facts.description.outputs.single().name)
            Files.list(output).use { assertEquals(3L, it.count()) }
        } finally { output.toFile().deleteRecursively() }
    }
}

private fun ByteBuffer.bytes(): ByteArray = ByteArray(remaining()).also { duplicate().get(it) }
private fun sources(action: (Path) -> Unit) {
    val directory = Files.createTempDirectory("rhi-shader-source")
    val root = Files.createDirectory(directory.resolve("sources"))
    try { action(root) } finally { directory.toFile().deleteRecursively() }
}
