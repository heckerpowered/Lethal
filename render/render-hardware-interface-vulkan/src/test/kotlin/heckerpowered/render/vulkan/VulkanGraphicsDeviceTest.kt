/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.color.Color
import heckerpowered.render.command.*
import heckerpowered.render.command.pass.*
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.vulkan.command.VertexTestFixtures
import heckerpowered.render.resource.buffer.*
import heckerpowered.render.resource.sampler.*
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.*
import heckerpowered.render.vulkan.command.RasterTestFixtures
import heckerpowered.render.vulkan.command.RasterTestQueue
import heckerpowered.render.vulkan.function.*
import heckerpowered.render.vulkan.resource.*
import kotlin.test.*

class VulkanGraphicsDeviceTest {
    @Test
    fun publicDirectSpirVPositionConsumerUploadsDeclaresBindsBothPushStagesAndDraws() {
        val scene = DeviceScene(true, VertexTestFixtures.vertexState(2, 32, 12))
        val consumer: GraphicsDevice = scene.device
        val vertices = GpuBufferView(checkNotNull(scene.vertices), 4, 88)
        val bytes = ByteArray(88) { it.toByte() }
        lateinit var escaped: RenderPass
        consumer.encode("public position") {
            memoryStack.frame { val address = reserve(88, 1); asByteBuffer(address, 88).put(bytes); writeBuffer(vertices, address) }
            renderPass(scene.pass, RenderPassResources(vertexBuffers = listOf(vertices))) {
                escaped = this
                bindVertexBuffer(2, vertices)
                bindPipeline(scene.pipeline)
                pushConstants(setOf(ShaderStage.Vertex, ShaderStage.Fragment), VertexTestFixtures.pushBytes())
                draw(3)
            }
            copyTextureToBuffer(scene.region, scene.bufferView)
        }
        assertFailsWith<IllegalStateException> { (vertices.buffer as VulkanBuffer).requireUnused() }
        assertFalse(scene.queue.calls.any { it == "idle" || it.startsWith("wait:") })
        val trace = scene.queue.calls
        val copyIndex = trace.indexOfFirst { it.startsWith("copyBuffer:") }
        val vertexBarrierIndex = trace.indexOfFirst { it.startsWith("vertexBarrier:") }
        val beginPassIndex = trace.indexOf("beginPass")
        assertTrue(copyIndex >= 0, "Vertex upload copy was not recorded")
        assertTrue(vertexBarrierIndex >= 0, "Vertex read barrier was not recorded")
        assertTrue(beginPassIndex >= 0, "Render pass begin was not recorded")

        assertTrue(copyIndex < vertexBarrierIndex)
        assertTrue(vertexBarrierIndex < beginPassIndex)
        val before = trace.toList()
        assertFailsWith<IllegalStateException> { escaped.bindVertexBuffer(2, vertices) }
        assertEquals(before, trace)
        consumer.awaitIdle()
        (vertices.buffer as VulkanBuffer).requireUnused()
        assertContentEquals(scene.clearBytes, scene.device.readBuffer(scene.bufferView))
        assertEquals(1, trace.count { it.startsWith("draw:") })
        assertFailsWith<IllegalArgumentException> { consumer.compileCanonicalShader(VertexTestFixtures.description(ShaderStage.Vertex), "unlit", emptyMap()) }
        scene.close()
    }

    @Test
    fun logicalDevicesSharingActualQueueRejectForeignVertexDeclarationsAndBindings() {
        val scene = DeviceScene(true, VertexTestFixtures.vertexState())
        val foreign = testVulkanGraphicsDevice(scene.queue)
        val foreignBuffer = foreign.createBuffer(BufferDescription("other logical device", 36, setOf(BufferUsage.Vertex)))
        val own = GpuBufferView(checkNotNull(scene.vertices), 0, 36)
        scene.device.encode("foreign declarations") {
            val before = scene.queue.calls.toList()
            assertFailsWith<IllegalArgumentException> { renderPass(scene.pass, RenderPassResources(vertexBuffers = listOf(GpuBufferView(foreignBuffer, 0, 36)))) {} }
            assertEquals(before, scene.queue.calls)
            renderPass(scene.pass, RenderPassResources(vertexBuffers = listOf(own))) {
                val bindBefore = scene.queue.calls.toList()
                assertFailsWith<IllegalArgumentException> { bindVertexBuffer(0, GpuBufferView(foreignBuffer, 0, 36)) }
                assertEquals(bindBefore, scene.queue.calls)
                bindVertexBuffer(0, own)
            }
        }
        scene.device.awaitIdle(); foreignBuffer.close(); foreign.close(); scene.close()
    }

    @Test
    fun failedVertexWaitKeepsPinsUntilExplicitRecoveryAndRejectedSubmitReleasesThem() {
        val scene = DeviceScene(true, VertexTestFixtures.vertexState())
        val buffer = checkNotNull(scene.vertices) as VulkanBuffer
        val resources = RenderPassResources(vertexBuffers = listOf(GpuBufferView(buffer, 0, 36)))
        scene.queue.submission = VulkanSubmissionStatus.OutOfDeviceMemory
        assertFailsWith<IllegalStateException> { scene.device.encode("rejected vertices") { renderPass(scene.pass, resources) {} } }
        buffer.requireUnused()
        scene.queue.submission = VulkanSubmissionStatus.Accepted
        scene.device.encode("pending vertices") { renderPass(scene.pass, resources) {} }
        scene.queue.failureStage = "idle"
        assertFailsWith<IllegalStateException> { scene.device.awaitIdle() }
        assertFailsWith<IllegalStateException> { buffer.requireUnused() }
        assertFailsWith<IllegalStateException> { scene.device.encode("no premature reuse") {} }
        scene.queue.failureStage = ""
        scene.device.awaitIdle(); buffer.requireUnused(); scene.close()
    }

    @Test
    fun unknownVertexSubmitRetainsPinsAndPrepassFailureLeavesDeviceReusable() {
        val scene = DeviceScene(true, VertexTestFixtures.vertexState())
        val buffer = checkNotNull(scene.vertices) as VulkanBuffer
        val resources = RenderPassResources(vertexBuffers = listOf(GpuBufferView(buffer, 0, 36)))
        scene.queue.failureStage = "vertexBarrier"
        assertFailsWith<IllegalStateException> { scene.device.encode("failed boundary") { renderPass(scene.pass, resources) {} } }
        buffer.requireUnused()
        assertFalse(scene.queue.calls.any { it.startsWith("submit:") })
        scene.queue.failureStage = "submitAfter"
        assertFailsWith<IllegalStateException> { scene.device.encode("unknown acceptance") { renderPass(scene.pass, resources) {} } }
        assertFailsWith<IllegalStateException> { buffer.requireUnused() }
        scene.queue.failureStage = ""
        scene.device.awaitIdle(); buffer.requireUnused()
        scene.device.encode("usable after proof") { renderPass(scene.pass, resources) {} }
        scene.device.awaitIdle(); scene.close()
    }

    @Test
    fun failureBeforeRecordEntryDiscardsReadySessionAndRestoresAvailableBoundary() {
        val queue = RasterTestQueue()
        var failNextAccess = false
        val buffers = object : VulkanBufferFunctions by queue.bufferFunctions {
            override fun checkAccess() {
                if (failNextAccess) { failNextAccess = false; error("pre-record setup failure") }
                queue.bufferFunctions.checkAccess()
            }
        }
        val images = object : VulkanImageFunctions by queue.imageFunctions { override val bufferFunctions = buffers }
        val host = object : VulkanHostMemoryFunctions by queue.host { override val bufferFunctions = buffers }
        val transfers = object : VulkanImageTransferFunctions by queue { override val imageFunctions = images }
        val raster = object : VulkanRasterFunctions by queue { override val imageFunctions = images }
        var injectFailure = true
        val functions = object : VulkanTransferFunctions by queue {
            override val bufferFunctions = buffers
            override val hostMemoryFunctions = host
            override val imageTransferFunctions = transfers
            override val rasterFunctions = raster
            override fun createRecording(): VulkanTransferRecording = queue.createRecording().also { failNextAccess = injectFailure }
        }
        val device = testVulkanGraphicsDevice(functions)
        assertFailsWith<IllegalStateException> { device.encode("setup") { fail("callback must not run") } }
        assertEquals(1, queue.calls.count { it.startsWith("destroyRecording:") })
        assertFalse(queue.calls.any { it.startsWith("submit:") || it == "idle" })
        injectFailure = false
        device.encode("retry") {}
        device.awaitIdle(); device.close()
        assertEquals(2, queue.calls.count { it.startsWith("destroyRecording:") })
    }

    @Test
    fun bufferImageCopiesUseExactOccupiedBytesAndPreserveExtraDestinationCapacity() {
        val scene = DeviceScene()
        val buffer = scene.device.createBuffer(BufferDescription("extra capacity", 100, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val whole = GpuBufferView(buffer, 0, 100)
        val expected = ByteArray(100) { (it + 17).toByte() }
        val consumer: GraphicsDevice = scene.device
        consumer.encode("source") {
            memoryStack.frame { val address = reserve(100, 1); asByteBuffer(address, 100).put(expected); writeBuffer(whole, address) }
            copyBufferToTexture(whole, scene.region)
        }
        consumer.encode("destination") { copyTextureToBuffer(scene.region, whole) }
        consumer.awaitIdle()
        assertContentEquals(expected, scene.device.readBuffer(whole))
        buffer.close(); scene.close()
    }

    @Test
    fun hostReadSelectsSubviewAndRejectsAddressWrapWithGuaranteedUnmap() {
        for (wrap in listOf(false, true)) {
            val queue = RasterTestQueue()
            var base = 0L
            var allocationSize = 0
            var wrapMapping = false
            val host = object : VulkanHostMemoryFunctions by queue.host {
                override fun mapWholeAllocation(memory: Long, allocationSizeBytes: Long): Long {
                    base = queue.host.mapWholeAllocation(memory, allocationSizeBytes)
                    allocationSize = allocationSizeBytes.toInt()
                    return if (wrapMapping) -2L else base
                }
                override fun readMappedBytes(mappedAddress: Long, destination: ByteArray) {
                    val all = ByteArray(allocationSize)
                    queue.host.readMappedBytes(base, all)
                    val offset = (mappedAddress - base).toInt()
                    all.copyInto(destination, startIndex = offset, endIndex = offset + destination.size)
                }
            }
            val functions = object : VulkanTransferFunctions by queue { override val hostMemoryFunctions = host }
            val device = testVulkanGraphicsDevice(functions, hostVisibleBuffers = true)
            val buffer = device.createBuffer(BufferDescription("selection", 8, setOf(BufferUsage.TransferDestination)))
            val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            device.encode("initialize") { memoryStack.frame { val address = reserve(8, 1); asByteBuffer(address, 8).put(bytes); writeBuffer(GpuBufferView(buffer, 0, 8), address) } }
            device.awaitIdle()
            wrapMapping = wrap
            val before = queue.host.calls.count { it.startsWith("unmap:") }
            if (wrap) assertFailsWith<IllegalArgumentException> { device.readBuffer(GpuBufferView(buffer, 4, 3)) }
            else assertContentEquals(byteArrayOf(5, 6, 7), device.readBuffer(GpuBufferView(buffer, 4, 3)))
            assertEquals(before + 1, queue.host.calls.count { it.startsWith("unmap:") })
            buffer.close(); device.close()
        }
    }

    @Test
    fun publicConsumerEncodesConsecutiveUploadsPassesAndReadbackWithoutImplicitWait() {
        val scene = DeviceScene()
        val consumer: GraphicsDevice = scene.device
        val bytes = ByteArray(84) { (it + 1).toByte() }
        consumer.encode("upload") { memoryStack.frame { val address = reserve(84, 1); asByteBuffer(address, 84).put(bytes); writeTexture(scene.region, address) } }
        bytes.fill(0)
        consumer.encode("pass") { renderPass(scene.pass) { bindPipeline(scene.pipeline); draw(3) } }
        consumer.encode("readback") { copyTextureToBuffer(scene.region, scene.bufferView) }
        assertEquals(3, scene.queue.calls.count { it.startsWith("submit:") })
        assertFalse(scene.queue.calls.any { it == "idle" || it.startsWith("poll:") || it.startsWith("wait:") })
        assertFailsWith<IllegalStateException> { scene.device.readBuffer(scene.bufferView) }
        assertFalse(scene.queue.host.calls.any { it.startsWith("invalidate:") })
        consumer.awaitIdle()
        val before = scene.queue.calls.toList()
        assertContentEquals(scene.clearBytes, scene.device.readBuffer(scene.bufferView))
        assertEquals(before, scene.queue.calls)
        assertEquals(3, scene.queue.calls.count { it.startsWith("destroyRecording:") })
        scene.close()
    }

    @Test
    fun passCacheMissIsAllowedAndEscapedPassChecksScopeBeforeResolution() {
        val scene = DeviceScene()
        lateinit var escaped: RenderPass
        scene.device.encode("first cache miss") { renderPass(scene.pass) { escaped = this; bindPipeline(scene.pipeline); draw(3) } }
        scene.device.awaitIdle()
        val before = scene.queue.calls.toList()
        assertFailsWith<IllegalStateException> { escaped.bindPipeline(scene.pipeline.copy(label = "uncached escaped")) }
        assertFailsWith<IllegalStateException> { escaped.memoryStack }
        assertFailsWith<IllegalStateException> { escaped.setStencilReference(7u) }
        assertEquals(before, scene.queue.calls)
        scene.device.encode("cached") { renderPass(scene.pass) { bindPipeline(scene.pipeline); draw(3) } }
        scene.device.awaitIdle()
        assertEquals(3, scene.queue.calls.count { it == "createPass" })
        scene.close()
    }

    @Test
    fun capturedOuterEncoderAndNestedDeviceEntryRejectWithoutNativeCommands() {
        val scene = DeviceScene()
        lateinit var escaped: CommandEncoder
        scene.device.encode("scope") {
            escaped = this
            val outer = this
            renderPass(scene.pass) {
                val before = scene.queue.calls.toList()
                assertFailsWith<IllegalStateException> { outer.copyTextureToBuffer(scene.region, scene.bufferView) }
                assertFailsWith<IllegalStateException> { scene.device.encode("nested") {} }
                assertFailsWith<IllegalStateException> { scene.device.awaitIdle() }
                assertEquals(before, scene.queue.calls)
            }
        }
        val before = scene.queue.calls.toList()
        assertFailsWith<IllegalStateException> { escaped.memoryStack }
        assertFailsWith<IllegalStateException> { escaped.copyTexture(scene.region, scene.region) }
        assertEquals(before, scene.queue.calls)
        scene.device.awaitIdle(); scene.close()
    }

    @Test
    fun rejectedCallbackReleasesRecordingAndLetsNextEncodeProceed() {
        val scene = DeviceScene()
        val failure = IllegalArgumentException("caller")
        assertSame(failure, assertFailsWith<IllegalArgumentException> { scene.device.encode("abandon") { throw failure } })
        assertFalse(scene.queue.calls.any { it.startsWith("submit:") })
        assertEquals(1, scene.queue.calls.count { it.startsWith("destroyRecording:") })
        scene.device.encode("retry") { renderPass(scene.pass) {} }
        scene.device.awaitIdle(); scene.close()
    }

    @Test
    fun rejectedSubmissionReleasesPinsAndLeavesDeviceReusable() {
        val scene = DeviceScene()
        scene.queue.submission = VulkanSubmissionStatus.OutOfDeviceMemory
        assertFailsWith<IllegalStateException> { scene.device.encode("rejected") { renderPass(scene.pass) {} } }
        assertEquals(1, scene.queue.calls.count { it.startsWith("destroyRecording:") })
        scene.queue.submission = VulkanSubmissionStatus.Accepted
        scene.device.encode("retry") { renderPass(scene.pass) {} }
        scene.device.awaitIdle(); scene.close()
    }

    @Test
    fun unknownSubmitBlocksFurtherWorkUntilExplicitQueueProofAndDoesNotPublishImageValidity() {
        val scene = DeviceScene()
        scene.queue.failureStage = "submitAfter"
        assertFailsWith<IllegalStateException> { scene.device.encode("uncertain") { renderPass(scene.pass) {} } }
        scene.queue.failureStage = ""
        val before = scene.queue.calls.toList()
        assertFailsWith<IllegalStateException> { scene.device.encode("blocked") {} }
        assertFailsWith<IllegalStateException> { scene.device.createBuffer(BufferDescription("blocked", 4, setOf(BufferUsage.TransferDestination))) }
        assertFailsWith<IllegalStateException> { scene.device.readBuffer(scene.bufferView) }
        assertEquals(before, scene.queue.calls)
        scene.device.awaitIdle()
        assertFailsWith<IllegalStateException> { scene.device.encode("undefined") { copyTextureToBuffer(scene.region, scene.bufferView) } }
        scene.device.encode("full rewrite") { renderPass(scene.pass) {}; copyTextureToBuffer(scene.region, scene.bufferView) }
        scene.device.awaitIdle()
        assertContentEquals(scene.clearBytes, scene.device.readBuffer(scene.bufferView))
        scene.close()
    }

    @Test
    fun failedWaitRetainsCapturedWorkAndWaitReentryCannotExtendRetirementBatch() {
        val scene = DeviceScene()
        scene.device.encode("pending") { renderPass(scene.pass) {} }
        scene.queue.failureStage = "idle"
        assertFailsWith<IllegalStateException> { scene.device.awaitIdle() }
        assertTrue(scene.queue.calls.none { it.startsWith("destroyRecording:") })
        assertFailsWith<IllegalStateException> { scene.device.encode("blocked") {} }
        scene.queue.failureStage = ""
        scene.queue.duringIdle = {
            val before = scene.queue.calls.toList()
            assertFailsWith<IllegalStateException> { scene.device.encode("reentrant") {} }
            assertFailsWith<IllegalStateException> { scene.device.awaitIdle() }
            assertFailsWith<IllegalStateException> { scene.device.createShaderModule(RasterTestFixtures.description("triangle.vert")) }
            assertEquals(before, scene.queue.calls)
        }
        scene.device.awaitIdle()
        assertEquals(1, scene.queue.calls.count { it.startsWith("destroyRecording:") })
        scene.close()
    }

    @Test
    fun emptyAwaitStillCallsBorrowedQueueAndClosedDeviceRejectsNativeAccess() {
        val queue = RasterTestQueue()
        val device = testVulkanGraphicsDevice(queue)
        device.awaitIdle()
        assertEquals(listOf("idle"), queue.calls)
        device.close(); device.close()
        assertFailsWith<IllegalStateException> { device.awaitIdle() }
        assertFailsWith<IllegalStateException> { device.encode("closed") {} }
        assertEquals(listOf("idle"), queue.calls)
    }

    @Test
    fun separateLogicalDevicesBorrowingSameQueueRejectEachOthersResources() {
        val scene = DeviceScene()
        val foreign = testVulkanGraphicsDevice(scene.queue, DeviceScene.facts())
        val shader = foreign.createShaderModule(RasterTestFixtures.description("triangle.vert"))
        assertFailsWith<IllegalArgumentException> { scene.device.createShaderStages(ShaderStagesDescription(listOf(shader), "foreign")) }
        val foreignTexture = foreign.createTexture(DeviceScene.textureDescription())
        assertFailsWith<IllegalArgumentException> { scene.device.createTextureView(foreignTexture, TextureViewDescription()) }
        val foreignBuffer = foreign.createBuffer(BufferDescription("foreign", 84, setOf(BufferUsage.TransferDestination)))
        scene.device.encode("reject") {
            assertFailsWith<IllegalArgumentException> { copyTextureToBuffer(scene.region, GpuBufferView(foreignBuffer, 0, 84)) }
        }
        scene.device.awaitIdle()
        foreignBuffer.close(); foreignTexture.close(); shader.close(); foreign.close(); scene.close()
    }

    @Test
    fun suppliedFactsAreFrozenAndConflictingFactsRejectBeforeNativeAllocation() {
        val queue = RasterTestQueue()
        val supplied = DeviceScene.facts().toMutableList()
        val device = testVulkanGraphicsDevice(queue, supplied)
        supplied.clear()
        val modules = listOf("triangle.vert", "constant.frag").map { device.createShaderModule(RasterTestFixtures.description(it)) }
        val stages = device.createShaderStages(ShaderStagesDescription(modules, "consumer"))
        val pipeline = device.createRenderPipeline(RenderPipelineDescription("frozen facts", stages, colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized))))
        pipeline.close(); stages.close(); modules.asReversed().forEach { it.close() }; device.close()
        val original = RasterTestFixtures.artifact("constant.frag")
        val altered = ShaderInterfaceArtifact(original.stage, original.entryPoint, original.spirVSha256, ShaderInterfaceDescription(emptyList(), emptyList(), emptyList()))
        val before = queue.calls.toList()
        assertFailsWith<IllegalArgumentException> { testVulkanGraphicsDevice(queue, listOf(original, altered)) }
        assertEquals(before, queue.calls)
        testVulkanGraphicsDevice(queue, listOf(original, original)).close()
    }

    @Test
    fun missingOrWrongEntryFactsPermitModuleButRejectPipelineBeforeNativeCreation() {
        for (facts in listOf(emptyList(), DeviceScene.facts().map { ShaderInterfaceArtifact(it.stage, "other", it.spirVSha256, it.description) })) {
            val queue = RasterTestQueue()
            val device = testVulkanGraphicsDevice(queue, facts)
            val modules = listOf("triangle.vert", "constant.frag").map { device.createShaderModule(RasterTestFixtures.description(it)) }
            val stages = device.createShaderStages(ShaderStagesDescription(modules, "consumer"))
            assertFailsWith<UnsupportedOperationException> { device.createRenderPipeline(RenderPipelineDescription("missing facts", stages, colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)))) }
            assertFalse("createPass" in queue.calls)
            stages.close(); modules.asReversed().forEach { it.close() }; device.close()
        }
    }

    @Test
    fun unsupportedSamplerBuiltinsAndNonCanonicalInputFailExplicitly() {
        val scene = DeviceScene()
        val before = scene.queue.calls.toList()
        assertFailsWith<UnsupportedOperationException> { scene.device.primitives }
        assertFailsWith<UnsupportedOperationException> { scene.device.createSampler(SamplerDescription(TextureFilter.Linear, TextureFilter.Linear, SamplerAddressMode.ClampToEdge, SamplerAddressMode.ClampToEdge)) }
        assertFailsWith<UnsupportedOperationException> { scene.device.resolveSampler(SamplerDescription(TextureFilter.Linear, TextureFilter.Linear, SamplerAddressMode.ClampToEdge, SamplerAddressMode.ClampToEdge)) }
        assertFailsWith<IllegalArgumentException> { scene.device.compileCanonicalShader(RasterTestFixtures.description("triangle.vert"), "test", emptyMap()) }
        assertEquals(before, scene.queue.calls)
        scene.close()
    }

    @Test
    fun unsupportedOffsetsPaddingAndFreshReadFailBeforeImageCommandEmission() {
        val scene = DeviceScene()
        val extra = scene.device.createBuffer(BufferDescription("offset capacity", 100, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        scene.device.encode("reject copies") {
            val before = scene.queue.imageCommandCount()
            assertFailsWith<UnsupportedOperationException> { copyBufferToTexture(GpuBufferView(extra, 4, 84), scene.region) }
            assertFailsWith<UnsupportedOperationException> { copyBufferToTexture(GpuBufferView(extra, 0, 100), scene.region, TextureDataLayout(32)) }
            assertFailsWith<IllegalStateException> { copyTextureToBuffer(scene.region, scene.bufferView) }
            assertEquals(before, scene.queue.imageCommandCount())
        }
        scene.device.awaitIdle(); extra.close(); scene.close()
    }

    @Test
    fun defaultBufferPlacementDoesNotInferCpuAccessFromTransferRole() {
        val queue = RasterTestQueue()
        val device = testVulkanGraphicsDevice(queue)
        val buffer = device.createBuffer(BufferDescription("device-local transfer", 4, setOf(BufferUsage.TransferDestination)))
        assertEquals(VulkanMemoryProperty.DeviceLocal.flag, (buffer as VulkanBuffer).memoryPropertyFlags)
        assertFailsWith<UnsupportedOperationException> { device.readBuffer(GpuBufferView(buffer, 0, 4)) }
        assertTrue(queue.host.calls.isEmpty())
        buffer.close(); device.close()
    }

    @Test
    fun hostReadUnmapsAfterInvalidateOrCopyFailureAndHonorsCoherence() {
        for (stage in listOf("invalidate", "read", "")) {
            val queue = RasterTestQueue()
            if (stage.isEmpty()) queue.bufferState.types = intArrayOf(6, 1)
            val device = testVulkanGraphicsDevice(queue, hostVisibleBuffers = true)
            val buffer = device.createBuffer(BufferDescription("host access", 4, setOf(BufferUsage.TransferDestination)))
            queue.host.failureStage = stage
            if (stage.isEmpty()) assertContentEquals(ByteArray(4), device.readBuffer(GpuBufferView(buffer, 0, 4)))
            else assertSame(queue.host.failure, assertFailsWith<IllegalStateException> { device.readBuffer(GpuBufferView(buffer, 0, 4)) })
            assertEquals(1, queue.host.calls.count { it.startsWith("unmap:") })
            assertEquals(if (stage.isEmpty()) 0 else 1, queue.host.calls.count { it.startsWith("invalidate:") })
            queue.host.failureStage = ""
            buffer.close(); device.close()
        }
    }

    @Test
    fun successfulMapReturningZeroStillUnmapsAndUnavailableMemoryFailsBeforeAllocation() {
        val queue = RasterTestQueue()
        val host = object : VulkanHostMemoryFunctions by queue.host {
            override fun mapWholeAllocation(memory: Long, allocationSizeBytes: Long): Long { queue.host.mapWholeAllocation(memory, allocationSizeBytes); return 0 }
        }
        val functions = object : VulkanTransferFunctions by queue { override val hostMemoryFunctions = host }
        val device = testVulkanGraphicsDevice(functions, hostVisibleBuffers = true)
        val buffer = device.createBuffer(BufferDescription("zero mapping", 4, setOf(BufferUsage.TransferDestination)))
        assertFailsWith<IllegalStateException> { device.readBuffer(GpuBufferView(buffer, 0, 4)) }
        assertEquals(1, queue.host.calls.count { it.startsWith("unmap:") })
        buffer.close(); device.close()
        val unavailable = RasterTestQueue().apply { bufferState.types = intArrayOf(1) }
        val other = testVulkanGraphicsDevice(unavailable, hostVisibleBuffers = true)
        assertFailsWith<UnsupportedOperationException> { other.createBuffer(BufferDescription("no host memory", 4, setOf(BufferUsage.TransferDestination))) }
        assertFalse(unavailable.calls.any { it.startsWith("allocateBuffer:") })
        other.close()
    }
}

private class DeviceScene(
    positionInput: Boolean = false,
    vertexState: VertexState = VertexState.Empty,
) {
    val queue = RasterTestQueue()
    val device = testVulkanGraphicsDevice(queue, facts(positionInput), hostVisibleBuffers = true)
    val texture = device.createTexture(textureDescription())
    private val view = device.createTextureView(texture, TextureViewDescription())
    val target = device.createAttachmentView(view)
    val region = ImageRegion.Texture(texture)
    private val buffer = device.createBuffer(BufferDescription("CPU-readable", 84, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
    val bufferView = GpuBufferView(buffer, 0, 84)
    val vertices = if (positionInput) device.createBuffer(BufferDescription("positions", 160, setOf(BufferUsage.Vertex, BufferUsage.TransferDestination))) else null
    private val layout = if (positionInput) device.createPipelineLayout(VertexTestFixtures.layoutDescription()) else null
    private val modules = if (positionInput) listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { device.createShaderModule(VertexTestFixtures.description(it)) }
        else listOf("triangle.vert", "constant.frag").map { device.createShaderModule(RasterTestFixtures.description(it)) }
    private val stages = device.createShaderStages(ShaderStagesDescription(modules, "consumer"))
    val pipeline = RenderPipelineDescription("public consumer", stages, layout, vertex = vertexState, colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)))
    val pass = RenderPassDescription("full clear", colorAttachments = listOf(RenderPassAttachment(target, AttachmentOperations(AttachmentLoadOperation.Clear(Color(0.0F, 0.0F, 1.0F, 1.0F)), AttachmentStoreOperation.Store))))
    val clearBytes = ByteArray(84) { if (it % 4 >= 2) 255.toByte() else 0 }

    fun close() = heckerpowered.render.terminateOnFailure { texture.close(); buffer.close(); vertices?.close(); layout?.close(); stages.close(); modules.asReversed().forEach { it.close() }; device.close() }

    companion object {
        fun facts(positionInput: Boolean = false): List<ShaderInterfaceArtifact> = if (positionInput) listOf(ShaderStage.Vertex, ShaderStage.Fragment).map(VertexTestFixtures::artifact)
            else listOf("triangle.vert", "constant.frag").map(RasterTestFixtures::artifact)
        fun textureDescription(): TextureDescription = TextureDescription("target", 7, 3, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.ColorAttachment, TextureUsage.TransferSource, TextureUsage.TransferDestination))
    }
}
