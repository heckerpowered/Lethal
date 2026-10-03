/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.math.Matrices
import heckerpowered.render.engine.geometry.vertex.VertexSemantic
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.shader.parameter.NumericPacking
import heckerpowered.render.engine.shader.parameter.ParameterDerivations
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.binding.DescriptorBindingLayout
import heckerpowered.render.shader.binding.DescriptorSetLayout
import heckerpowered.render.shader.binding.DescriptorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShaderInputLayoutDslTest {
    @Test
    fun textureDeclarationConnectsOneNamedParameterToItsSlotAndAlphaContract() {
        val position = VertexSemantic("position")
        val inputs = shaderInputLayout {
            vertices { attribute(position, location = 5, format = VertexFormat.Float32x3) }
            descriptorSet(0, ShaderStage.Fragment) {
                combinedTextureSampler(
                    "albedo",
                    binding = 7,
                    alphaQuantity = AlphaQuantity.Coverage,
                    representation = AlphaRepresentation.Straight,
                )
            }
        }

        assertEquals(AttributeInput(position, 5, VertexFormat.Float32x3), inputs.vertices.inputs.single())
        val descriptor = inputs.descriptors.single()
        val slot = descriptor.layout.bindings.single()
        val parameter = descriptor.parameters.single()
        assertEquals(7, slot.binding)
        assertIs<DescriptorType.CombinedTextureSampler>(slot.type)
        assertEquals(setOf(ShaderStage.Fragment), slot.stages)
        assertEquals(1, slot.descriptorCount)
        assertEquals(ParameterName("albedo"), parameter.name)
        assertEquals(slot.binding, parameter.binding)
        assertEquals(0, parameter.element)
        assertEquals(AlphaQuantity.Coverage, parameter.alphaQuantity)
        assertEquals(AlphaRepresentation.Straight, parameter.representation)
        assertEquals(setOf(ParameterName("albedo")), inputs.parameterNames)
    }

    @Test
    fun sparseSetNumbersPreserveArrayMappingsAndMixedStageVisibility() {
        val mapping = DescriptorInputMapping(
            DescriptorSetLayout(
                listOf(
                    DescriptorBindingLayout(4, DescriptorType.CombinedTextureSampler(), setOf(ShaderStage.Fragment), descriptorCount = 2),
                    DescriptorBindingLayout(9, DescriptorType.UniformBuffer(16), setOf(ShaderStage.Vertex)),
                ),
            ),
            listOf(
                DescriptorParameter(ParameterName("firstTexture"), 4, element = 0),
                DescriptorParameter(ParameterName("secondTexture"), 4, element = 1),
                DescriptorParameter(ParameterName("scene"), 9, numericSizeBytes = 16),
            ),
        )
        val inputs = shaderInputLayout {
            descriptorSet(3, mapping)
            descriptorSet(1, ShaderStage.Fragment) { combinedTextureSampler("detail", binding = 6) }
        }

        assertEquals(4, inputs.descriptors.size)
        for (index in listOf(0, 2)) {
            assertSame(DescriptorSetLayout.Empty, inputs.descriptors[index].layout)
            assertTrue(inputs.descriptors[index].parameters.isEmpty())
        }
        assertEquals(6, inputs.descriptors[1].layout.bindings.single().binding)
        assertEquals(ParameterName("detail"), inputs.descriptors[1].parameters.single().name)
        assertSame(mapping, inputs.descriptors[3])
        assertEquals(setOf(ShaderStage.Fragment), inputs.descriptors[3].layout.findBinding(4)!!.stages)
        assertEquals(setOf(ShaderStage.Vertex), inputs.descriptors[3].layout.findBinding(9)!!.stages)
        assertEquals(2, inputs.descriptors[3].layout.findBinding(4)!!.descriptorCount)
        assertEquals(listOf(4 to 0, 4 to 1, 9 to 0), inputs.descriptors[3].parameters.map { it.binding to it.element })
        assertEquals(setOf("firstTexture", "secondTexture", "scene", "detail"), inputs.parameterNames.map { it.value }.toSet())
    }

    @Test
    fun pushFieldsStayRelativeToTheWriteAndConsumePreservedDerivations() {
        val configuredDerivations = ParameterDerivations(
            packing = listOf(
                NumericPacking(ParameterName("packed"), 4, listOf(PushConstantField(ParameterName("gain"), 0, 4))),
            ),
        )
        val inputs = shaderInputLayout {
            derivations(configuredDerivations)
            pushConstants(12, ShaderStage.Fragment, offsetBytes = 48) {
                parameter("packed", offsetBytes = 8, sizeBytes = 4)
            }
        }
        val values = inputs.derivations.resolve(
            ParameterValues(ParameterName("gain") to NumericParameterValue.floats(.75f)),
            Matrices.Identity,
        )
        val packing = inputs.pushes.single()
        val write = packing.resolve(values)
        val bytes = write.asByteBuffer()

        assertSame(configuredDerivations, inputs.derivations)
        assertEquals(48, packing.offsetBytes)
        assertEquals(8, packing.fields.single().offsetBytes)
        assertEquals(48, write.destinationOffsetBytes)
        assertEquals(12, write.sizeBytes)
        assertEquals(setOf(ShaderStage.Fragment), write.stages)
        assertEquals(0L, bytes.getLong(0))
        assertEquals(.75f, bytes.getFloat(8))
        assertEquals(setOf(ParameterName("packed")), inputs.parameterNames)
    }

    @Test
    fun escapedBuildersCannotChangeTheCompletedLayoutSnapshot() {
        lateinit var layoutBuilder: ShaderInputLayoutBuilder
        lateinit var vertexBuilder: VertexInputMappingBuilder
        lateinit var descriptorBuilder: DescriptorInputMappingBuilder
        lateinit var pushBuilder: PushConstantPackingBuilder
        val position = VertexSemantic("position")
        val initialDerivations = ParameterDerivations()
        val inputs = shaderInputLayout {
            layoutBuilder = this
            vertices {
                vertexBuilder = this
                attribute(position, location = 0, format = VertexFormat.Float32x3)
            }
            descriptorSet(1, ShaderStage.Fragment) {
                descriptorBuilder = this
                combinedTextureSampler("texture", binding = 0)
            }
            pushConstants(8, ShaderStage.Fragment) {
                pushBuilder = this
                parameter("gain", offsetBytes = 0, sizeBytes = 4)
            }
            derivations(initialDerivations)
        }
        vertexBuilder.attribute(VertexSemantic("normal"), location = 1, format = VertexFormat.Float32x3)
        descriptorBuilder.combinedTextureSampler("laterTexture", binding = 2)
        pushBuilder.parameter("laterGain", offsetBytes = 4, sizeBytes = 4)
        layoutBuilder.descriptorSet(3, ShaderStage.Fragment) { combinedTextureSampler("otherSet", binding = 0) }
        layoutBuilder.pushConstants(4, ShaderStage.Fragment, offsetBytes = 8) { parameter("otherPush", offsetBytes = 0, sizeBytes = 4) }
        layoutBuilder.derivations(ParameterDerivations())

        assertEquals(listOf(AttributeInput(position, 0, VertexFormat.Float32x3)), inputs.vertices.inputs)
        assertEquals(2, inputs.descriptors.size)
        assertEquals(listOf(0), inputs.descriptors[1].layout.bindings.map { it.binding })
        assertEquals(listOf(ParameterName("texture")), inputs.descriptors[1].parameters.map { it.name })
        assertEquals(1, inputs.pushes.size)
        assertEquals(listOf(PushConstantField(ParameterName("gain"), 0, 4)), inputs.pushes.single().fields)
        assertSame(initialDerivations, inputs.derivations)
        assertEquals(setOf(ParameterName("texture"), ParameterName("gain")), inputs.parameterNames)
    }

    @Test
    fun descriptorSetAddressesMustFitTheSetListAndCannotBeDeclaredTwice() {
        assertFailsWith<IllegalArgumentException> {
            shaderInputLayout {
                descriptorSet(-1, ShaderStage.Fragment) { combinedTextureSampler("texture", binding = 0) }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            shaderInputLayout {
                descriptorSet(Int.MAX_VALUE, DescriptorInputMapping(DescriptorSetLayout.Empty, emptyList()))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            shaderInputLayout {
                descriptorSet(2, ShaderStage.Fragment) { combinedTextureSampler("first", binding = 0) }
                descriptorSet(2, ShaderStage.Fragment) { combinedTextureSampler("second", binding = 1) }
            }
        }
    }
}
