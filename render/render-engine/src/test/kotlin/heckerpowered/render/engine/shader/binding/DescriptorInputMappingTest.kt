/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.terminateOnFailure
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.*
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.buffer.*
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.binding.*
import java.lang.reflect.Proxy
import kotlin.test.*

class DescriptorInputMappingTest {
    @Test
    fun numericValuesReceiveUniformStorageAndPendingUpload() {
        var allocations = 0
        var releases = 0
        val buffer = proxy<GpuBuffer> { property ->
            when (property) {
                "getSizeBytes" -> 4L
                "getUsage" -> setOf(BufferUsage.Uniform, BufferUsage.TransferDestination)
                "close" -> terminateOnFailure { releases++; Unit }
                else -> error("Unexpected buffer access: $property")
            }
        }
        val device = proxy<GraphicsDevice> { operation ->
            check(operation == "createBuffer")
            allocations++
            buffer
        }
        ResourceLifetime.build {
            try {
                val preparation = PassPreparation(device, this)
                val declaration = DescriptorParameter(ParameterName("value"), 0, numericSizeBytes = 4)
                val set = input(DescriptorType.UniformBuffer(4), declaration).resolve(values(NumericParameterValue.floats(1f)), preparation)
                assertSame(buffer, assertIs<DescriptorResource.Buffer>(set.bindings.single().resources.single()).view.buffer)
                assertSame(buffer, preparation.snapshot().single().destination.buffer)
                assertEquals(4, preparation.snapshot().single().bytes.sizeBytes)
                assertEquals(1, allocations)
            } finally { close() }
        }
        assertEquals(1, releases)
    }

    @Test
    fun residentBuffersAndFormattedTexelsUseTheirTargetRoles() = prepared { preparation ->
        val view = bufferView(16, setOf(BufferUsage.Uniform, BufferUsage.Storage, BufferUsage.UniformTexel, BufferUsage.StorageTexel))
        val buffer = BufferParameterValue(view)
        val texels = TexelBufferParameterValue(view, TextureFormat.R32Float)
        for (type in listOf(DescriptorType.UniformBuffer(16), DescriptorType.StorageBuffer(minimumSizeBytes = 16))) {
            val resource = input(type).resolve(values(buffer), preparation).bindings.single().resources.single()
            assertSame(view, assertIs<DescriptorResource.Buffer>(resource).view)
        }
        for (type in listOf(DescriptorType.UniformTexelBuffer(), DescriptorType.StorageTexelBuffer(TextureFormat.R32Float))) {
            val resource = input(type).resolve(values(texels), preparation).bindings.single().resources.single()
            assertSame(view, assertIs<DescriptorResource.TexelBuffer>(resource).view)
            assertEquals(TextureFormat.R32Float, resource.format)
        }
        assertTrue(preparation.snapshot().isEmpty())
    }

    @Test
    fun targetSelectsTheImageSamplerOrCombinedPair() = prepared { preparation ->
        val texture = textureValue()
        for (type in listOf(DescriptorType.SampledTexture(), DescriptorType.StorageTexture(texture.view.format), DescriptorType.InputAttachment())) {
            val resource = input(type).resolve(values(texture), preparation).bindings.single().resources.single()
            assertSame(texture.view, assertIs<DescriptorResource.Texture>(resource).view)
        }
        val combined = input(DescriptorType.CombinedTextureSampler()).resolve(values(texture), preparation).bindings.single().resources.single()
        assertSame(texture.view, assertIs<DescriptorResource.CombinedTextureSampler>(combined).view)
        assertSame(texture.sampler, combined.sampler)
        val sampler = input(DescriptorType.Sampler).resolve(values(texture), preparation).bindings.single().resources.single()
        assertSame(texture.sampler, assertIs<DescriptorResource.Sampler>(sampler).sampler)
    }

    @Test
    fun rawResourcesRemainLimitedToImageViewsWithoutAlphaRequirements() = prepared { preparation ->
        val texture = textureValue()
        val raw = DescriptorParameterValue(DescriptorResource.Texture(texture.view))
        assertSame(raw.resource, input(DescriptorType.SampledTexture()).resolve(values(raw), preparation).bindings.single().resources.single())
        val excluded = listOf(
            DescriptorType.UniformBuffer() to DescriptorResource.Buffer(bufferView(4, setOf(BufferUsage.Uniform))),
            DescriptorType.Sampler to DescriptorResource.Sampler(texture.sampler),
            DescriptorType.CombinedTextureSampler() to DescriptorResource.CombinedTextureSampler(texture.view, texture.sampler),
            DescriptorType.UniformTexelBuffer() to DescriptorResource.TexelBuffer(bufferView(4, setOf(BufferUsage.UniformTexel)), TextureFormat.R32Float),
        )
        for ([type, resource] in excluded) {
            assertFailsWith<IllegalArgumentException> { input(type).resolve(values(DescriptorParameterValue(resource)), preparation) }
        }
        val alpha = DescriptorParameter(ParameterName("value"), 0, alphaQuantity = AlphaQuantity.Coverage)
        assertFailsWith<IllegalArgumentException> { input(DescriptorType.SampledTexture(), alpha).resolve(values(raw), preparation) }
    }

    @Test
    fun invalidLaterValueIsRejectedBeforeAnyUniformAllocation() = prepared { preparation ->
        val layout = DescriptorSetLayout(listOf(
            DescriptorBindingLayout(0, DescriptorType.UniformBuffer(4), setOf(ShaderStage.Fragment)),
            DescriptorBindingLayout(1, DescriptorType.StorageBuffer(), setOf(ShaderStage.Fragment)),
        ))
        val input = DescriptorInputMapping(layout, listOf(
            DescriptorParameter(ParameterName("uniform"), 0, numericSizeBytes = 4),
            DescriptorParameter(ParameterName("storage"), 1),
        ))
        val bytes = NumericParameterValue.floats(1f)
        assertFailsWith<IllegalArgumentException> {
            input.resolve(ParameterValues().replacing("uniform", bytes).replacing("storage", bytes), preparation)
        }
        assertTrue(preparation.snapshot().isEmpty())
    }

    @Test
    fun numericSizesAndAlphaAssociationRemainIndependentRequirements() = prepared { preparation ->
        val bytes = NumericParameterValue.floats(1f)
        assertFailsWith<IllegalArgumentException> { input(DescriptorType.UniformBuffer()).resolve(values(bytes), preparation) }
        val exact = DescriptorParameter(ParameterName("value"), 0, numericSizeBytes = 8)
        assertFailsWith<IllegalArgumentException> { input(DescriptorType.UniformBuffer(), exact).resolve(values(bytes), preparation) }
        val tooSmall = DescriptorParameter(ParameterName("value"), 0, numericSizeBytes = 4)
        assertFailsWith<IllegalArgumentException> { input(DescriptorType.UniformBuffer(8), tooSmall).resolve(values(bytes), preparation) }
        assertFailsWith<IllegalArgumentException> { input(DescriptorType.StorageBuffer(), exact) }
        assertFailsWith<IllegalArgumentException> { input(DescriptorType.UniformBuffer(), DescriptorParameter(ParameterName("value"), 0, numericSizeBytes = 0)) }
        val alpha = DescriptorParameter(ParameterName("value"), 0, alphaQuantity = AlphaQuantity.Coverage, representation = AlphaRepresentation.Premultiplied)
        val contract = input(DescriptorType.CombinedTextureSampler(), alpha)
        assertFailsWith<IllegalArgumentException> { contract.resolve(values(textureValue()), preparation) }
        assertFailsWith<IllegalArgumentException> { contract.resolve(values(textureValue().copy(alphaQuantity = AlphaQuantity.Signal)), preparation) }
        contract.resolve(values(textureValue().copy(representation = AlphaRepresentation.Premultiplied)), preparation)
    }

    private fun input(type: DescriptorType, parameter: DescriptorParameter = DescriptorParameter(ParameterName("value"), 0)) =
        DescriptorInputMapping(DescriptorSetLayout(listOf(DescriptorBindingLayout(0, type, setOf(ShaderStage.Fragment)))), listOf(parameter))

    private fun values(value: ParameterValue) = ParameterValues().replacing("value", value)

    private fun prepared(action: (PassPreparation) -> Unit) = ResourceLifetime.build {
        try {
            action(PassPreparation(proxy<GraphicsDevice> { error("Rejected or resident values must not access the device") }, this))
        } finally { close() }
    }

    private fun bufferView(size: Long, usage: Set<BufferUsage>) = GpuBufferView(proxy<GpuBuffer> { property ->
        when (property) {
            "getSizeBytes" -> size
            "getUsage" -> usage
            else -> error("Unexpected buffer access: $property")
        }
    }, 0, size)

    private fun textureValue(): TextureParameterValue {
        val texture = proxy<GpuTexture> { property ->
            when (property) {
                "getUsage" -> setOf(TextureUsage.Sampled, TextureUsage.Storage, TextureUsage.InputAttachment)
                "getSampleCount" -> SampleCount.One
                "getFormat" -> TextureFormat.Rgba8UnsignedNormalized
                else -> error("Unexpected texture access: $property")
            }
        }
        val view = proxy<GpuTextureView> { property ->
            when (property) {
                "getTexture" -> texture
                "getDimension" -> TextureViewDimension.TwoDimensional
                "getFormat" -> texture.format
                "getAspects" -> setOf(TextureAspect.Color)
                else -> error("Unexpected view access: $property")
            }
        }
        return TextureParameterValue(view, proxy<GpuSampler> { error("Sampler must remain a resource reference") })
    }

    private inline fun <reified T> proxy(crossinline invoke: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> invoke(method.name.substringBefore('-')) } as T
}
