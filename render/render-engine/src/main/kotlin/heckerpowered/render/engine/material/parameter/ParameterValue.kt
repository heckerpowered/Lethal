/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.material.parameter

import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.texture.GpuTextureView
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.shader.binding.DescriptorResource
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Supplies one named shader parameter as numeric bytes or an existing GPU resource selection.
 *
 * Numeric values retain copied host bytes. Resource values retain references to live storage,
 * views, and samplers; they do not copy contents or extend resource lifetimes. The shader's
 * parameter interface determines the required representation and shader binding role.
 */
sealed interface ParameterValue

/**
 * Snapshots bytes already packed for a numeric shader parameter.
 *
 * Construction copies the array, and [bytes] returns another copy. No scalar type, matrix shape,
 * padding, or byte order is inferred; the producer must match the consuming interface's layout.
 * [floats] writes consecutive 32-bit components in native byte order without adding padding.
 */
class NumericParameterValue(bytes: ByteArray) : ParameterValue {
    private val data = bytes.copyOf()

    val sizeBytes: Int get() = data.size

    fun copyTo(destination: ByteBuffer) {
        destination.put(data)
    }

    fun bytes(): ByteArray = data.copyOf()

    companion object {
        fun floats(vararg values: Float): NumericParameterValue {
            val data = ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder())
            values.forEach(data::putFloat)
            return NumericParameterValue(data.array())
        }
    }
}

/** Supplies a GPU byte range whose uniform or storage role is selected by the input interface. */
data class BufferParameterValue(val view: GpuBufferView) : ParameterValue

/**
 * Pairs texture contents with sampling rules and their declared alpha representation.
 * The input interface may bind the image and sampler together or separately; [representation]
 * describes sampled color rather than converting the texture's stored bytes.
 */
data class TextureParameterValue(
    val view: GpuTextureView,
    val sampler: GpuSampler,
    val representation: AlphaRepresentation = AlphaRepresentation.Straight,
    val alphaQuantity: AlphaQuantity = AlphaQuantity.Coverage,
) : ParameterValue {
    private val hasImplicitOpaqueAlpha: Boolean get() = view.format.isColor && !view.format.colorComponents.alpha
    internal val sampledAlphaQuantity: AlphaQuantity get() = if (hasImplicitOpaqueAlpha) AlphaQuantity.Coverage else alphaQuantity
    internal val sampledRepresentation: AlphaRepresentation? get() = if (hasImplicitOpaqueAlpha || alphaQuantity == AlphaQuantity.Signal) null else representation
}

/**
 * Supplies an already formed RHI resource selection when the consuming parameter interface
 * accepts that form. Wrapping a resource does not bypass the interface's type checks.
 */
data class DescriptorParameterValue(val resource: DescriptorResource) : ParameterValue

/** Supplies a GPU byte range interpreted as formatted texels rather than a shader structure. */
data class TexelBufferParameterValue(
    val view: GpuBufferView,
    val format: TextureFormat,
) : ParameterValue
