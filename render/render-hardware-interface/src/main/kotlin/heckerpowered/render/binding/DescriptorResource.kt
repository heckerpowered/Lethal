/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.binding

import heckerpowered.render.buffer.BufferUsage
import heckerpowered.render.buffer.GpuBufferView
import heckerpowered.render.memory.Size
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.sampler.GpuSampler
import heckerpowered.render.texture.*

/**
 * Selects the actual resource supplied to one element of a shader binding.
 *
 * A layout declares a slot's role, such as uniform-buffer access or sampled-texture access.
 * This value supplies the buffer range, texture view, or sampler used for that role. The same
 * byte-range representation can serve uniform and storage slots when its buffer permits both.
 * The layout determines the required access rather than duplicating it in the resource value.
 *
 * Wrapping a resource does not copy its contents. Buffer and image writes still change the data
 * seen by later shader accesses, subject to their ordering and synchronization requirements.
 */
sealed interface DescriptorResource {
    /**
     * Supplies a byte range for a uniform-buffer or storage-buffer slot.
     *
     * Shader offset zero starts at [view]'s byte offset, allowing several parameter blocks or
     * tables to share one buffer allocation. The binding role determines the required usage.
     */
    data class Buffer(val view: GpuBufferView) : DescriptorResource

    /**
     * Supplies a texture view for sampled, storage, or input-attachment access.
     *
     * The view fixes the image shape, mip levels, layers, and aspects exposed to the shader.
     * The slot declaration determines the access role; this wrapper cannot add a missing usage.
     */
    data class Texture(val view: GpuTextureView) : DescriptorResource

    /** Supplies reusable sampling rules independently of a texture view. */
    data class Sampler(val sampler: GpuSampler) : DescriptorResource

    /**
     * Supplies a texture view and sampler together, for a combined slot such as GLSL sampler2D.
     *
     * This pairs two existing resources. It does not make the sampler part of the image storage.
     */
    data class CombinedTextureSampler(
        val view: GpuTextureView,
        val sampler: GpuSampler,
    ) : DescriptorResource

    /**
     * Supplies a buffer range interpreted as formatted, integer-indexed texels.
     *
     * [format] describes each texel rather than a shader structure's member layout. The backend
     * establishes the formatted access representation during binding; this value itself allocates
     * neither new storage nor a native buffer view. Format support, texel-size divisibility, and
     * device-specific range alignment must be checked before the binding is used.
     */
    data class TexelBuffer(
        val view: GpuBufferView,
        val format: TextureFormat,
    ) : DescriptorResource
}

internal fun validateDescriptorResource(type: DescriptorType, resource: DescriptorResource, context: String) {
    when (type) {
        is DescriptorType.UniformBuffer -> validateUniformBuffer(type, resource, context)
        is DescriptorType.StorageBuffer -> validateStorageBuffer(type, resource, context)
        DescriptorType.Sampler -> validateSampler(resource, context)
        is DescriptorType.SampledTexture -> validateSampledTexture(type, resource, context)
        is DescriptorType.CombinedTextureSampler -> validateCombinedTextureSampler(type, resource, context)
        is DescriptorType.StorageTexture -> validateStorageTexture(type, resource, context)
        is DescriptorType.InputAttachment -> validateInputAttachment(type, resource, context)
        is DescriptorType.UniformTexelBuffer -> validateUniformTexelBuffer(resource, context)
        is DescriptorType.StorageTexelBuffer -> validateStorageTexelBuffer(type, resource, context)
    }
}

private fun validateUniformBuffer(type: DescriptorType.UniformBuffer, resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.Buffer) { "$context requires a buffer range" }
    validateBuffer(resource.view, BufferUsage.Uniform, type.minimumSizeBytes, context)
}

private fun validateStorageBuffer(type: DescriptorType.StorageBuffer, resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.Buffer) { "$context requires a buffer range" }
    validateBuffer(resource.view, BufferUsage.Storage, type.minimumSizeBytes, context)
}

private fun validateSampler(resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.Sampler) { "$context requires a sampler" }
}

private fun validateSampledTexture(type: DescriptorType.SampledTexture, resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.Texture) { "$context requires a texture view" }
    validateTexture(resource.view, TextureUsage.Sampled, type.dimension, type.multisampled, context)
}

private fun validateCombinedTextureSampler(type: DescriptorType.CombinedTextureSampler, resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.CombinedTextureSampler) { "$context requires a texture view and sampler together" }
    validateTexture(resource.view, TextureUsage.Sampled, type.dimension, false, context)
}

private fun validateStorageTexture(type: DescriptorType.StorageTexture, resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.Texture) { "$context requires a texture view" }
    validateTexture(resource.view, TextureUsage.Storage, type.dimension, type.multisampled, context)
    require(resource.view.format == type.format) { "$context requires storage format ${type.format}, but received ${resource.view.format}" }
}

private fun validateInputAttachment(type: DescriptorType.InputAttachment, resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.Texture) { "$context requires a texture view" }
    validateTexture(resource.view, TextureUsage.InputAttachment, null, type.multisampled, context)
}

private fun validateUniformTexelBuffer(resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.TexelBuffer) { "$context requires a formatted buffer range" }
    validateBuffer(resource.view, BufferUsage.UniformTexel, 0, context)
    require(resource.format.isColor) { "$context requires a color texel format" }
}

private fun validateStorageTexelBuffer(type: DescriptorType.StorageTexelBuffer, resource: DescriptorResource, context: String) {
    require(resource is DescriptorResource.TexelBuffer) { "$context requires a formatted buffer range" }
    validateBuffer(resource.view, BufferUsage.StorageTexel, 0, context)
    require(resource.format.isColor) { "$context requires a color texel format" }
    require(resource.format == type.format) { "$context requires storage format ${type.format}, but received ${resource.format}" }
}

private fun validateBuffer(view: GpuBufferView, usage: BufferUsage, minimumSizeBytes: Size, context: String) {
    require(usage in view.buffer.usage) { "$context requires BufferUsage.$usage" }
    require(view.sizeBytes > 0) { "$context requires a non-empty buffer range" }
    require(view.sizeBytes >= minimumSizeBytes) { "$context requires at least $minimumSizeBytes bytes, but received ${view.sizeBytes}" }
}

private fun validateTexture(view: GpuTextureView, usage: TextureUsage, dimension: TextureViewDimension?, multisampled: Boolean, context: String) {
    require(usage in view.texture.usage) { "$context requires TextureUsage.$usage" }
    require(dimension == null || view.dimension == dimension) { "$context requires a $dimension view, but received ${view.dimension}" }
    require((view.texture.sampleCount != SampleCount.One) == multisampled) { "$context requires ${if (multisampled) "multisampled" else "single-sampled"} image access" }

    // One shader image binding reads one value category, not a depth/stencil pair.
    val aspect = requireNotNull(view.aspects.singleOrNull()) { "$context requires exactly one image aspect" }
    val aspectExists = when (aspect) {
        TextureAspect.Color -> view.format.isColor
        TextureAspect.Depth -> view.format.hasDepth
        TextureAspect.Stencil -> view.format.hasStencil
    }
    require(aspectExists) { "$context selects an aspect absent from ${view.format}" }
}
