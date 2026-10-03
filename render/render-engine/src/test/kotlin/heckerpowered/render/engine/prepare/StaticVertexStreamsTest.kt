/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.prepare

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Matrices
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.engine.image.RenderImageStore
import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.engine.pass.RasterPassProcessor
import heckerpowered.render.engine.pass.RenderElementPassProcessors
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderElement
import heckerpowered.render.engine.scene.drawing.WorldDrawing
import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.engine.stage.RenderStageExecutor
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.lifetime
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.test.*

class StaticVertexStreamsTest {
    @Test
    fun sourceCopiesCallerBytesAndDistinctIdentitiesRemainIndependent() = withFixture {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val source = VertexStreamSource.Static(bytes)
        bytes.fill(9)
        val captured = ByteBuffer.allocate(4).also(source.bytes::copyTo).array()
        assertContentEquals(byteArrayOf(1, 2, 3, 4), captured)
        stage(source, VertexStreamSource.Static(captured))
        assertEquals(2, created)
        assertEquals(2, uploads)
    }

    @Test
    fun severalPassesAndLaterStagesShareOneImmutableAllocationAndUpload() = withFixture {
        val source = VertexStreamSource.Static(byteArrayOf(1, 2, 3, 4))
        stage(source, source)
        stage(source)
        assertEquals(1, created)
        assertEquals(1, uploads)
        assertEquals(0, closed)
    }

    @Test
    fun sameCpuSourceInDifferentEnginesUsesIndependentGpuResources() {
        val source = VertexStreamSource.Static(byteArrayOf(1, 2, 3, 4))
        withFixture {
            stage(source)
            withFixture { stage(source); assertEquals(1, created); assertEquals(1, uploads) }
            stage(source)
            assertEquals(1, created)
            assertEquals(1, uploads)
        }
    }

    @Test
    fun failedAllocationDoesNotBecomeACacheHit() = withFixture {
        val source = VertexStreamSource.Static(byteArrayOf(1, 2, 3, 4))
        failAllocation = true
        assertFailsWith<IllegalStateException> { stage(source) }
        failAllocation = false
        stage(source)
        assertEquals(2, allocationAttempts)
        assertEquals(1, created)
        assertEquals(1, uploads)
    }

    @Test
    fun failedPreparationKeepsAllocatedStorageUninitialized() = withFixture {
        val source = VertexStreamSource.Static(byteArrayOf(1, 2, 3, 4))
        failPreparation = true
        assertFailsWith<IllegalStateException> { stage(source) }
        assertEquals(1, created)
        assertEquals(0, uploads)
        failPreparation = false
        stage(source)
        assertEquals(1, created)
        assertEquals(1, uploads)
    }

    @Test
    fun failedRecordingAfterUploadRequiresTheSameBytesToBeUploadedAgain() = withFixture {
        val source = VertexStreamSource.Static(byteArrayOf(1, 2, 3, 4))
        failRecording = true
        assertFailsWith<IllegalStateException> { stage(source) }
        failRecording = false
        stage(source)
        stage(source)
        assertEquals(1, created)
        assertEquals(2, uploads)
    }

    @Test
    fun failedCompletionBlocksReuseAndRecoveryDoesNotAssumeUploadSucceeded() = withFixture {
        val source = VertexStreamSource.Static(byteArrayOf(1, 2, 3, 4))
        failCompletion = true
        assertFailsWith<IllegalStateException> { stage(source) }
        assertFailsWith<IllegalStateException> { stage(source) }
        assertEquals(1, uploads)
        assertEquals(0, closed)
        failCompletion = false
        engine.awaitIdle()
        stage(source)
        stage(source)
        assertEquals(1, created)
        assertEquals(2, uploads)
    }

    @Test
    fun existingUploadStillUsesTemporaryStorageForEveryPass() = withFixture {
        val source = VertexStreamSource.Upload(byteArrayOf(1, 2, 3, 4))
        stage(source, source)
        stage(source)
        assertEquals(3, created)
        assertEquals(3, uploads)
        assertEquals(3, closed)
    }

    @Test
    fun consumedEmptyStaticSourceIsRejectedBeforeAllocation() = withFixture {
        assertFailsWith<IllegalArgumentException> { stage(VertexStreamSource.Static(byteArrayOf())) }
        assertEquals(0, created)
    }

    @Test
    fun engineClosureReleasesStaticStorageOnceAndRejectsLaterUse() = withFixture {
        val source = VertexStreamSource.Static(byteArrayOf(1, 2, 3, 4))
        stage(source)
        engine.close()
        engine.close()
        assertEquals(1, closed)
        assertFailsWith<IllegalStateException> { stage(source) }
    }

    private class StreamElement(val source: VertexStreamSource) : RenderElement

    private class Fixture {
        var created = 0
        var closed = 0
        var uploads = 0
        var allocationAttempts = 0
        var failAllocation = false
        var failPreparation = false
        var failRecording = false
        var failCompletion = false
        private val stack = MemoryStack(128)
        private val pass = proxy<RenderPass> { operation, _ -> error("Unexpected pass operation $operation") }
        private val encoder = proxy<CommandEncoder> { operation, arguments ->
            when (operation) {
                "getMemoryStack" -> stack
                "writeBuffer" -> { uploads++; Unit }
                "renderPass" -> {
                    @Suppress("UNCHECKED_CAST")
                    val body = arguments.last() as RenderPass.() -> Unit
                    body(pass)
                }
                else -> error("Unexpected encoder operation $operation")
            }
        }
        private val device = proxy<GraphicsDevice> { operation, arguments ->
            when (operation) {
                "createBuffer" -> {
                    allocationAttempts++
                    check(!failAllocation) { "Allocation rejected" }
                    val description = arguments[0] as BufferDescription
                    created++
                    proxy<GpuBuffer> { property, _ ->
                        when (property) {
                            "getSizeBytes" -> description.sizeBytes
                            "getUsage" -> description.usage
                            "close" -> terminateOnFailure { closed++; Unit }
                            else -> error(property)
                        }
                    }
                }
                "encode" -> {
                    @Suppress("UNCHECKED_CAST")
                    val body = arguments.last() as CommandEncoder.() -> Unit
                    body(encoder)
                    check(!failRecording) { "Recording abandoned after upload" }
                }
                "awaitIdle" -> check(!failCompletion) { "GPU completion unavailable" }
                else -> error("Unexpected device operation $operation")
            }
        }
        private val lifetime = ResourceLifetime.build { this }
        val engine: RenderEngine

        init {
            val staticVertices = StaticVertexStreams(device, lifetime)
            val elements = RenderElementPassProcessors()
            elements.install(StreamElement::class.java) { element, _, _, preparation ->
                preparation.stream(element.source)
                check(!failPreparation) { "Preparation rejected after static resolution" }
                emptyList()
            }
            val sampler = proxy<GpuSampler> { operation, _ -> error(operation) }
            val images = RenderImageStore(device, lifetime, sampler)
            engine = RenderEngine(images, RasterPassProcessor(device, elements), RenderStageExecutor(device).lifetime(lifetime), lifetime, ShaderRealizations(device, lifetime), staticVertices)
        }

        fun stage(vararg sources: VertexStreamSource) = engine.stage {
            for (source in sources) {
                val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) { submit(StreamElement(source)) }
                rasterPass(RasterPass(RenderPassDescription("static test", renderArea = RenderArea(0, 0, 1, 1)), collection, ViewParameters(Matrices.Identity, 1, 1, CompareFunction.LessOrEqual)))
            }
        }
    }

    private fun withFixture(block: Fixture.() -> Unit) {
        val fixture = Fixture()
        try { fixture.block() } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }
}

private inline fun <reified T> proxy(crossinline body: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, arguments ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === arguments?.get(0)
            else -> body(method.name.substringBefore('-'), arguments ?: emptyArray())
        }
    } as T
