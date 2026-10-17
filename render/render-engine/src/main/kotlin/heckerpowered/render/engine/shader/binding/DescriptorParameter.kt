/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.*
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.shader.binding.DescriptorResource
import heckerpowered.render.shader.binding.DescriptorType

/**
 * Connects a named parameter to one binding and array element in a descriptor set.
 *
 * [binding] is the layout's explicit binding number, and [element] selects an element of that
 * binding's fixed array. They do not depend on the position of this declaration in a list.
 *
 * [numericSizeBytes] must specify the exact byte size when numeric values supply a uniform buffer.
 * Existing buffer ranges are checked against the descriptor layout instead; this value
 * does not impose a separate size on them.
 */
class DescriptorParameter(
    val name: ParameterName,
    val binding: Int,
    val numericSizeBytes: Int? = null,
    val element: Int = 0,
    val alphaQuantity: AlphaQuantity? = null,
    val representation: AlphaRepresentation? = null,
) {
    init {
        require(binding >= 0) { "Descriptor parameter binding must not be negative" }
        require(element >= 0) { "Descriptor parameter element must not be negative" }
        require(numericSizeBytes == null || numericSizeBytes > 0) { "${name.value} requires a positive numeric size" }
    }

    internal fun validateDeclaration(type: DescriptorType) {
        if (numericSizeBytes != null) {
            require(type is DescriptorType.UniformBuffer) { "${name.value} requires a positive numeric size on a uniform-buffer slot" }
        }
        require(alphaQuantity == null && representation == null || acceptsTexture(type)) { "${name.value} declares alpha requirements on a non-texture slot" }
    }

    internal fun validateValue(type: DescriptorType, value: ParameterValue, preparation: PassPreparation) {
        if (alphaQuantity != null || representation != null) {
            val texture = requireValue<TextureParameterValue>(value)
            preparation.validateTexture(texture)
            require(alphaQuantity == null || alphaQuantity == texture.sampledAlphaQuantity) { "${name.value} has an incompatible sampled alpha meaning" }
            require(representation == null || texture.sampledRepresentation == null || representation == texture.sampledRepresentation) { "${name.value} has an incompatible sampled RGB association" }
        }
        val compatible = when (value) {
            is NumericParameterValue -> {
                require(type is DescriptorType.UniformBuffer) { "${name.value} requires a uniform-buffer slot for numeric bytes" }
                require(value.sizeBytes == numericSizeBytes && value.sizeBytes > 0) { "${name.value} has an unexpected uniform size" }
                require(value.sizeBytes.toLong() >= type.minimumSizeBytes) { "${name.value} is smaller than the uniform-buffer slot requires" }
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
        require(compatible) { "${name.value} cannot supply $type" }
    }

    internal fun resolveValidatedResource(type: DescriptorType, value: ParameterValue, preparation: PassPreparation): DescriptorResource = when (value) {
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

    private inline fun <reified T : ParameterValue> requireValue(value: ParameterValue): T =
        requireNotNull(value as? T) { "${name.value} requires ${T::class.java.simpleName}" }
}
