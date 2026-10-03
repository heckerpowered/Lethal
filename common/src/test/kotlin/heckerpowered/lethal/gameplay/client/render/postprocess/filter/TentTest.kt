/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render.postprocess.filter

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.geometry.BuiltInPrimitives
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.shader.ShaderStages
import heckerpowered.render.shader.primitive.PrimitiveShader
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TentTest {
    @Test
    fun releasesOwnedResourcesOnceWithoutClosingBorrowedResources() {
        val closed = mutableListOf<String>()
        val device = device(closed, failSampler = false)
        val tent = context(device) { Tent.create(resource<ShaderStages>("shaders", closed)) }

        tent.close()
        tent.close()

        assertEquals(listOf("layout"), closed)
    }

    @Test
    fun releasesLayoutWhenSamplerResolutionFails() {
        val closed = mutableListOf<String>()
        val device = device(closed, failSampler = true)

        assertFailsWith<UnsupportedOperationException> {
            context(device) { Tent.create(resource<ShaderStages>("shaders", closed)) }
        }

        assertEquals(listOf("layout"), closed)
    }

    private fun device(closed: MutableList<String>, failSampler: Boolean): GraphicsDevice {
        val quad = object : GpuBuffer {
            override val sizeBytes = 32L
            override val usage = setOf(BufferUsage.Vertex)
            override fun close() = terminateOnFailure { closed.add("quad"); Unit }
        }
        val primitives = object : BuiltInPrimitives {
            override val unitQuad = quad
            override fun get(shader: PrimitiveShader): ShaderStages = error("Unexpected primitive shader request: $shader")
        }
        return Proxy.newProxyInstance(GraphicsDevice::class.java.classLoader, arrayOf(GraphicsDevice::class.java)) { _, method, _ ->
            when (method.name) {
                "getPrimitives" -> primitives
                "createPipelineLayout" -> resource<PipelineLayout>("layout", closed)
                "resolveSampler" -> {
                    if (failSampler) throw UnsupportedOperationException("Sampler unsupported")
                    resource<GpuSampler>("sampler", closed)
                }
                else -> error("Unexpected device operation: ${method.name}")
            }
        } as GraphicsDevice
    }

    private inline fun <reified T> resource(name: String, closed: MutableList<String>): T {
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            check(method.name == "close") { "Unexpected resource operation: ${method.name}" }
            terminateOnFailure { closed.add(name) }
            null
        } as T
    }
}
