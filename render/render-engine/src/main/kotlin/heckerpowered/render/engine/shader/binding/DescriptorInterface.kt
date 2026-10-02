/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.material.parameter.*
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.shader.binding.*

/**
 * Fills one descriptor set from named appearance, view, or geometry parameters.
 *
 * The layout declares the shader resource slots, while each [DescriptorParameter] selects the
 * parameter used for a slot. Every binding and every array element must be mapped exactly once;
 * missing mappings, duplicate mappings, and undeclared slots are rejected at construction.
 * Numeric sizes must be positive and attached to uniform-buffer slots; alpha requirements
 * apply to slots supplied by texture parameters.
 *
 * Resolution checks value forms, numeric sizes and alpha requirements before allocating temporary uniform buffers.
 * The slot selects which parts of a texture parameter to bind. Numeric uniform bytes receive
 * temporary GPU storage through pass preparation. Existing buffers, image views, and samplers
 * remain external resources: resolving the set does not copy their contents or extend their
 * lifetime. The RHI descriptor set checks the resulting resources against [layout].
 * [DescriptorParameterValue] accepts only a preformed texture view; alpha requirements still
 * require [TextureParameterValue].
 */
class DescriptorInterface(
    val layout: DescriptorSetLayout,
    parameters: List<DescriptorParameter>,
) {
    val parameters = parameters.toList()

    init {
        val supplied = this.parameters.map { it.binding to it.element }
        val expected = layout.bindings.flatMap { declaration ->
            (0 until declaration.descriptorCount).map { declaration.binding to it }
        }
        require(supplied.toSet() == expected.toSet() && supplied.size == expected.size)
        for (parameter in this.parameters) {
            val type = checkNotNull(layout.findBinding(parameter.binding)).type
            parameter.numericSizeBytes?.let { size ->
                require(type is DescriptorType.UniformBuffer && size > 0) { "${parameter.name.value} requires a positive numeric size on a uniform-buffer slot" }
            }
            require(parameter.alphaQuantity == null && parameter.representation == null || acceptsTexture(type)) { "${parameter.name.value} declares alpha requirements on a non-texture slot" }
        }
    }

    internal fun resolve(values: ParameterValues, preparation: PassPreparation): DescriptorSet {
        for (parameter in parameters) {
            val type = checkNotNull(layout.findBinding(parameter.binding)).type
            validateParameter(parameter, type, values.require(parameter.name), preparation)
        }
        val bindings = layout.bindings.map { declaration ->
            val resources = (0 until declaration.descriptorCount).map { element ->
                val parameter = parameters.single { it.binding == declaration.binding && it.element == element }
                resolveParameter(declaration.type, values.require(parameter.name), preparation)
            }
            DescriptorBinding(declaration.binding, resources)
        }
        return DescriptorSet(layout, bindings)
    }

    private fun validateParameter(parameter: DescriptorParameter, type: DescriptorType, value: ParameterValue, preparation: PassPreparation) {
        if (parameter.alphaQuantity != null || parameter.representation != null) {
            val texture = requireValue<TextureParameterValue>(parameter, value)
            preparation.validateTexture(texture)
            require(parameter.alphaQuantity == null || parameter.alphaQuantity == texture.sampledAlphaQuantity) { "${parameter.name.value} has an incompatible sampled alpha meaning" }
            require(parameter.representation == null || texture.sampledRepresentation == null || parameter.representation == texture.sampledRepresentation) { "${parameter.name.value} has an incompatible sampled RGB association" }
        }
        val compatible = when (value) {
            is NumericParameterValue -> {
                require(type is DescriptorType.UniformBuffer) { "${parameter.name.value} requires a uniform-buffer slot for numeric bytes" }
                require(value.sizeBytes == parameter.numericSizeBytes && value.sizeBytes > 0) { "${parameter.name.value} has an unexpected uniform size" }
                require(value.sizeBytes.toLong() >= type.minimumSizeBytes) { "${parameter.name.value} is smaller than the uniform-buffer slot requires" }
                true
            }

            is BufferParameterValue -> type is DescriptorType.UniformBuffer || type is DescriptorType.StorageBuffer
            is TextureParameterValue -> {
                if (type != DescriptorType.Sampler) preparation.validateTexture(value)
                acceptsTexture(type)
            }

            is DescriptorParameterValue -> value.resource is DescriptorResource.Texture && acceptsTextureView(type)
            is TexelBufferParameterValue -> type is DescriptorType.UniformTexelBuffer || type is DescriptorType.StorageTexelBuffer
        }
        require(compatible) { "${parameter.name.value} cannot supply $type" }
    }

    private fun resolveParameter(type: DescriptorType, value: ParameterValue, preparation: PassPreparation): DescriptorResource = when (value) {
        is NumericParameterValue -> DescriptorResource.Buffer(preparation.uniform(value))
        is BufferParameterValue -> DescriptorResource.Buffer(value.view)
        is DescriptorParameterValue -> value.resource
        is TexelBufferParameterValue -> DescriptorResource.TexelBuffer(value.view, value.format)
        is TextureParameterValue -> when (type) {
            is DescriptorType.SampledTexture, is DescriptorType.StorageTexture, is DescriptorType.InputAttachment -> DescriptorResource.Texture(value.view)
            is DescriptorType.CombinedTextureSampler -> DescriptorResource.CombinedTextureSampler(value.view, value.sampler)
            DescriptorType.Sampler -> DescriptorResource.Sampler(value.sampler)
            else -> error("Texture parameter was not validated for $type")
        }
    }

    private fun acceptsTexture(type: DescriptorType): Boolean =
        acceptsTextureView(type) || type is DescriptorType.CombinedTextureSampler || type == DescriptorType.Sampler

    private fun acceptsTextureView(type: DescriptorType): Boolean =
        type is DescriptorType.SampledTexture || type is DescriptorType.StorageTexture || type is DescriptorType.InputAttachment

    private inline fun <reified T : ParameterValue> requireValue(parameter: DescriptorParameter, value: ParameterValue): T =
        requireNotNull(value as? T) { "${parameter.name.value} requires ${T::class.java.simpleName}" }
}
