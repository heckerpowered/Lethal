/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.image.ImageSize
import heckerpowered.render.engine.image.RenderImage
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.GpuTextureView
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderStage
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScreenShaderFactoryTest {
    @Test
    fun typedBindingsRetainBorrowedImagesAndEncodeTheirOwnValues() {
        for (quantity in AlphaQuantity.entries) {
            val source = image(quantity)
            val copy = copyImageShader().bind(source).shading
            val tent = tentShader().bind(source).shading
            val brightness = brightnessShader(quantity).bind(BrightnessInput(source, .625f)).shading
            for (shading in listOf(copy, tent, brightness)) {
                val sampled = assertIs<TextureParameterValue>(shading.parameters.require(ParameterName("source")))
                assertSame(source.view, sampled.view)
                assertSame(source.sampler, sampled.sampler)
                assertEquals(quantity, sampled.alphaQuantity)
                val geometry = assertIs<ShaderGeometry>(shading.geometry)
                assertEquals(PrimitiveTopology.TriangleList, geometry.primitive.topology)
                val selection = assertIs<GeometrySelection.Vertices>(geometry.selection)
                assertEquals(3, selection.range.vertexCount)
            }
            assertEquals(setOf(ParameterName("source")), copy.parameters.values.keys)
            assertContentEquals(NumericParameterValue.floats(.125f, .25f).bytes(), tent.parameters.requireNumeric(ParameterName("texelSize")).bytes())
            assertContentEquals(NumericParameterValue.floats(.625f).bytes(), brightness.parameters.requireNumeric(ParameterName("threshold")).bytes())
        }
    }

    @Test
    fun sampledOutputsInheritSourceAlphaAndBrightnessOutputsDeclareTheirSelectedQuantity() {
        for (shader in listOf(copyImageShader(), tentShader(), brightnessShader(), brightnessShader(AlphaQuantity.Signal))) {
            assertEquals(AlphaRepresentation.Premultiplied, shader.sourceRepresentation)
            assertTrue(shader.replaySafe)
            assertEquals(setOf(0), shader.outputs.keys)
            assertEquals(AlphaRepresentation.Premultiplied, shader.outputs.getValue(0).representation)
        }
        for (shader in listOf(copyImageShader(), tentShader())) {
            assertEquals(ParameterName("source"), shader.outputs.getValue(0).alphaFromTexture)
        }
        assertEquals(AlphaQuantity.Coverage, brightnessShader().outputs.getValue(0).alphaQuantity)
        for (quantity in AlphaQuantity.entries) {
            val output = brightnessShader(quantity).outputs.getValue(0)
            assertEquals(quantity, output.alphaQuantity)
            assertNull(output.alphaFromTexture)
        }
    }

    @Test
    fun brightnessKeepsItsCoverageDefaultAndRejectsMismatchedInputs() {
        val coverage = brightnessShader()
        val signal = brightnessShader(AlphaQuantity.Signal)
        coverage.bind(BrightnessInput(image(AlphaQuantity.Coverage), .5f))
        signal.bind(BrightnessInput(image(AlphaQuantity.Signal), .5f))
        val coverageFailure = assertFailsWith<IllegalArgumentException> { coverage.bind(BrightnessInput(image(AlphaQuantity.Signal), .5f)) }
        val signalFailure = assertFailsWith<IllegalArgumentException> { signal.bind(BrightnessInput(image(AlphaQuantity.Coverage), .5f)) }
        assertEquals("Brightness extraction requires coverage input", coverageFailure.message)
        assertEquals("Brightness extraction requires signal input", signalFailure.message)
        for (quantity in AlphaQuantity.entries) {
            val shader = brightnessShader(quantity)
            for (threshold in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                assertFailsWith<IllegalArgumentException> { shader.bind(BrightnessInput(image(quantity), threshold)) }
            }
        }
    }

    @Test
    fun eachEffectDeclaresItsOwnResourcesAndUsesReflectedDescriptorAndPushLocations() {
        val paths = mutableListOf<String>()
        val shaders = listOf(copyImageShader(), tentShader(), brightnessShader(), brightnessShader(AlphaQuantity.Signal))
        val device = proxy(GraphicsDevice::class.java) { operation, arguments ->
            check(operation == "compileCanonicalShader")
            paths += (arguments[0] as ShaderModuleDescription).label
            fixtureScreenPreparation(arguments[0] as ShaderModuleDescription, arguments[1] as String, binding = 7, pushOffset = 16)
        }
        for (shader in shaders) {
            paths.clear()
            val definition = ResourceLifetime.build { this }.use { ShaderRealizations(device, it).definition(shader) }
            assertEquals(listOf("/assets/render-engine/shaders/fullscreen.vert", "/assets/render-engine/shaders/${shader.label}.frag"), paths)
            assertEquals(listOf(ShaderStage.Vertex, ShaderStage.Fragment), definition.modules.map { it.stage })
            assertTrue(definition.inputs.vertices.inputs.isEmpty())
            val descriptor = definition.inputs.descriptors.single().parameters.single()
            assertEquals(ParameterName("source"), descriptor.name)
            assertEquals(7, descriptor.binding)
            when (shader.label) {
                "copy" -> assertTrue(definition.inputs.pushes.isEmpty())
                "tent", "brightness" -> {
                    val field = definition.inputs.pushes.single().fields.single()
                    assertEquals(ParameterName(if (shader.label == "tent") "texelSize" else "threshold"), field.name)
                    assertEquals(16, field.offsetBytes)
                    assertEquals(if (shader.label == "tent") 8 else 4, field.sizeBytes)
                }
            }
        }
    }

    private fun image(quantity: AlphaQuantity): RenderImage {
        val view = proxy(GpuTextureView::class.java) { operation, _ ->
            check(operation == "getFormat")
            TextureFormat.Rgba16Float
        }
        val attachment = proxy(RenderAttachment::class.java) { operation, _ -> error(operation) }
        val sampler = proxy(GpuSampler::class.java) { operation, _ -> error(operation) }
        return RenderImage(view, attachment, ImageSize(8, 4), sampler, quantity)
    }

    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments ->
            invoke(method.name.substringBefore('-'), arguments ?: emptyArray())
        },
    )
}
