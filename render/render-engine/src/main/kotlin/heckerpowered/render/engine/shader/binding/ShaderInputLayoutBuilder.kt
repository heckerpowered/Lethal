/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.geometry.vertex.VertexSemantic
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.shader.parameter.ParameterDerivations
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.binding.DescriptorBindingLayout
import heckerpowered.render.shader.binding.DescriptorSetLayout
import heckerpowered.render.shader.binding.DescriptorType

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class ShaderInputLayoutDsl

/** Maps geometry semantics and named parameters to shader inputs; byte offsets remain explicit. */
fun shaderInputLayout(block: ShaderInputLayoutBuilder.() -> Unit): ShaderInputLayout =
    ShaderInputLayoutBuilder().apply(block).build()

@ShaderInputLayoutDsl
class ShaderInputLayoutBuilder internal constructor() {
    private val vertices = VertexInputMappingBuilder()
    private val descriptors = linkedMapOf<Int, DescriptorInputMapping>()
    private val pushes = mutableListOf<PushConstantPacking>()
    private var derivations = ParameterDerivations()

    fun vertices(block: VertexInputMappingBuilder.() -> Unit) {
        vertices.apply(block)
    }

    fun descriptorSet(index: Int, vararg stages: ShaderStage, block: DescriptorInputMappingBuilder.() -> Unit) {
        descriptorSet(index, DescriptorInputMappingBuilder(stages.toSet()).apply(block).build())
    }

    /** Keeps explicit set numbering, including empty positions before this set. */
    fun descriptorSet(index: Int, mapping: DescriptorInputMapping) {
        require(index >= 0 && index < Int.MAX_VALUE) { "Descriptor set index must fit in a numbered set list" }
        require(!descriptors.containsKey(index)) { "Descriptor set $index is already declared" }
        descriptors[index] = mapping
    }

    /** Places one write in the push address space; parameter offsets are relative to that write. */
    fun pushConstants(sizeBytes: Int, vararg stages: ShaderStage, offsetBytes: Int = 0, block: PushConstantPackingBuilder.() -> Unit) {
        pushes += PushConstantPackingBuilder(stages.toSet(), offsetBytes, sizeBytes).apply(block).build()
    }

    fun derivations(value: ParameterDerivations) {
        derivations = value
    }

    internal fun build(): ShaderInputLayout {
        val setCount = (descriptors.keys.maxOrNull() ?: -1) + 1
        val mappings = List(setCount) { index ->
            descriptors[index] ?: DescriptorInputMapping(DescriptorSetLayout.Empty, emptyList())
        }
        return ShaderInputLayout(vertices.build(), mappings, pushes, derivations)
    }
}

@ShaderInputLayoutDsl
class VertexInputMappingBuilder internal constructor() {
    private val inputs = mutableListOf<AttributeInput>()

    fun attribute(semantic: VertexSemantic, location: Int, format: VertexFormat) {
        inputs += AttributeInput(semantic, location, format)
    }

    internal fun build(): VertexInputMapping = VertexInputMapping(inputs)
}

@ShaderInputLayoutDsl
class DescriptorInputMappingBuilder internal constructor(private val stages: Set<ShaderStage>) {
    private val bindings = mutableListOf<DescriptorBindingLayout>()
    private val parameters = mutableListOf<DescriptorParameter>()

    fun combinedTextureSampler(name: String, binding: Int, alphaQuantity: AlphaQuantity? = null, representation: AlphaRepresentation? = null) {
        bindings += DescriptorBindingLayout(binding, DescriptorType.CombinedTextureSampler(), stages)
        parameters += DescriptorParameter(ParameterName(name), binding, alphaQuantity = alphaQuantity, representation = representation)
    }

    internal fun build(): DescriptorInputMapping = DescriptorInputMapping(DescriptorSetLayout(bindings), parameters)
}

@ShaderInputLayoutDsl
class PushConstantPackingBuilder internal constructor(
    private val stages: Set<ShaderStage>,
    private val offsetBytes: Int,
    private val sizeBytes: Int,
) {
    private val fields = mutableListOf<PushConstantField>()

    fun parameter(name: String, offsetBytes: Int, sizeBytes: Int) {
        fields += PushConstantField(ParameterName(name), offsetBytes, sizeBytes)
    }

    internal fun build(): PushConstantPacking = PushConstantPacking(stages, offsetBytes, sizeBytes, fields)
}
