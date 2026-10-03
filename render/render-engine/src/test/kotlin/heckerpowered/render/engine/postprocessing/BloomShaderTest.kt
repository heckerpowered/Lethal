/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.postprocessing

import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.engine.shader.program.fixtureScreenPreparation
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.image.ImageSize
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.shader.program.brightnessShader
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.scene.geometryElement
import heckerpowered.render.engine.stage.RenderStageOperation
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

class BloomShaderTest {
    @Test
    fun defaultEnginePreparesHeldScreenShadersWithCoarseCopyAndFineTentAddition() {
        var created = 0
        var closed = 0
        fun <T> owned(type: Class<T>): T {
            created++
            return proxy(type) { operation, _ ->
                check(operation == "close")
                terminateOnFailure { closed++; Unit }
            }
        }
        val device = proxy(GraphicsDevice::class.java) { operation, arguments ->
            when (operation) {
                "compileCanonicalShader" -> fixtureScreenPreparation(arguments[0] as ShaderModuleDescription, arguments[1] as String)
                "createShaderModule" -> owned(ShaderModule::class.java)
                "createShaderStages" -> owned(ShaderStages::class.java)
                "createPipelineLayout" -> owned(PipelineLayout::class.java)
                "createTexture" -> {
                    val description = arguments[0] as TextureDescription
                    created++
                    proxy(GpuTexture::class.java) { property, _ ->
                        when (property) {
                            "getUsage" -> description.usage
                            "getDimension" -> description.dimension
                            "getSampleCount" -> description.sampleCount
                            "getFormat" -> description.format
                            "getWidth" -> description.width
                            "getHeight" -> description.height
                            "getDepth", "getMipLevelCount", "getArrayLayerCount" -> 1
                            "close" -> terminateOnFailure { closed++; Unit }
                            else -> error("Unexpected texture access: $property")
                        }
                    }
                }
                "createTextureView" -> {
                    val texture = arguments[0] as GpuTexture
                    proxy(GpuTextureView::class.java) { property, _ ->
                        when (property) {
                            "getTexture" -> texture
                            "getDimension" -> TextureViewDimension.TwoDimensional
                            "getFormat" -> texture.format
                            "getWidth" -> texture.width
                            "getHeight" -> texture.height
                            "getArrayLayerCount" -> 1
                            "getBaseMipLevel", "getBaseArrayLayer" -> 0
                            "getMipLevelCount", "getDepth" -> 1
                            "getAspects" -> setOf(TextureAspect.Color)
                            else -> error("Unexpected view access: $property")
                        }
                    }
                }
                "createAttachmentView" -> {
                    val view = arguments[0] as GpuTextureView
                    proxy(RenderAttachment::class.java) { property, _ ->
                        when (property) {
                            "getWidth" -> view.width
                            "getHeight" -> view.height
                            "getArrayLayerCount" -> 1
                            "getFormat" -> view.format
                            "getSampleCount" -> SampleCount.One
                            "getAspects" -> view.aspects
                            else -> error("Unexpected attachment access: $property")
                        }
                    }
                }
                "createSampler" -> proxy(GpuSampler::class.java) { operation, _ ->
                    check(operation == "close")
                    terminateOnFailure { Unit }
                }
                "awaitIdle" -> Unit
                "encode" -> Unit
                else -> error("Unexpected device operation: $operation")
            }
        }
        val engine = RenderEngine.create(device)
        try {
            val source = engine.images.image("source", ImageSize(8, 8), TextureFormat.Rgba16Float)
            val createdBeforeBloom = created
            val bloom = Bloom(engine.images, TextureFormat.Rgba16Float)
            assertEquals(createdBeforeBloom, created)
            engine.stage {
                val result = bloom.render(this, source, .5f, "test")
                assertEquals(source.size, result.size)
                val passes = build().operations.map { (it as RenderStageOperation.Raster).pass }
                val elements = passes.map { it.collection.submissions.single().geometryElement }
                assertEquals(listOf(
                    "brightness",
                    "tent",
                    "tent",
                    "copy",
                    "copy",
                    "tent",
                    "copy",
                    "tent"
                ), elements.map { it.shading.shader.label })
                assertEquals(listOf(8, 4, 2, 2, 4, 4, 8, 8), passes.map { it.description.renderArea.width })
                assertEquals(listOf(
                    CompositingMode.Replace,
                    CompositingMode.Replace,
                    CompositingMode.Replace,
                    CompositingMode.Replace,
                    CompositingMode.Replace,
                    CompositingMode.Add,
                    CompositingMode.Replace,
                    CompositingMode.Add
                ), elements.map { it.composition })
                bloom.render(this, source, .5f, "test")
                val repeated = build().operations.drop(passes.size).map { (it as RenderStageOperation.Raster).pass.collection.submissions.single().geometryElement }
                for ((first, second) in elements.zip(repeated)) assertSame(first.shading.shader, second.shading.shader)
            }
        } finally { engine.close() }
        assertEquals(created, closed)
    }

    @Test
    fun bothBrightnessEntrypointsResolveDeclaredResourcesInEachEngine() {
        for (quantity in AlphaQuantity.entries) {
            val shader = if (quantity == AlphaQuantity.Coverage) brightnessShader() else brightnessShader(quantity)
            val descriptions = mutableListOf<ShaderModuleDescription>()
            fun device() = proxy(GraphicsDevice::class.java) { operation, arguments ->
                check(operation == "compileCanonicalShader")
                val description = arguments[0] as ShaderModuleDescription
                descriptions += description
                fixtureScreenPreparation(description, arguments[1] as String)
            }
            ResourceLifetime.build { this }.use { ShaderRealizations(device(), it).definition(shader) }
            ResourceLifetime.build { this }.use { ShaderRealizations(device(), it).definition(shader) }
            assertEquals(listOf("/assets/render-engine/shaders/fullscreen.vert", "/assets/render-engine/shaders/brightness.frag"), descriptions.take(2).map { it.label })
            for (index in 0..1) assertEquals(descriptions[index], descriptions[index + 2])
            assertEquals(quantity, shader.outputs.getValue(0).alphaQuantity)
            assertEquals(AlphaRepresentation.Premultiplied, shader.outputs.getValue(0).representation)
        }
    }

    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, arguments ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === arguments?.get(0)
                "toString" -> type.simpleName
                else -> invoke(method.name.substringBefore('-'), arguments ?: emptyArray())
            }
        },
    )
}
