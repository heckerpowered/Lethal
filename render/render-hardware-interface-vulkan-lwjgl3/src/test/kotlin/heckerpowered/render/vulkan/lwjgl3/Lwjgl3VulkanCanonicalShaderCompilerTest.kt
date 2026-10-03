/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.vulkan.lwjgl3

import heckerpowered.render.compiler.GlslCompiler
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.ShaderInterfaceResourceKind
import kotlin.test.*

/** Actual shaderc/SPIRV-Cross CPU tests; these create no Vulkan instance, device or GPU resource. */
class Lwjgl3VulkanCanonicalShaderCompilerTest {
    @Test
    fun actualCompilerPreservesOriginalSurfaceBlockAndProducesBothStages() {
        Lwjgl3VulkanCanonicalShaderCompiler(GlslCompiler()).use { compiler ->
            for (stage in listOf(ShaderStage.Vertex, ShaderStage.Fragment)) {
                val source = surfaceSource(stage)
                val compiled = compiler.compile(ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, source, "surface")), "surface/${stage.name}.glsl", emptyMap())
                assertEquals(stage, compiled.module.stage)
                assertEquals("main", compiled.module.entryPoint)
                val binary = compiled.module.code as ShaderBinary
                assertEquals(ShaderBinaryFormat.SpirV, binary.format)
                assertEquals(0x07230203, binary.bytes.getInt(0))
                assertEquals(0x00010000, binary.bytes.getInt(4))
                val block = compiled.reflection.resources.single { it.kind == ShaderInterfaceResourceKind.PushConstant }
                assertEquals(80L, block.sizeBytes)
                assertEquals(listOf("clipFromLocal", "color"), block.members.map { it.name })
                assertEquals(listOf(0, 64), block.members.map { it.offsetBytes })
                assertEquals(16, block.members.first().matrixStrideBytes)
                assertFalse(block.members.first().rowMajor)
                if (stage == ShaderStage.Vertex) {
                    val input = compiled.reflection.inputs.single()
                    assertEquals(0, input.location)
                    assertEquals(3, input.type.components)
                } else {
                    assertEquals(listOf(0), compiled.reflection.outputs.map { it.location })
                }
                assertTrue(binary.bytes.isReadOnly)
            }
        }
    }

    @Test
    fun actualIncludesUseVirtualOriginAndSyntaxFailureDoesNotCloseTheCompiler() {
        Lwjgl3VulkanCanonicalShaderCompiler(GlslCompiler()).use { compiler ->
            val source = "#version 450\n#extension GL_GOOGLE_include_directive : require\n#include \"fill.glsl\"\nlayout(location=0) out vec4 color;\nvoid main(){color=FILL;}"
            val description = ShaderModuleDescription(ShaderStage.Fragment, ShaderSource(ShaderLanguage.Glsl, source, "included"))
            val compiled = compiler.compile(description, "surface/source.frag", mapOf("surface/fill.glsl" to "#define FILL vec4(1,0,0,1)"))
            assertEquals(ShaderBinaryFormat.SpirV, (compiled.module.code as ShaderBinary).format)
            assertFailsWith<IllegalStateException> { compiler.compile(description.copy(code = ShaderSource(ShaderLanguage.Glsl, "invalid GLSL", "invalid")), "invalid.frag", emptyMap()) }
            assertEquals(ShaderStage.Fragment, compiler.compile(description, "surface/source.frag", mapOf("surface/fill.glsl" to "#define FILL vec4(0,1,0,1)")).module.stage)
        }
    }

    @Test
    fun compilationPreservesDescriptorsAndVaryingsWithoutClaimingPipelineSupport() {
        Lwjgl3VulkanCanonicalShaderCompiler(GlslCompiler()).use { compiler ->
            val source = "#version 450\nlayout(location=0) in vec2 uv;\nlayout(location=0) out vec4 color;\nlayout(set=2,binding=3) uniform sampler2D image;\nvoid main(){color=texture(image,uv);}"
            val compiled = compiler.compile(ShaderModuleDescription(ShaderStage.Fragment, ShaderSource(ShaderLanguage.Glsl, source, "sampled")), "sampled.frag", emptyMap())
            assertEquals(listOf(0), compiled.reflection.inputs.map { it.location })
            val resource = compiled.reflection.resources.single()
            assertEquals(ShaderInterfaceResourceKind.CombinedTextureSampler, resource.kind)
            assertEquals(2, resource.set)
            assertEquals(3, resource.binding)
        }
    }

    @Test
    fun unsupportedInputRejectsAndClosedCompilerCannotAllocateAgain() {
        val compiler = Lwjgl3VulkanCanonicalShaderCompiler(GlslCompiler())
        val description = ShaderModuleDescription(ShaderStage.Vertex, ShaderSource(ShaderLanguage.Glsl, surfaceSource(ShaderStage.Vertex), "surface"))
        assertFailsWith<IllegalArgumentException> { compiler.compile(description.copy(entryPoint = "other"), "source", emptyMap()) }
        compiler.close(); compiler.close()
        assertFailsWith<IllegalStateException> { compiler.compile(description, "source", emptyMap()) }
    }

    private fun surfaceSource(stage: ShaderStage): String {
        val block = "layout(push_constant) uniform Surface { mat4 clipFromLocal; vec4 color; } surface;"
        return if (stage == ShaderStage.Vertex) "#version 450\nlayout(location=0) in vec3 position;\n$block\nvoid main(){gl_Position=surface.clipFromLocal*vec4(position,1);}"
        else "#version 450\n$block\nlayout(location=0) out vec4 result;\nvoid main(){result=surface.color;}"
    }
}
