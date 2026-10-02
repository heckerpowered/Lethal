/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.prepare

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Matrices
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.image.RenderImageStore
import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.engine.pass.RasterPassProcessor
import heckerpowered.render.engine.pass.RenderElementPassProcessors
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderElement
import heckerpowered.render.engine.scene.drawing.WorldDrawing
import heckerpowered.render.engine.stage.PreparedRenderStage
import heckerpowered.render.engine.stage.RenderStageBuilder
import heckerpowered.render.engine.stage.RenderStageExecutor
import heckerpowered.render.engine.stage.RenderStageProcessor
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.lifetime
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

class ResourceLifetimeTest {
    @Test
    fun alternatingEnginesPrepareWithTheirOwnDeviceAndElementProcessors() {
        val events = mutableListOf<String>()
        fun engine(label: String): RenderEngine {
            val lifetime = ResourceLifetime.build { this }
            val device = device { events += "$label:encode" }
            val elements = RenderElementPassProcessors()
            elements.install(StageElement::class.java) { _, _, _, preparation ->
                assertSame(device, preparation.device)
                events += "$label:prepare"
                emptyList()
            }
            val sampler = Proxy.newProxyInstance(GpuSampler::class.java.classLoader, arrayOf(GpuSampler::class.java)) { _, _, _ -> error("Unexpected sampler access") } as GpuSampler
            val images = RenderImageStore(device, lifetime, sampler).lifetime(lifetime)
            val executor = RenderStageExecutor(device, GpuQuiescence { events += "$label:idle" }).lifetime(lifetime)
            return RenderEngine(images, RasterPassProcessor(device, elements), executor, lifetime)
        }

        val first = engine("first")
        val second = engine("second")
        try {
            first.stage { rasterPass(stagePass("shared")) }
            second.stage { rasterPass(stagePass("shared")) }
            first.stage { rasterPass(stagePass("shared")) }
            assertEquals(
                listOf(
                    "first:prepare", "first:encode", "first:idle",
                    "second:prepare", "second:encode", "second:idle",
                    "first:prepare", "first:encode", "first:idle",
                ), events
            )
        } finally {
            second.close()
            first.close()
        }
    }

    @Test
    fun failedStagePreparationReleasesAllEarlierPassResourcesInReverseOrder() {
        val events = mutableListOf<String>()
        val failure = AssertionError("second pass failed")
        val elements = RenderElementPassProcessors()
        elements.install(StageElement::class.java) { element, _, _, preparation ->
            events += element.label
            preparation.lifetime.register(resource(events, "${element.label}:close"))
            if (element.label == "second") throw failure
            emptyList()
        }
        val builder = RenderStageBuilder().apply {
            rasterPass(stagePass("first"))
            rasterPass(stagePass("second"))
            rasterPass(stagePass("third"))
        }
        assertSame(failure, assertFailsWith<AssertionError> {
            RenderStageProcessor.prepare(builder.build(), RasterPassProcessor(device { error("Preparation must not encode") }, elements))
        })
        assertEquals(listOf("first", "second", "second:close", "first:close"), events)
    }

    @Test
    fun closesInReverseOrderOnlyOnce() {
        val events = mutableListOf<String>()
        val lifetime = ResourceLifetime.build {
            resource(events, "first").lifetime(this)
            resource(events, "second").lifetime(this)
            this
        }
        lifetime.close()
        lifetime.close()
        assertEquals(listOf("second", "first"), events)
        assertFailsWith<IllegalStateException> { lifetime.checkOpen() }
    }

    @Test
    fun failedConstructionCleansUpEvenForErrors() {
        val events = mutableListOf<String>()
        val failure = AssertionError("construction failed")
        val actual = assertFailsWith<AssertionError> {
            ResourceLifetime.build {
                register(resource(events, "temporary"))
                throw failure
            }
        }
        assertSame(failure, actual)
        assertEquals(listOf("temporary"), events)
    }

    @Test
    fun rejectedRegistrationClosesTheNewResource() {
        val events = mutableListOf<String>()
        val lifetime = ResourceLifetime.build { this }
        lifetime.close()
        assertFailsWith<IllegalStateException> { resource(events, "rejected").lifetime(lifetime) }
        assertEquals(listOf("rejected"), events)
    }

    @Test
    fun executionWaitsBeforeClosingTemporaryResources() {
        val events = mutableListOf<String>()
        val stage = stage(events)
        RenderStageExecutor(device { events += "encode" }, GpuQuiescence { events += "idle" }).execute(stage)
        assertEquals(listOf("encode", "idle", "temporary"), events)
    }

    @Test
    fun encodingFailureStillWaitsBeforeCleanup() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("encoding failed")
        val stage = stage(events)
        val executor = RenderStageExecutor(device { events += "encode"; throw failure }, GpuQuiescence { events += "idle" })
        assertSame(failure, assertFailsWith<IllegalStateException> { executor.execute(stage) })
        assertEquals(listOf("encode", "idle", "temporary"), events)
    }

    @Test
    fun failedGpuWaitDoesNotReleasePotentiallyActiveResources() {
        val events = mutableListOf<String>()
        val stage = stage(events)
        val failure = IllegalStateException("GPU completion unknown")
        var completionFails = true
        val executor = RenderStageExecutor(device { events += "encode" }, GpuQuiescence {
            if (completionFails) throw failure
            events += "idle"
        })
        assertSame(failure, assertFailsWith<IllegalStateException> { executor.execute(stage) })
        assertEquals(listOf("encode"), events)
        stage.temporaryLifetime.checkOpen()

        val nextStage = stage(events)
        assertFailsWith<IllegalStateException> { executor.execute(nextStage) }
        assertEquals(listOf("encode"), events)
        stage.temporaryLifetime.checkOpen()
        nextStage.temporaryLifetime.checkOpen()

        completionFails = false
        executor.awaitIdle()
        assertEquals(listOf("encode", "idle", "temporary"), events)
        assertFailsWith<IllegalStateException> { stage.temporaryLifetime.checkOpen() }

        executor.execute(nextStage)
        assertEquals(listOf("encode", "idle", "temporary", "encode", "idle", "temporary"), events)
        assertFailsWith<IllegalStateException> { nextStage.temporaryLifetime.checkOpen() }
    }

    @Test
    fun pendingGpuCompletionRejectsAStageBeforeItsCallbackOrDeviceAccess() {
        val events = mutableListOf<String>()
        val lifetime = ResourceLifetime.build { this }
        val device = device { events += "encode" }
        var completionFails = true
        val executor = RenderStageExecutor(device, GpuQuiescence {
            check(!completionFails) { "GPU completion unknown" }
            events += "idle"
        })
        val pending = stage(events)
        assertFailsWith<IllegalStateException> { executor.execute(pending) }

        val sampler = Proxy.newProxyInstance(GpuSampler::class.java.classLoader, arrayOf(GpuSampler::class.java)) { _, _, _ -> error("Unexpected sampler access") } as GpuSampler
        val images = RenderImageStore(device, lifetime, sampler)
        val engine = RenderEngine(
            images,
            RasterPassProcessor(device, RenderElementPassProcessors()), executor, lifetime
        )
        var callbackInvoked = false

        assertFailsWith<IllegalStateException> { engine.stage { callbackInvoked = true } }
        assertFalse(callbackInvoked)
        assertEquals(listOf("encode"), events)
        pending.temporaryLifetime.checkOpen()

        completionFails = false
        engine.awaitIdle()
        engine.stage { callbackInvoked = true }
        assertTrue(callbackInvoked)
        assertEquals(listOf("encode", "idle", "temporary", "encode", "idle"), events)
        engine.close()
    }

    @Test
    fun closedEngineLifetimeRejectsStageBeforeUserCallbackOrGpuAccess() {
        val lifetime = ResourceLifetime.build { this }
        val device = device { error("Closed renderer must not encode") }
        val sampler = Proxy.newProxyInstance(GpuSampler::class.java.classLoader, arrayOf(GpuSampler::class.java)) { _, _, _ -> error("Unexpected sampler access") } as GpuSampler
        val images = RenderImageStore(device, lifetime, sampler)
        val engine = RenderEngine(
            images,
            RasterPassProcessor(device, RenderElementPassProcessors()), RenderStageExecutor(device, GpuQuiescence { error("Closed engine must not wait") }), lifetime
        )
        lifetime.close()
        assertFailsWith<IllegalStateException> { engine.stage { error("Closed engine must not invoke callbacks") } }
    }

    private data class StageElement(val label: String) : RenderElement

    private fun stagePass(label: String): RasterPass = RasterPass(
        RenderPassDescription(label, RenderArea(0, 0, 1, 1)),
        WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) { submit(StageElement(label)) },
        ViewParameters(Matrices.Identity, 1, 1, CompareFunction.Always),
    )

    private fun stage(events: MutableList<String>): PreparedRenderStage = ResourceLifetime.build {
        register(resource(events, "temporary"))
        PreparedRenderStage(emptyList(), this)
    }

    private fun resource(events: MutableList<String>, name: String): AutoCloseable = object : AutoCloseable {
        override fun close() = terminateOnFailure { events += name }
    }

    private fun device(encode: () -> Unit): GraphicsDevice = Proxy.newProxyInstance(
        GraphicsDevice::class.java.classLoader, arrayOf(GraphicsDevice::class.java),
    ) { _, method, _ ->
        check(method.name == "encode") { "Unexpected graphics operation: ${method.name}" }
        encode()
        null
    } as GraphicsDevice
}
