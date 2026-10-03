/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.shader.program

import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.color.Color
import heckerpowered.render.engine.geometry.triangle
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.texture.GpuTextureView
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.*
import java.lang.reflect.Proxy
import kotlin.test.*

class SurfaceShaderFactoryTest {
    private val geometry = triangle(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f))

    @Test
    fun surfaceBindingsKeepGeometryAndBorrowedTextureWhileEncodingColor() {
        val color = Color(.25f, .5f, .75f, .625f)
        val expected = NumericParameterValue.floats(color.red, color.green, color.blue, color.alpha).bytes()
        val unlit = unlitShader().bind(SurfaceColor(geometry, color)).shading
        assertSame(geometry, unlit.geometry)
        assertContentEquals(expected, unlit.parameters.requireNumeric(ParameterName("color")).bytes())
        assertEquals(setOf(ParameterName("color")), unlit.parameters.values.keys)
        for ((shader, representation) in texturedFactories()) {
            val texture = texture(representation)
            val shading = shader.bind(TexturedColor(geometry, texture, color)).shading
            assertSame(geometry, shading.geometry)
            assertSame(texture, shading.parameters.require(ParameterName("texture")))
            assertContentEquals(expected, shading.parameters.requireNumeric(ParameterName("color")).bytes())
            assertEquals(setOf(ParameterName("texture"), ParameterName("color")), shading.parameters.values.keys)
        }
    }

    @Test
    fun surfacePreparationKeepsResourceChoicesAndIndependentInputAndOutputContracts() {
        val paths = mutableListOf<String>()
        val device = proxy(GraphicsDevice::class.java) { operation, arguments ->
            check(operation == "compileCanonicalShader")
            val description = arguments[0] as ShaderModuleDescription
            paths += description.label
            fixtureSurfacePreparation(description, arguments[1] as String)
        }
        val unlit = unlitShader()
        val unlitInputs = ResourceLifetime.build { this }.use { ShaderRealizations(device, it).definition(unlit) }.inputs
        assertEquals(listOf("surface.vert", "unlit.frag"), paths.map { it.substringAfterLast('/') })
        assertTrue(unlitInputs.descriptors.isEmpty())
        assertEquals(1, unlitInputs.vertices.inputs.size)
        assertEquals(AlphaRepresentation.Straight, unlit.sourceRepresentation)
        assertEquals(FragmentOutput(), unlit.outputs.getValue(0))
        assertTrue(unlit.replaySafe)
        for ((shader, representation) in texturedFactories()) {
            paths.clear()
            val inputs = ResourceLifetime.build { this }.use { ShaderRealizations(device, it).definition(shader) }.inputs
            assertEquals(listOf("/assets/render-engine/shaders/textured.vert", "/assets/render-engine/shaders/${shader.label}.frag"), paths)
            assertEquals(2, inputs.vertices.inputs.size)
            val sampled = inputs.descriptors.single().parameters.single()
            assertEquals(ParameterName("texture"), sampled.name)
            assertEquals(11, sampled.binding)
            assertEquals(representation, sampled.representation)
            assertEquals(AlphaQuantity.Coverage, sampled.alphaQuantity)
            assertEquals(representation, shader.sourceRepresentation)
            assertEquals(FragmentOutput(representation, AlphaQuantity.Coverage), shader.outputs.getValue(0))
            assertTrue(shader.replaySafe)
        }
    }

    @Test
    fun eachSurfaceEncoderRejectsInvalidOpacity() {
        for (alpha in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.1f, 1.1f)) {
            val color = Color(1f, 1f, 1f, alpha)
            assertFailsWith<IllegalArgumentException> { unlitShader().bind(SurfaceColor(geometry, color)) }
            for ((shader, representation) in texturedFactories()) {
                assertFailsWith<IllegalArgumentException> { shader.bind(TexturedColor(geometry, texture(representation), color)) }
            }
        }
    }

    private fun texturedFactories() = listOf(
        texturedStraightShader() to AlphaRepresentation.Straight,
        texturedPremultipliedShader() to AlphaRepresentation.Premultiplied,
    )

    private fun texture(representation: AlphaRepresentation) = TextureParameterValue(
        proxy(GpuTextureView::class.java) { operation, _ -> error(operation) },
        proxy(GpuSampler::class.java) { operation, _ -> error(operation) },
        representation,
    )

    private fun fixtureSurfacePreparation(description: ShaderModuleDescription, origin: String): ShaderCompilation {
        fun vector(components: Int) = ShaderValueDescription(ShaderScalarKind.Float, 32, components, 1)
        val matrix = ShaderValueDescription(ShaderScalarKind.Float, 32, 4, 4)
        val members = listOf(
            ShaderInterfaceBlockMember("clipFromLocal", 0, 64, matrix, matrixStrideBytes = 16, arrayStrideBytes = 0, rowMajor = false),
            ShaderInterfaceBlockMember("color", 64, 16, vector(4), 0, 0, false),
        )
        val push = ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), 80, "Params", members, null)
        val coordinates = ShaderInterfaceVariable("coordinates", 0, vector(2))
        val textured = origin.substringAfterLast('/') !in setOf("surface.vert", "unlit.frag")
        val facts = if (description.stage == ShaderStage.Vertex) {
            val attributes = listOfNotNull(ShaderInterfaceVariable("position", 0, vector(3)), if (textured) ShaderInterfaceVariable("uv", 1, vector(2)) else null)
            ShaderInterfaceDescription(attributes, if (textured) listOf(coordinates) else emptyList(), listOf(push))
        } else {
            val sampler = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 11, emptyList(), null, null, emptyList(), ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, vector(1)))
            ShaderInterfaceDescription(if (textured) listOf(coordinates) else emptyList(), listOf(ShaderInterfaceVariable("result", 0, vector(4))), if (textured) listOf(push, sampler) else listOf(push))
        }
        return ShaderCompilation(description.copy(code = ShaderSource(ShaderLanguage.Glsl, "native fixture code", description.label)), facts)
    }

    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments -> invoke(method.name.substringBefore('-'), arguments ?: emptyArray()) },
    )
}
