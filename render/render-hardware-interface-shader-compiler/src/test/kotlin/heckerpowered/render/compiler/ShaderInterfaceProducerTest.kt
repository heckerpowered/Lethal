/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.reflection.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.*

class ShaderInterfaceProducerTest {
    @Test
    fun fullscreenCopyTentAndBrightnessRetainOriginalFacts() = fixtures { root ->
        GlslCompiler().use { compiler ->
            fun compile(name: String, stage: ShaderStage): ShaderInterfaceArtifact {
                val shader = compiler.compile(root, root.resolve(name), stage)
                val encoded = shaderInterfaceArtifact(shader).encode()
                val facts = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(encoded))
                assertTrue(facts.matchesSpirV(shader.spirV()))
                assertEquals(stage, facts.stage); assertEquals("main", facts.entryPoint)
                val glBefore = lowerToOpenGL(shader).encode()
                assertContentEquals(encoded, shaderInterfaceArtifact(shader).encode())
                assertContentEquals(glBefore, lowerToOpenGL(shader).encode())
                return facts
            }
            val vertex = compile("fullscreen.vert", ShaderStage.Vertex).description
            assertTrue(vertex.inputs.isEmpty()); assertTrue(vertex.resources.isEmpty())
            assertEquals("coordinates", vertex.outputs.single().name)
            assertEquals(0, vertex.outputs.single().location); assertEquals(2, vertex.outputs.single().type.components)
            for (name in listOf("copy.frag", "tent.frag", "brightness.frag")) {
                val facts = compile(name, ShaderStage.Fragment).description
                assertEquals("coordinates", facts.inputs.single().name); assertEquals(0, facts.inputs.single().location)
                assertEquals("result", facts.outputs.single().name); assertEquals(4, facts.outputs.single().type.components)
                val image = facts.resources.single { it.kind == ShaderInterfaceResourceKind.CombinedTextureSampler }
                assertEquals("image", image.name); assertEquals(0, image.set); assertEquals(0, image.binding); assertEquals(1L, image.descriptorCount)
                val shape = assertNotNull(image.image)
                assertEquals(ShaderImageDimension.TwoDimensional, shape.dimension)
                assertFalse(shape.depth || shape.arrayed || shape.multisampled)
                assertEquals(ShaderScalarKind.Float, shape.sampledType.scalar); assertEquals(32, shape.sampledType.bitWidth)
                val pushes = facts.resources.filter { it.kind == ShaderInterfaceResourceKind.PushConstant }
                when (name) {
                    "copy.frag" -> assertTrue(pushes.isEmpty())
                    "tent.frag" -> assertPush(pushes.single(), "Tent", "tentFilter", "texelSize", 2, 8)
                    "brightness.frag" -> assertPush(pushes.single(), "Brightness", "bloom", "threshold", 1, 4)
                }
            }
        }
    }

    @Test
    fun rawNonFloatFactsArePreservedEvenWhenOpenGLCannotLowerThem() = fixtures { root ->
        val source = root.resolve("numeric.frag")
        source.writeText("""
            #version 450
            layout(set=1,binding=2,std430) readonly buffer Numeric { ivec2 signedValue; uvec2 unsignedValue; } numeric;
            layout(location=0) out vec4 color;
            void main() { color = vec4(vec2(numeric.signedValue) + vec2(numeric.unsignedValue),0,1); }
        """.trimIndent())
        GlslCompiler().use { compiler ->
            val shader = compiler.compile(root, source, ShaderStage.Fragment)
            val artifact = ShaderInterfaceArtifact.decode(ByteBuffer.wrap(shaderInterfaceArtifact(shader).encode()))
            val resource = artifact.description.resources.single()
            assertEquals(ShaderInterfaceResourceKind.StorageBuffer, resource.kind)
            assertEquals(listOf(ShaderScalarKind.SignedInteger, ShaderScalarKind.UnsignedInteger), resource.members.map { it.type.scalar })
            assertEquals(listOf(32, 32), resource.members.map { it.type.bitWidth })
            assertEquals(listOf(8L, 8L), resource.members.map { it.sizeBytes })
            assertFailsWith<IllegalArgumentException> { lowerToOpenGL(shader) }
        }
    }

    private fun assertPush(resource: ShaderInterfaceResource, block: String, instance: String, member: String, components: Int, size: Long) {
        assertEquals(block, resource.blockName); assertEquals(instance, resource.name); assertEquals(size, resource.sizeBytes)
        val value = resource.members.single()
        assertEquals(member, value.name); assertEquals(0, value.offsetBytes); assertEquals(size, value.sizeBytes)
        assertEquals(ShaderScalarKind.Float, value.type.scalar); assertEquals(32, value.type.bitWidth)
        assertEquals(components, value.type.components); assertEquals(1, value.type.columns)
        assertEquals(0, value.matrixStrideBytes); assertEquals(0, value.arrayStrideBytes); assertFalse(value.rowMajor)
    }
}

private fun fixtures(action: (Path) -> Unit) {
    val root = Files.createTempDirectory("rhi-interface-fixtures")
    try {
        for (name in listOf("fullscreen.vert", "copy.frag", "tent.frag", "brightness.frag")) {
            val resource = checkNotNull(ShaderInterfaceProducerTest::class.java.getResourceAsStream("/interface-fixtures/$name"))
            resource.use { Files.copy(it, root.resolve(name)) }
        }
        action(root)
    } finally { root.toFile().deleteRecursively() }
}
