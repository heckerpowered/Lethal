/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.engine.geometry.vertex.VertexSemantics
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.reflection.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuiltinShaderInputsTest {
    @Test
    fun screenMappingRetainsSparseDescriptorSetsAndReflectedPushPadding() {
        val parameter = vectorMember("texelSize", 16, 2)
        val inputs = screenShaderInputs(screen(listOf(image(set = 2, binding = 7), push(listOf(parameter), 32))), "texelSize", 2)
        assertTrue(inputs.vertices.inputs.isEmpty())
        assertEquals(3, inputs.descriptors.size)
        assertTrue(inputs.descriptors.take(2).all { it.layout.bindings.isEmpty() && it.parameters.isEmpty() })
        val descriptor = inputs.descriptors[2]
        assertEquals(setOf(ShaderStage.Fragment), descriptor.layout.bindings.single().stages)
        assertEquals(ParameterName("source"), descriptor.parameters.single().name)
        assertEquals(7, descriptor.parameters.single().binding)
        val packing = inputs.pushes.single()
        assertEquals(setOf(ShaderStage.Fragment), packing.stages)
        assertEquals(0, packing.offsetBytes)
        assertEquals(32, packing.sizeBytes)
        assertEquals(ParameterName("texelSize"), packing.fields.single().name)
        assertEquals(16, packing.fields.single().offsetBytes)
        assertEquals(8, packing.fields.single().sizeBytes)
    }

    @Test
    fun surfaceMappingUsesNamedAttributesAndStructurallyEqualStageLayouts() {
        val vertex = surfaceVertex(textured = true)
        val fragment = surfaceFragment(textured = true, members = surfaceMembers().reversed())
        val inputs = surfaceShaderInputs(raster(vertex, fragment), AlphaRepresentation.Premultiplied)
        assertEquals(listOf(VertexSemantics.Position, VertexSemantics.UV), inputs.vertices.inputs.map { it.semantic })
        assertEquals(listOf(5, 9), inputs.vertices.inputs.map { it.location })
        assertEquals(listOf(VertexFormat.Float32x3, VertexFormat.Float32x2), inputs.vertices.inputs.map { it.format })
        val parameter = inputs.descriptors.single().parameters.single()
        assertEquals(ParameterName("texture"), parameter.name)
        assertEquals(AlphaQuantity.Coverage, parameter.alphaQuantity)
        assertEquals(AlphaRepresentation.Premultiplied, parameter.representation)
        val packing = inputs.pushes.single()
        assertEquals(setOf(ShaderStage.Vertex, ShaderStage.Fragment), packing.stages)
        assertEquals(112, packing.sizeBytes)
        assertEquals(listOf(ParameterName("clipFromLocal"), ParameterName("color")), packing.fields.map { it.name })
        assertEquals(listOf(16, 96), packing.fields.map { it.offsetBytes })
        assertEquals(listOf(64, 16), packing.fields.map { it.sizeBytes })
    }

    @Test
    fun unlitSurfaceRequiresOnlyPositionAndSharedPushConstants() {
        val inputs = surfaceShaderInputs(raster(surfaceVertex(), surfaceFragment()), null)
        assertEquals(listOf(VertexSemantics.Position), inputs.vertices.inputs.map { it.semantic })
        assertTrue(inputs.descriptors.isEmpty())
        assertEquals(setOf(ParameterName("clipFromLocal"), ParameterName("color")), inputs.parameterNames)
    }

    @Test
    fun screenRejectsUnexpectedVertexInputsResourcesVaryingsAndColorOutputs() {
        val valid = screen(listOf(image()))
        val position = ShaderInterfaceVariable("position", 0, vector(3))
        val invalidVertices = listOf(
            ShaderInterfaceDescription(listOf(position), valid.vertex.outputs, emptyList()),
            ShaderInterfaceDescription(emptyList(), valid.vertex.outputs, listOf(push(surfaceMembers(), 112))),
            ShaderInterfaceDescription(emptyList(), emptyList(), emptyList()),
            ShaderInterfaceDescription(emptyList(), listOf(coordinates(4)), emptyList()),
        )
        for (vertex in invalidVertices) {
            assertFailsWith<IllegalArgumentException> { screenShaderInputs(raster(vertex, valid.fragment)) }
        }
        for (outputs in listOf(emptyList(), listOf(ShaderInterfaceVariable("result", 1, vector(4))), listOf(ShaderInterfaceVariable("result", 0, vector(3))))) {
            val fragment = ShaderInterfaceDescription(valid.fragment.inputs, outputs, valid.fragment.resources)
            assertFailsWith<IllegalArgumentException> { screenShaderInputs(raster(valid.vertex, fragment)) }
        }
    }

    @Test
    fun screenRejectsUnsupportedImageShapeCountAndMissingSlots() {
        val invalidImages = listOf(
            image(name = "other"), image(dimensions = listOf(2)), image(set = null), image(binding = null),
            image(set = -1), image(binding = -1), image(dimension = ShaderImageDimension.Cube),
            image(depth = true), image(arrayed = true), image(multisampled = true),
            image(sampledType = ShaderValueDescription(ShaderScalarKind.SignedInteger, 32, 1, 1)),
        )
        for (image in invalidImages) {
            assertFailsWith<IllegalArgumentException> { screenShaderInputs(screen(listOf(image))) }
        }
        assertFails { screenShaderInputs(screen(emptyList())) }
        assertFailsWith<IllegalArgumentException> { screenShaderInputs(screen(listOf(image(), image(binding = 8)))) }
        assertFailsWith<IllegalArgumentException> { screenShaderInputs(screen(listOf(image(), push(surfaceMembers(), 112)))) }
    }

    @Test
    fun screenRejectsUnsupportedParameterShapeAndBlockExtent() {
        val parameter = vectorMember("threshold", 16, 1)
        val invalidMembers = listOf(
            parameter.copy(name = "other"), parameter.copy(sizeBytes = 8), parameter.copy(arrayStrideBytes = 4),
            parameter.copy(matrixStrideBytes = 4), parameter.copy(rowMajor = true),
            parameter.copy(type = ShaderValueDescription(ShaderScalarKind.Float, 64, 1, 1)),
            parameter.copy(type = ShaderValueDescription(ShaderScalarKind.Float, 32, 1, 1, listOf(1))),
        )
        for (member in invalidMembers) {
            assertFailsWith<IllegalArgumentException> { screenShaderInputs(screen(listOf(image(), push(listOf(member), 32))), "threshold") }
        }
        for (size in listOf(null, 0L, 31L, Int.MAX_VALUE.toLong() + 1)) {
            assertFailsWith<IllegalArgumentException> { screenShaderInputs(screen(listOf(image(), push(listOf(parameter), size))), "threshold") }
        }
        assertFailsWith<IllegalArgumentException> { screenShaderInputs(screen(listOf(image())), "threshold") }
    }

    @Test
    fun surfaceRejectsStageDisagreementInMemberOffsetsShapesAndBlockExtent() {
        val members = surfaceMembers()
        val transform = members[0]
        val mismatchedTransforms = listOf(
            transform.copy(offsetBytes = 0), transform.copy(sizeBytes = 80), transform.copy(matrixStrideBytes = 32),
            transform.copy(arrayStrideBytes = 16), transform.copy(rowMajor = true), transform.copy(name = "other"),
            transform.copy(type = ShaderValueDescription(ShaderScalarKind.Float, 32, 3, 4)),
        )
        for (member in mismatchedTransforms) {
            val fragment = surfaceFragment(members = listOf(member, members[1]))
            assertFails { surfaceShaderInputs(raster(surfaceVertex(), fragment), null) }
        }
        val fragment = surfaceFragment(size = 128)
        assertFailsWith<IllegalArgumentException> { surfaceShaderInputs(raster(surfaceVertex(), fragment), null) }
    }

    @Test
    fun surfaceRejectsUnsupportedTransformPackingInTheReferenceStage() {
        val members = surfaceMembers()
        for (transform in listOf(members[0].copy(rowMajor = true), members[0].copy(matrixStrideBytes = 32), members[0].copy(sizeBytes = 80))) {
            val changed = listOf(transform, members[1])
            assertFailsWith<IllegalArgumentException> { surfaceShaderInputs(raster(surfaceVertex(members = changed), surfaceFragment(members = changed)), null) }
        }
    }

    private fun raster(vertex: ShaderInterfaceDescription, fragment: ShaderInterfaceDescription) =
        RasterShaderInterface(linkedMapOf(ShaderStage.Vertex to vertex, ShaderStage.Fragment to fragment))

    private fun screen(resources: List<ShaderInterfaceResource>) = raster(
        ShaderInterfaceDescription(emptyList(), listOf(coordinates()), emptyList()),
        ShaderInterfaceDescription(listOf(coordinates()), listOf(ShaderInterfaceVariable("result", 0, vector(4))), resources),
    )

    private fun surfaceVertex(textured: Boolean = false, members: List<ShaderInterfaceBlockMember> = surfaceMembers()) = ShaderInterfaceDescription(
        listOfNotNull(if (textured) ShaderInterfaceVariable("uv", 9, vector(2)) else null, ShaderInterfaceVariable("position", 5, vector(3))),
        if (textured) listOf(coordinates()) else emptyList(),
        listOf(push(members, 112)),
    )

    private fun surfaceFragment(textured: Boolean = false, members: List<ShaderInterfaceBlockMember> = surfaceMembers(), size: Long = 112) = ShaderInterfaceDescription(
        if (textured) listOf(coordinates()) else emptyList(),
        listOf(ShaderInterfaceVariable("result", 0, vector(4))),
        listOfNotNull(push(members, size), if (textured) image() else null),
    )

    private fun surfaceMembers() = listOf(
        ShaderInterfaceBlockMember("clipFromLocal", 16, 64, ShaderValueDescription(ShaderScalarKind.Float, 32, 4, 4), 16, 0, false),
        vectorMember("color", 96, 4),
    )

    private fun vector(components: Int) = ShaderValueDescription(ShaderScalarKind.Float, 32, components, 1)
    private fun coordinates(location: Int = 3) = ShaderInterfaceVariable("coordinates", location, vector(2))
    private fun vectorMember(name: String, offset: Int, components: Int) = ShaderInterfaceBlockMember(name, offset, components.toLong() * Float.SIZE_BYTES, vector(components), 0, 0, false)
    private fun push(members: List<ShaderInterfaceBlockMember>, size: Long?) = ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), size, "Params", members, null)

    private fun image(name: String = "image", set: Int? = 0, binding: Int? = 7, dimensions: List<Int?> = emptyList(), dimension: ShaderImageDimension = ShaderImageDimension.TwoDimensional, depth: Boolean = false, arrayed: Boolean = false, multisampled: Boolean = false, sampledType: ShaderValueDescription = vector(1)) = ShaderInterfaceResource(
        ShaderInterfaceResourceKind.CombinedTextureSampler, name, set, binding, dimensions, null, null, emptyList(),
        ShaderImageDescription(dimension, depth, arrayed, multisampled, sampledType),
    )
}
