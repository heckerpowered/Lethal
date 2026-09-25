/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.binding

import heckerpowered.render.memory.Size
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.resource.texture.TextureViewDimension

/**
 * Describes the kind of resource one shader binding expects, rather than a resource to bind.
 *
 * A camera slot can require a uniform-buffer range while a material slot requires a sampled
 * two-dimensional texture. Changing the material's image changes the bound resource, not the
 * slot declaration. Different resource kinds carry different requirements, so a texture slot
 * has a view dimension while a buffer slot has a byte-range requirement.
 *
 * The declaration does not grant resource usage. Actual resources must have the required usage,
 * shape, and format, and satisfy shader declarations and device limits. The binding count and
 * stage visibility are specified separately by [DescriptorBindingLayout].
 */
sealed interface DescriptorType {
    /**
     * Reads structured parameters through a uniform-buffer binding, such as camera matrices.
     *
     * The bound buffer must allow Uniform usage. [minimumSizeBytes] is a lower bound on the
     * exposed range, not a declaration of member offsets or padding. Zero adds no layout-level
     * minimum; it does not exempt a binding from the size required by the shader that uses it.
     */
    data class UniformBuffer(val minimumSizeBytes: Size = 0) : DescriptorType {
        init {
            require(minimumSizeBytes >= 0) { "Uniform buffer minimum size must be non-negative" }
        }
    }

    /**
     * Accesses a buffer range as shader storage, for example an indexed table of object data.
     *
     * The bound buffer must allow Storage usage. [access] restricts operations through this slot;
     * it does not change other bindings of the buffer. [minimumSizeBytes] has the same range-only
     * meaning as for [UniformBuffer]. Shader declarations still determine the data layout.
     */
    data class StorageBuffer(
        val access: StorageAccess = StorageAccess.ReadWrite,
        val minimumSizeBytes: Size = 0,
    ) : DescriptorType {
        init {
            require(minimumSizeBytes >= 0) { "Storage buffer minimum size must be non-negative" }
        }
    }

    /**
     * Supplies sampling rules separately from image storage.
     *
     * Separate slots allow several images to share a sampler, or one image to be paired with
     * different samplers. Filtering and comparison compatibility are checked with the image
     * and shader operation that actually use the sampler.
     */
    data object Sampler : DescriptorType

    /**
     * Reads a texture view through the shader's sampled-image interface.
     *
     * [dimension] describes the view, not just the source texture's storage dimension. A cube
     * slot cannot be filled with an ordinary two-dimensional array merely because it has six
     * layers. The source texture must allow Sampled usage.
     *
     * [multisampled] requires explicit per-sample access when true. It distinguishes ordinary
     * and multisampled shader interfaces, not an exact sample count; a compatible shader may
     * handle different supported counts. Multisampled access is not sampler-based filtering.
     */
    data class SampledTexture(
        val dimension: TextureViewDimension = TextureViewDimension.TwoDimensional,
        val sampleType: TextureSampleType = TextureSampleType.Float,
        val multisampled: Boolean = false,
    ) : DescriptorType {
        init {
            validateMultisampleDimension(dimension, multisampled)
        }
    }

    /**
     * Supplies a single-sampled texture view and its sampler together at one binding number.
     *
     * This directly describes a combined shader resource such as a GLSL sampler2D. It does not
     * make the sampler part of the texture's storage. Use separate [SampledTexture] and [Sampler]
     * slots when the shader interface declares them separately.
     */
    data class CombinedTextureSampler(
        val dimension: TextureViewDimension = TextureViewDimension.TwoDimensional,
        val sampleType: TextureSampleType = TextureSampleType.Float,
    ) : DescriptorType

    /**
     * Reads or writes formatted image values through a storage-image binding, without filtering.
     *
     * For example, a shader can write intermediate image values at explicitly chosen texel
     * coordinates. The texture must allow Storage usage, and the view format must match [format].
     * [access] limits this binding; supporting a format for sampling does not imply storage support.
     * The device checks the complete format, dimension, access, and multisample combination.
     */
    data class StorageTexture(
        val format: TextureFormat,
        val dimension: TextureViewDimension = TextureViewDimension.TwoDimensional,
        val access: StorageAccess = StorageAccess.ReadWrite,
        val multisampled: Boolean = false,
    ) : DescriptorType {
        init {
            validateMultisampleDimension(dimension, multisampled)
        }
    }

    /**
     * Reads an attachment value at the fragment's current framebuffer position.
     *
     * This can let a lighting stage consume local attachment data without arbitrary-coordinate
     * texture sampling. It requires InputAttachment usage and Fragment visibility. The shader's
     * attachment-input index and the render-pass mapping are checked when the pipeline is used;
     * the descriptor binding number is not a color-attachment index.
     */
    data class InputAttachment(
        val sampleType: TextureSampleType = TextureSampleType.Float,
        val multisampled: Boolean = false,
    ) : DescriptorType

    /**
     * Fetches formatted texels from a buffer by integer index.
     *
     * The resource must allow UniformTexel usage and provide a formatted buffer view. This is
     * not a uniform-buffer structure and has neither mip levels nor sampler-based filtering.
     */
    data class UniformTexelBuffer(
        val sampleType: TextureSampleType = TextureSampleType.Float,
    ) : DescriptorType

    /**
     * Accesses formatted buffer texels as shader storage.
     *
     * The resource must allow StorageTexel usage and provide a buffer view matching [format].
     * [access] restricts reads and writes through the binding; atomics need additional support.
     */
    data class StorageTexelBuffer(
        val format: TextureFormat,
        val access: StorageAccess = StorageAccess.ReadWrite,
    ) : DescriptorType
}

private fun validateMultisampleDimension(dimension: TextureViewDimension, multisampled: Boolean) {
    require(
        !multisampled || dimension == TextureViewDimension.TwoDimensional ||
                dimension == TextureViewDimension.TwoDimensionalArray
    ) {
        "Multisampled bindings require a two-dimensional or two-dimensional-array view"
    }
}
