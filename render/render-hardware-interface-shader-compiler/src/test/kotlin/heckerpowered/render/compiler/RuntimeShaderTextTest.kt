/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.opengl.shader.OpenGLPushConstantMember
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.ShaderInterfaceResourceKind
import java.nio.ByteBuffer
import java.util.Collections
import kotlin.test.*

class RuntimeShaderTextTest {
    @Test
    fun partialPushNormalizationRejectsAmbiguousOrUnexpectedGeneratedStructures() {
        val name = "rhi_push_vertex"
        val members = listOf(
            OpenGLPushConstantMember(0, 4, 4, 16, false, "member_0"),
            OpenGLPushConstantMember(64, 4, 1, 0, false, "member_1"),
        )
        val source = "struct ${name}_type { mat4 member_0; vec4 member_1; }; uniform ${name}_type $name; void main(){gl_Position=$name.member_0*vec4(1);}"
        val retained = pruneInactivePushDeclarations(source, name, members, setOf(0))
        assertContains(retained, "mat4 member_0;")
        assertFalse(retained.contains("member_1"))
        val invalid = listOf(
            source.replace("vec4 member_1;", "mat4 member_1;"),
            source.replace("vec4 member_1;", "vec4 member_1[2];"),
            source.replace("vec4 member_1;", "vec4 member_1; float unknown;"),
            source.replace("vec4 member_1;", "struct Nested { vec4 value; } member_1;"),
            source.replace("member_0; vec4 member_1;", "member_1; vec4 member_0;"),
            "$source ${name}_type helper(${name}_type value){return value;}",
            "$source ${name}_type copy = $name;",
            "$source bool all(){return $name == $name;}",
            "$source void helper(){consume($name);}",
            "$source void helper(){existing = $name;}",
            "$source void helper(){vec4 color=$name.member_1;}",
            "$source void helper(){vec4 color=$name /* gap */ .member_1;}",
            "$source void helper(){vec4 color=$name. /* gap */ member_1;}",
            "$source void helper(){vec4 color=$name.unknown;}",
            "$source struct ${name}_type { mat4 member_0; vec4 member_1; };",
            source.replace("uniform ${name}_type $name;", "uniform ${name}_type unexpected;"),
        )
        for (generated in invalid) {
            assertFailsWith<IllegalArgumentException> { pruneInactivePushDeclarations(generated, name, members, setOf(0)) }
        }
        assertFailsWith<IllegalArgumentException> { pruneInactivePushDeclarations(source, name, members, emptySet()) }
        assertFailsWith<IllegalArgumentException> { pruneInactivePushDeclarations(source, name, members, setOf(2)) }
    }

    @Test
    fun surfacePushDeclarationsMatchActiveStageBytesWithoutChangingCanonicalFacts() {
        val declaration = "layout(push_constant) uniform Surface { mat4 clipFromLocal; vec4 color; } surface;"
        val vertex = "#version 450\nlayout(location=0) in vec3 position;\n$declaration\nvoid main(){gl_Position=surface.clipFromLocal*vec4(position,1);}"
        val fragment = "#version 450\nlayout(location=0) out vec4 result;\n$declaration\nvoid main(){result=surface.color;}"
        GlslCompiler().use { compiler ->
            for ([stage, source, activeIndex] in listOf(Triple(ShaderStage.Vertex, vertex, 0), Triple(ShaderStage.Fragment, fragment, 1))) {
                val shader = compiler.compile(source, "surface/${stage.name}.glsl", stage)
                val originalBinary = shader.spirV().let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }
                val originalFacts = shaderInterfaceArtifact(shader).encode()
                val canonicalPush = shaderInterfaceArtifact(shader).description.resources.single { it.kind == ShaderInterfaceResourceKind.PushConstant }
                assertEquals(80L, canonicalPush.sizeBytes)
                assertEquals(listOf("clipFromLocal", "color"), canonicalPush.members.map { it.name })
                assertEquals(listOf(0, 64), canonicalPush.members.map { it.offsetBytes })
                val directCore = lowerOpenGLTarget(shader, 140)
                val legacy = lowerOpenGLTarget(shader, 120)
                assertEquals(directCore, lowerOpenGLTarget(shader, 140))
                val artifact = lowerToOpenGL(shader)
                assertEquals(legacy.source, artifact.source)
                assertEquals(directCore.source, artifact.coreSource)
                val member = artifact.pushConstants.single()
                assertEquals("rhi_push_${stage.name.lowercase()}.member_$activeIndex", member.name)
                assertEquals(if (activeIndex == 0) 0 else 64, member.offsetBytes)
                assertEquals(if (activeIndex == 0) 4 else 1, member.columns)
                for (generated in listOf(artifact.source, artifact.coreSource)) {
                    assertContains(generated, if (activeIndex == 0) "mat4 member_0;" else "vec4 member_1;")
                    assertFalse(generated.contains("member_${1 - activeIndex}"))
                }
                assertContentEquals(originalBinary, shader.spirV().let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } })
                assertContentEquals(originalFacts, shaderInterfaceArtifact(shader).encode())
                assertEquals(artifact, OpenGLShaderArtifact.decode(ByteBuffer.wrap(artifact.encode())))
            }
        }
    }

    @Test
    fun fullyActivePushBlocksRetainEverySupportedFieldDeclaration() {
        val source = """
            #version 450
            layout(push_constant) uniform Params { float scale; vec2 offset; vec3 normal; vec4 color; mat4 transform; } params;
            void main() { gl_Position = params.transform * vec4(params.normal.xy + params.offset, params.normal.z, params.scale) + params.color; }
        """.trimIndent()
        GlslCompiler().use { compiler ->
            val artifact = lowerToOpenGL(compiler.compile(source, "all-active.vert", ShaderStage.Vertex))
            assertEquals(5, artifact.pushConstants.size)
            for (generated in listOf(artifact.source, artifact.coreSource)) {
                for ([index, type] in listOf("float", "vec2", "vec3", "vec4", "mat4").withIndex()) {
                    assertContains(generated, "$type member_$index;")
                }
            }
        }
    }

    @Test
    fun runtimeTextAndSnapshotIncludesProducePairedCodeAndFacts() {
        val source = ShaderSource(ShaderLanguage.Glsl, """
            #version 450
            #extension GL_GOOGLE_include_directive : require
            #include "nested/layout.glsl"
            #include <helpers.glsl>
            layout(set=0,binding=0) uniform sampler2D image;
            layout(location=0) in vec2 coordinates;
            layout(location=0) out vec4 result;
            void main() { result = applyThreshold(texture(image, coordinates)); }
        """.trimIndent(), "diagnostic label unrelated to origin")
        val files = linkedMapOf(
            "screen/nested/layout.glsl" to "layout(push_constant) uniform Params { float threshold; } params;",
            "helpers.glsl" to "vec4 applyThreshold(vec4 color) { return color * params.threshold; }",
        )
        val retainedIncludes = Collections.unmodifiableMap(LinkedHashMap(files))
        files.clear()
        OpenGLCanonicalShaderCompiler(GlslCompiler()).use { compiler ->
            val compilation = compiler.compile(ShaderModuleDescription(ShaderStage.Fragment, source), "screen/main.frag", retainedIncludes)
            assertEquals(source.label, compilation.module.label)
            assertEquals("main", compilation.module.entryPoint)
            val binary = assertIs<ShaderBinary>(compilation.module.code)
            assertEquals(ShaderBinaryFormat.OpenGLGlsl, binary.format)
            val gl = OpenGLShaderArtifact.decode(binary.bytes)
            assertEquals(ShaderStage.Fragment, gl.stage)
            val image = compilation.reflection.resources.single { it.kind == ShaderInterfaceResourceKind.CombinedTextureSampler }
            assertEquals("image", image.name); assertEquals(0, image.set); assertEquals(0, image.binding)
            val push = compilation.reflection.resources.single { it.kind == ShaderInterfaceResourceKind.PushConstant }
            assertEquals("params", push.name); assertEquals("Params", push.blockName)
            assertEquals(4L, push.members.single().sizeBytes)
            assertEquals("threshold", push.members.single().name)
            assertEquals(gl.pushConstants.single().offsetBytes, push.members.single().offsetBytes)
        }
    }

    @Test
    fun runtimeRequestRejectsUnsupportedEntryRepresentationAndIncludeFailures() {
        val simple = ShaderSource(ShaderLanguage.Glsl, "#version 450\nlayout(location=0) out vec4 color;\nvoid main(){color=vec4(1);}", "simple")
        OpenGLCanonicalShaderCompiler(GlslCompiler()).use { compiler ->
            val description = ShaderModuleDescription(ShaderStage.Fragment, simple)
            assertFailsWith<IllegalArgumentException> { compiler.compile(description.copy(entryPoint = "other"), "simple.frag", emptyMap()) }
            val binary = ShaderBinary.copyOf(ByteBuffer.wrap(byteArrayOf(0)), ShaderBinaryFormat.SpirV, "binary")
            assertFailsWith<IllegalArgumentException> { compiler.compile(description.copy(code = binary), "simple.frag", emptyMap()) }
            val native = ShaderSource(ShaderLanguage.Glsl, "#version 120\nvoid main(){gl_FragColor=vec4(1);}", "native")
            assertFailsWith<IllegalStateException> { compiler.compile(description.copy(code = native), "native.frag", emptyMap()) }
            for (include in listOf("missing.glsl", "../escape.glsl", "cycle.glsl")) {
                val text = "#version 450\n#extension GL_GOOGLE_include_directive : require\n#include \"$include\"\nlayout(location=0) out vec4 color;\nvoid main(){color=vec4(1);}"
                val recursive = mapOf("cycle.glsl" to "#include \"cycle.glsl\"")
                assertFailsWith<IllegalStateException> { compiler.compile(description.copy(code = simple.copy(text = text)), "main.frag", recursive) }
            }
            assertEquals(ShaderStage.Fragment, compiler.compile(description, "simple.frag", emptyMap()).module.stage)
        }
    }
}
