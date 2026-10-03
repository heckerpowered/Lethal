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
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.image.RenderImageStore
import heckerpowered.render.engine.image.ImageSize
import heckerpowered.render.resource.sampler.SamplerDescription
import heckerpowered.render.resource.sampler.TextureFilter
import heckerpowered.render.resource.sampler.SamplerAddressMode
import heckerpowered.render.resource.sampler.SamplerMipmapMode
import heckerpowered.render.resource.texture.GpuTexture
import heckerpowered.render.resource.texture.GpuTextureView
import heckerpowered.render.resource.texture.TextureDescription
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.resource.target.RenderAttachment
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
            val device = device(awaitIdle = { events += "$label:idle" }) { events += "$label:encode" }
            val elements = RenderElementPassProcessors()
            elements.install(StageElement::class.java) { _, _, _, preparation ->
                assertSame(device, preparation.device)
                events += "$label:prepare"
                emptyList()
            }
            val sampler = Proxy.newProxyInstance(GpuSampler::class.java.classLoader, arrayOf(GpuSampler::class.java)) { _, _, _ -> error("Unexpected sampler access") } as GpuSampler
            val images = RenderImageStore(device, lifetime, sampler)
            val executor = RenderStageExecutor(device).lifetime(lifetime)
            return RenderEngine(images, RasterPassProcessor(device, elements), executor, lifetime, ShaderRealizations(device, lifetime))
        }

        val first = engine("first")
        val second = engine("second")
        try {
            first.stage { rasterPass(stagePass("shared")) }
            second.stage { rasterPass(stagePass("shared")) }
            first.stage { rasterPass(stagePass("shared")) }
            assertEquals(
                listOf(
                    "first:prepare",
                    "first:encode",
                    "first:idle",
                    "second:prepare",
                    "second:encode",
                    "second:idle",
                    "first:prepare",
                    "first:encode",
                    "first:idle",
                ),
                events
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
        assertSame(
            failure,
            assertFailsWith<AssertionError> {
                RenderStageProcessor.prepare(builder.build(), RasterPassProcessor(device { error("Preparation must not encode") }, elements))
            },
        )
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
        RenderStageExecutor(device(awaitIdle = { events += "idle" }) { events += "encode" }).execute(stage)
        assertEquals(listOf("encode", "idle", "temporary"), events)
    }

    @Test
    fun encodingFailureStillWaitsBeforeCleanup() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("encoding failed")
        val stage = stage(events)
        val executor = RenderStageExecutor(device(awaitIdle = { events += "idle" }) {
            events += "encode"
            throw failure
        })
        assertSame(failure, assertFailsWith<IllegalStateException> { executor.execute(stage) })
        assertEquals(listOf("encode", "idle", "temporary"), events)
        assertTrue(failure.suppressed.isEmpty())
    }

    @Test
    fun recordingAndCompletionFailuresWaitOnceAndPreserveTheirPropagationRules() {
        val failures: List<() -> Throwable?> = listOf(
            { null },
            { IllegalStateException("operational failure") },
            { AssertionError("fatal failure") },
            { Throwable("direct throwable") },
        )
        for (createRecordingFailure in failures) for (createCompletionFailure in failures) {
            val events = mutableListOf<String>()
            val recordingFailure = createRecordingFailure()
            val completionFailure = createCompletionFailure()
            var completionFails = true
            // Direct overrides keep checked Throwables from being wrapped by the proxy.
            val graphics = object : GraphicsDevice by device(encode = { error("Unexpected fallback encoding") }) {
                override fun encode(label: String, commands: CommandEncoder.() -> Unit) {
                    events += "encode"
                    recordingFailure?.let { throw it }
                }

                override fun awaitIdle() {
                    events += "wait"
                    if (completionFails) completionFailure?.let { throw it }
                }
            }
            val executor = RenderStageExecutor(graphics)
            val pending = stage(events)
            val expectedFailure = if (completionFailure is Error) completionFailure else recordingFailure ?: completionFailure

            if (expectedFailure == null) {
                executor.execute(pending)
            } else {
                assertSame(expectedFailure, assertFails { executor.execute(pending) })
            }
            val secondaryFailure = if (expectedFailure === recordingFailure) completionFailure else recordingFailure
            val expectedSuppressed = if (recordingFailure != null && completionFailure != null) listOf(secondaryFailure) else emptyList()
            assertEquals(if (expectedFailure === recordingFailure) expectedSuppressed else emptyList(), recordingFailure?.suppressed?.toList() ?: emptyList())
            assertEquals(if (expectedFailure === completionFailure) expectedSuppressed else emptyList(), completionFailure?.suppressed?.toList() ?: emptyList())

            if (completionFailure != null) {
                assertEquals(listOf("encode", "wait"), events)
                pending.temporaryLifetime.checkOpen()
                assertFailsWith<IllegalStateException> { executor.checkReady() }
                completionFails = false
                executor.awaitIdle()
                assertEquals(listOf("encode", "wait", "wait", "temporary"), events)
            } else {
                assertEquals(listOf("encode", "wait", "temporary"), events)
            }
            executor.checkReady()
            assertFailsWith<IllegalStateException> { pending.temporaryLifetime.checkOpen() }
            val completedEvents = events.toList()
            executor.close()
            assertEquals(completedEvents, events)
        }
    }

    @Test
    fun suppressionDisabledRecordingFailureRemainsPrimaryWhenCompletionFails() {
        val events = mutableListOf<String>()
        val recordingFailure = object : RuntimeException("recording failed", null, false, true) {}
        val completionFailure = IllegalStateException("GPU completion unknown")
        var completionFails = true
        val executor = RenderStageExecutor(device(awaitIdle = {
            events += "wait"
            if (completionFails) throw completionFailure
        }) {
            events += "encode"
            throw recordingFailure
        })
        val pending = stage(events)

        assertSame(recordingFailure, assertFailsWith<Exception> { executor.execute(pending) })
        assertTrue(recordingFailure.suppressed.isEmpty())
        assertEquals(listOf("encode", "wait"), events)
        pending.temporaryLifetime.checkOpen()
        assertFailsWith<IllegalStateException> { executor.checkReady() }

        completionFails = false
        executor.awaitIdle()
        executor.checkReady()
        assertFailsWith<IllegalStateException> { pending.temporaryLifetime.checkOpen() }
        executor.close()
        assertEquals(listOf("encode", "wait", "wait", "temporary"), events)
    }

    @Test
    fun suppressionDisabledCompletionErrorRemainsPrimaryWithoutRecordingDiagnostic() {
        val events = mutableListOf<String>()
        val recordingFailure = IllegalArgumentException("encoding failed")
        val completionFailure = object : Error("GPU completion unknown", null, false, true) {}
        var completionFails = true
        val executor = RenderStageExecutor(device(awaitIdle = {
            events += "wait"
            if (completionFails) throw completionFailure
        }) {
            events += "encode"
            throw recordingFailure
        })
        val pending = stage(events)

        assertSame(completionFailure, assertFailsWith<Error> { executor.execute(pending) })
        assertTrue(completionFailure.suppressed.isEmpty())
        assertTrue(recordingFailure.suppressed.isEmpty())
        assertEquals(listOf("encode", "wait"), events)
        pending.temporaryLifetime.checkOpen()
        assertFailsWith<IllegalStateException> { executor.checkReady() }

        completionFails = false
        executor.awaitIdle()
        executor.checkReady()
        assertFailsWith<IllegalStateException> { pending.temporaryLifetime.checkOpen() }
        executor.close()
        assertEquals(listOf("encode", "wait", "wait", "temporary"), events)
    }

    @Test
    fun recordingAndCompletionExceptionsRetainBothDiagnosticsUntilSuccessfulRetry() {
        val events = mutableListOf<String>()
        val recordingFailure = IllegalArgumentException("encoding failed")
        val completionFailure = IllegalStateException("GPU completion unknown")
        var recordingFails = true
        var completionFails = true
        val executor = RenderStageExecutor(device(awaitIdle = {
            events += "wait"
            if (completionFails) throw completionFailure
        }) {
            events += "encode"
            if (recordingFails) throw recordingFailure
        })
        val pending = stage(events)

        assertSame(recordingFailure, assertFailsWith<IllegalArgumentException> { executor.execute(pending) })
        assertEquals(1, recordingFailure.suppressed.size)
        assertSame(completionFailure, recordingFailure.suppressed.single())
        assertEquals(listOf("encode", "wait"), events)
        pending.temporaryLifetime.checkOpen()
        val nextStage = stage(events)
        assertFailsWith<IllegalStateException> { executor.execute(nextStage) }
        nextStage.temporaryLifetime.checkOpen()
        assertEquals(listOf("encode", "wait"), events)

        completionFails = false
        executor.awaitIdle()
        assertFailsWith<IllegalStateException> { pending.temporaryLifetime.checkOpen() }
        recordingFails = false
        executor.execute(nextStage)
        assertFailsWith<IllegalStateException> { nextStage.temporaryLifetime.checkOpen() }
        executor.close()
        assertEquals(listOf("encode", "wait", "wait", "temporary", "encode", "wait", "temporary"), events)
    }

    @Test
    fun theSameRecordingAndCompletionExceptionIsNotSuppressedOnItself() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("shared operational failure")
        var completionFails = true
        val executor = RenderStageExecutor(device(awaitIdle = {
            if (completionFails) throw failure
            events += "idle"
        }) { throw failure })
        val pending = stage(events)

        assertSame(failure, assertFailsWith<IllegalStateException> { executor.execute(pending) })
        assertTrue(failure.suppressed.isEmpty())
        pending.temporaryLifetime.checkOpen()
        assertFailsWith<IllegalStateException> { executor.checkReady() }

        completionFails = false
        executor.awaitIdle()
        executor.checkReady()
        assertFailsWith<IllegalStateException> { pending.temporaryLifetime.checkOpen() }
        executor.close()
        assertEquals(listOf("idle", "temporary"), events)
    }

    @Test
    fun theSameRecordingAndCompletionErrorIsNotSuppressedOnItself() {
        val events = mutableListOf<String>()
        val failure = AssertionError("shared recording and completion failure")
        var completionFails = true
        val executor = RenderStageExecutor(device(awaitIdle = {
            events += "wait"
            if (completionFails) throw failure
        }) {
            events += "encode"
            throw failure
        })
        val pending = stage(events)

        assertSame(failure, assertFailsWith<AssertionError> { executor.execute(pending) })
        assertTrue(failure.suppressed.isEmpty())
        assertEquals(listOf("encode", "wait"), events)
        pending.temporaryLifetime.checkOpen()
        assertFailsWith<IllegalStateException> { executor.checkReady() }

        completionFails = false
        executor.awaitIdle()
        executor.checkReady()
        assertFailsWith<IllegalStateException> { pending.temporaryLifetime.checkOpen() }
        executor.close()
        assertEquals(listOf("encode", "wait", "wait", "temporary"), events)
    }

    @Test
    fun aRecordingErrorStillPropagatesAfterSuccessfulCompletionAndCleanup() {
        val events = mutableListOf<String>()
        val failure = AssertionError("recording failed")
        val pending = stage(events)
        val executor = RenderStageExecutor(device(awaitIdle = { events += "idle" }) {
            events += "encode"
            throw failure
        })

        assertSame(failure, assertFailsWith<AssertionError> { executor.execute(pending) })
        assertTrue(failure.suppressed.isEmpty())
        assertFailsWith<IllegalStateException> { pending.temporaryLifetime.checkOpen() }
        executor.checkReady()
        executor.close()
        assertEquals(listOf("encode", "idle", "temporary"), events)
    }

    @Test
    fun aCompletionErrorStillPropagatesAndRetainsResourcesUntilSuccessfulRetry() {
        val events = mutableListOf<String>()
        val failure = AssertionError("GPU completion unknown")
        var completionFails = true
        val executor = RenderStageExecutor(device(awaitIdle = {
            events += "wait"
            if (completionFails) throw failure
        }) { events += "encode" })
        val pending = stage(events)

        assertSame(failure, assertFailsWith<AssertionError> { executor.execute(pending) })
        assertTrue(failure.suppressed.isEmpty())
        pending.temporaryLifetime.checkOpen()
        assertFailsWith<IllegalStateException> { executor.checkReady() }
        assertEquals(listOf("encode", "wait"), events)

        completionFails = false
        executor.awaitIdle()
        executor.checkReady()
        assertFailsWith<IllegalStateException> { pending.temporaryLifetime.checkOpen() }
        executor.close()
        assertEquals(listOf("encode", "wait", "wait", "temporary"), events)
    }

    @Test
    fun failedGpuWaitDoesNotReleasePotentiallyActiveResources() {
        val events = mutableListOf<String>()
        val stage = stage(events)
        val failure = IllegalStateException("GPU completion unknown")
        var completionFails = true
        val executor = RenderStageExecutor(device(awaitIdle = {
            if (completionFails) throw failure
            events += "idle"
        }) { events += "encode" })
        assertSame(failure, assertFailsWith<IllegalStateException> { executor.execute(stage) })
        assertEquals(listOf("encode"), events)
        assertTrue(failure.suppressed.isEmpty())
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
        var completionFails = true
        val device = device(awaitIdle = {
            check(!completionFails) { "GPU completion unknown" }
            events += "idle"
        }) { events += "encode" }
        val executor = RenderStageExecutor(device)
        val pending = stage(events)
        assertFailsWith<IllegalStateException> { executor.execute(pending) }

        val sampler = Proxy.newProxyInstance(GpuSampler::class.java.classLoader, arrayOf(GpuSampler::class.java)) { _, _, _ -> error("Unexpected sampler access") } as GpuSampler
        val images = RenderImageStore(device, lifetime, sampler)
        val engine = RenderEngine(
            images,
            RasterPassProcessor(device, RenderElementPassProcessors()),
            executor,
            lifetime,
            ShaderRealizations(device, lifetime),
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
    fun failedExplicitGpuWaitBlocksAnEmptyEngineUntilCompletionSucceeds() {
        val events = mutableListOf<String>()
        val lifetime = ResourceLifetime.build { this }
        val failure = AssertionError("GPU completion unknown")
        var completionFails = true
        val device = device(awaitIdle = {
            events += "wait"
            if (completionFails) throw failure
        }) { events += "encode" }
        val sampler = Proxy.newProxyInstance(GpuSampler::class.java.classLoader, arrayOf(GpuSampler::class.java)) { _, _, _ -> error("Unexpected sampler access") } as GpuSampler
        val images = RenderImageStore(device, lifetime, sampler)
        val engine = RenderEngine(
            images,
            RasterPassProcessor(device, RenderElementPassProcessors()),
            RenderStageExecutor(device),
            lifetime,
            ShaderRealizations(device, lifetime),
        )

        repeat(2) {
            assertSame(failure, assertFailsWith<AssertionError> { engine.awaitIdle() })
            assertFailsWith<IllegalStateException> { engine.stage { events += "callback" } }
        }
        assertEquals(listOf("wait", "wait"), events)

        completionFails = false
        engine.awaitIdle()
        engine.stage { events += "callback" }
        assertEquals(listOf("wait", "wait", "wait", "callback", "encode", "wait"), events)
        engine.close()
        assertEquals(listOf("wait", "wait", "wait", "callback", "encode", "wait"), events)
    }

    @Test
    fun closedEngineLifetimeRejectsStageBeforeUserCallbackOrGpuAccess() {
        val lifetime = ResourceLifetime.build { this }
        val device = device(awaitIdle = { error("Closed engine must not wait") }) { error("Closed renderer must not encode") }
        val sampler = Proxy.newProxyInstance(GpuSampler::class.java.classLoader, arrayOf(GpuSampler::class.java)) { _, _, _ -> error("Unexpected sampler access") } as GpuSampler
        val images = RenderImageStore(device, lifetime, sampler)
        val engine = RenderEngine(
            images,
            RasterPassProcessor(device, RenderElementPassProcessors()),
            RenderStageExecutor(device),
            lifetime,
            ShaderRealizations(device, lifetime),
        )
        lifetime.close()
        assertFailsWith<IllegalStateException> { engine.stage { error("Closed engine must not invoke callbacks") } }
    }

    @Test
    fun engineCreatesOneLinearClampSamplerAndReleasesItAfterItsImagesWithoutWaiting() {
        val fixture = SamplerDevice()
        val engine = RenderEngine.create(fixture.device)
        assertEquals(SamplerDescription(
            minificationFilter = TextureFilter.Linear,
            magnificationFilter = TextureFilter.Linear,
            addressModeU = SamplerAddressMode.ClampToEdge,
            addressModeV = SamplerAddressMode.ClampToEdge,
            addressModeW = SamplerAddressMode.ClampToEdge,
            mipmapMode = SamplerMipmapMode.Disabled,
        ), fixture.description)
        val first = engine.images.image("first", ImageSize(2, 2), TextureFormat.Rgba16Float)
        val second = engine.images.image("second", ImageSize(2, 2), TextureFormat.Rgba16Float)
        assertSame(first, engine.images.image("first", ImageSize(2, 2), TextureFormat.Rgba16Float))
        assertSame(first.sampler, second.sampler)
        assertSame(first.sampler, first.sampled().sampler)
        assertEquals(1, fixture.samplerCreations)

        engine.close()
        engine.close()
        assertEquals(listOf("texture:first", "texture:second", "sampler"), fixture.closed)
        assertEquals(0, fixture.waits)
    }

    @Test
    fun failedImageCreationReleasesPartialTextureAndKeepsSharedSamplerUntilEngineClose() {
        val fixture = SamplerDevice()
        val engine = RenderEngine.create(fixture.device)
        fixture.failViewCreation = true
        assertFailsWith<IllegalStateException> {
            engine.images.image("failed", ImageSize(2, 2), TextureFormat.Rgba16Float)
        }
        assertEquals(listOf("texture:failed"), fixture.closed)
        assertEquals(1, fixture.samplerCreations)

        fixture.failViewCreation = false
        engine.images.image("retry", ImageSize(2, 2), TextureFormat.Rgba16Float)
        assertEquals(1, fixture.samplerCreations)
        engine.close()
        assertEquals(listOf("texture:failed", "texture:retry", "sampler"), fixture.closed)
        assertEquals(0, fixture.waits)
    }

    @Test
    fun failedDefaultSamplerCreationStopsEngineConstruction() {
        val failure = IllegalStateException("sampler allocation failed")
        val calls = mutableListOf<String>()
        val device = testProxy<GraphicsDevice> { operation, _ ->
            calls += operation
            if (operation == "createSampler") throw failure
            error("Construction must stop before $operation")
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { RenderEngine.create(device) })
        assertEquals(listOf("createSampler"), calls)
    }

    private class SamplerDevice {
        val closed = mutableListOf<String>()
        var samplerCreations = 0
        var waits = 0
        var description: SamplerDescription? = null
        var failViewCreation = false
        val device = testProxy<GraphicsDevice> { operation, arguments ->
            when (operation) {
                "createSampler" -> {
                    samplerCreations++
                    description = arguments[0] as SamplerDescription
                    testProxy<GpuSampler> { method, _ ->
                        check(method == "close")
                        terminateOnFailure { closed += "sampler"; Unit }
                    }
                }
                "createTexture" -> {
                    val texture = arguments[0] as TextureDescription
                    testProxy<GpuTexture> { method, _ ->
                        check(method == "close")
                        terminateOnFailure { closed += "texture:${texture.label}"; Unit }
                    }
                }
                "createTextureView" -> {
                    check(!failViewCreation) { "view creation failed" }
                    testProxy<GpuTextureView> { _, _ -> error("No view access during allocation") }
                }
                "createAttachmentView" -> testProxy<RenderAttachment> { _, _ -> error("No attachment access during allocation") }
                "awaitIdle" -> { waits++; Unit }
                else -> error("Unexpected sampler fixture operation: $operation")
            }
        }
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

    private fun resource(events: MutableList<String>, name: String): AutoCloseable = AutoCloseable { terminateOnFailure { events += name } }

    private fun device(awaitIdle: () -> Unit = {}, encode: () -> Unit): GraphicsDevice = Proxy.newProxyInstance(
        GraphicsDevice::class.java.classLoader,
        arrayOf(GraphicsDevice::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "encode" -> encode()
            "awaitIdle" -> awaitIdle()
            else -> error("Unexpected graphics operation: ${method.name}")
        }
        null
    } as GraphicsDevice

    companion object {
        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> testProxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, arguments ->
                when (method.name) {
                    "equals" -> instance === arguments?.get(0)
                    "hashCode" -> System.identityHashCode(instance)
                    "toString" -> T::class.java.simpleName
                    else -> invoke(method.name.substringBefore('-'), arguments ?: emptyArray())
                }
            } as T
    }

}
