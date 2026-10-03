/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.command

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.color.Color
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.pass.*
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.pipeline.*
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.pipeline.vertex.*
import heckerpowered.render.resource.buffer.*
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.*
import heckerpowered.render.vulkan.function.*
import heckerpowered.render.vulkan.pipeline.*
import heckerpowered.render.vulkan.resource.*
import heckerpowered.render.vulkan.shader.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.test.*

class VulkanRasterCommandsTest {
    @Test
    fun declaredVertexPinsAndRepeatedPrepassBarriersFollowEachUploadBeforeDrawing() {
        val scene = RasterScene()
        val buffer = scene.vertex(96)
        val view = GpuBufferView(buffer, 4, 76)
        val unused = scene.vertex(12)
        val resources = RenderPassResources(vertexBuffers = listOf(view, GpuBufferView(unused, 0, 12)))
        val pipeline = scene.positionPipeline(VertexTestFixtures.vertexState(2, 32))
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            repeat(2) {
                writeBuffer(view, ByteArray(76))
                raster.renderPass(scene.description(), resources) {
                    assertFailsWith<IllegalStateException> { unused.requireUnused() }
                    bindVertexBuffer(2, view)
                    bindPipeline(pipeline)
                    pushConstants(setOf(ShaderStage.Vertex, ShaderStage.Fragment), VertexTestFixtures.pushBytes())
                    draw(3)
                    bindPipeline(scene.pipeline)
                    pushConstants(fragmentStage, pushBytes())
                    draw(3)
                    bindPipeline(pipeline)
                    pushConstants(setOf(ShaderStage.Vertex, ShaderStage.Fragment), VertexTestFixtures.pushBytes())
                    draw(3)
                }
            }
        }
        val trace = scene.queue.calls.filter { it.startsWith("copyBuffer:") || it.startsWith("vertexBarrier:") || it == "beginPass" || it.startsWith("vertexBind:") || it.startsWith("bindPipeline:") || it.startsWith("draw:") || it == "endPass" }
        assertEquals(4, trace.count { it.startsWith("vertexBarrier:") })
        val firstBegin = trace.indexOf("beginPass")
        assertTrue(trace[0].startsWith("copyBuffer:"))
        assertTrue(trace[firstBegin - 2].startsWith("vertexBarrier:"))
        assertTrue(trace[firstBegin - 1].startsWith("vertexBarrier:"))
        assertTrue(trace.any { it == "vertexBind:2:${buffer.handle}:4" })
        val positionHandle = pipeline.requireHandle(scene.queue.pipelineFunctions.deviceIdentity)
        val proceduralHandle = scene.pipeline.requireHandle(scene.queue.pipelineFunctions.deviceIdentity)
        val onePass = listOf(
            "copyBuffer", "vertexBarrier:${buffer.handle}:4:76", "vertexBarrier:${unused.handle}:0:12", "beginPass",
            "vertexBind:2:${buffer.handle}:4", "bindPipeline:$positionHandle", "draw:3:0:1:0",
            "bindPipeline:$proceduralHandle", "draw:3:0:1:0", "bindPipeline:$positionHandle", "draw:3:0:1:0", "endPass",
        )
        assertEquals(onePass + onePass, trace.map { if (it.startsWith("copyBuffer:")) "copyBuffer" else it })
        assertFailsWith<IllegalStateException> { buffer.requireUnused() }
        assertFailsWith<IllegalStateException> { unused.requireUnused() }
        complete(session, scene.queue)
        buffer.requireUnused(); unused.requireUnused(); scene.close()
    }

    @Test
    fun viewLocalDrawBoundsUseAttributeEndsInsteadOfTrailingStridePadding() {
        for ([offset, sufficient] in listOf(0 to 76L, 12 to 88L)) {
            val scene = RasterScene()
            val buffer = scene.vertex(160)
            val pipeline = scene.positionPipeline(VertexTestFixtures.vertexState(2, 32, offset))
            val session = VulkanTransferSession.create(scene.queue)
            session.record {
                raster.renderPass(scene.description(), RenderPassResources(vertexBuffers = listOf(GpuBufferView(buffer, 4, 140)))) {
                    bindPipeline(pipeline)
                    pushConstants(setOf(ShaderStage.Vertex, ShaderStage.Fragment), VertexTestFixtures.pushBytes())
                    bindVertexBuffer(2, GpuBufferView(buffer, 4, sufficient))
                    draw(3)
                    bindVertexBuffer(2, GpuBufferView(buffer, 4, sufficient - 1))
                    assertFailsWith<IllegalArgumentException> { draw(3) }
                    bindVertexBuffer(2, GpuBufferView(buffer, 4, sufficient + 32))
                    draw(3, 1)
                    assertFailsWith<IllegalArgumentException> { draw(3, 2) }
                    draw(0, Int.MAX_VALUE)
                    assertFailsWith<IllegalArgumentException> { draw(-1) }
                }
            }
            complete(session, scene.queue); scene.close()
        }
    }

    @Test
    fun floatAlignmentCombinesPipelineAndViewAndOnlyChecksAddressesActuallyFetched() {
        val scene = RasterScene()
        val buffer = scene.vertex(128)
        val compensating = scene.positionPipeline(VertexTestFixtures.vertexState(stride = 16, offset = 3))
        val movingMisaligned = scene.positionPipeline(VertexTestFixtures.vertexState(stride = 15, offset = 3))
        val simple = scene.positionPipeline(VertexTestFixtures.vertexState(stride = 16))
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            raster.renderPass(scene.description(), RenderPassResources(vertexBuffers = listOf(GpuBufferView(buffer, 0, 128)))) {
                bindVertexBuffer(0, GpuBufferView(buffer, 1, 100))
                bindPipeline(compensating)
                pushConstants(setOf(ShaderStage.Vertex, ShaderStage.Fragment), VertexTestFixtures.pushBytes())
                draw(3)
                bindPipeline(simple)
                assertFailsWith<IllegalArgumentException> { draw(3) }
                bindPipeline(movingMisaligned)
                draw(1)
                assertFailsWith<IllegalArgumentException> { draw(2) }
                assertFailsWith<IllegalArgumentException> { draw(1, 1) }
                draw(1, 4)
                draw(0)
            }
        }
        assertEquals(4, scene.queue.calls.count { it.startsWith("draw:") })
        complete(session, scene.queue); scene.close()
    }

    @Test
    fun vertexBindingCoveragePreservesAdjacentUnionAndRejectsHolesBeforeEmission() {
        val scene = RasterScene()
        val buffer = scene.vertex(80)
        val pipeline = scene.positionPipeline()
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            for (middle in listOf(12L, 13L)) {
                val resources = RenderPassResources(vertexBuffers = listOf(GpuBufferView(buffer, 0, 12), GpuBufferView(buffer, middle, 36 - middle)))
                raster.renderPass(scene.description(), resources) {
                    val before = scene.queue.calls.size
                    if (middle == 13L) {
                        assertFailsWith<IllegalArgumentException> { bindVertexBuffer(0, GpuBufferView(buffer, 0, 36)) }
                        assertEquals(before, scene.queue.calls.size)
                    } else {
                        bindVertexBuffer(0, GpuBufferView(buffer, 0, 36))
                        bindPipeline(pipeline)
                        pushConstants(setOf(ShaderStage.Vertex, ShaderStage.Fragment), VertexTestFixtures.pushBytes())
                        draw(3)
                    }
                    assertFailsWith<IllegalArgumentException> { bindVertexBuffer(-1, GpuBufferView(buffer, 0, 12)) }
                    assertFailsWith<IllegalArgumentException> { bindVertexBuffer(0, GpuBufferView(scene.buffer, 0, 4)) }
                }
            }
        }
        complete(session, scene.queue); scene.close()
    }

    @Test
    fun vertexAndFragmentOriginalPushMembersNeedTheirOwnByteHistory() {
        val scene = RasterScene()
        val buffer = scene.vertex(36)
        val pipeline = scene.positionPipeline()
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            raster.renderPass(scene.description(), RenderPassResources(vertexBuffers = listOf(GpuBufferView(buffer, 0, 36)))) {
                bindVertexBuffer(0, GpuBufferView(buffer, 0, 36)); bindPipeline(pipeline)
                val bothStages = setOf(ShaderStage.Vertex, ShaderStage.Fragment)
                assertEquals(bothStages.flatMap { stage -> listOf(VulkanPushConstantRead(stage, 0, 64), VulkanPushConstantRead(stage, 64, 16)) }, pipeline.resourceInterface.pushConstantReads)
                assertFailsWith<IllegalArgumentException> { pushConstants(fragmentStage, VertexTestFixtures.pushBytes()) }
                assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Vertex), VertexTestFixtures.pushBytes()) }
                assertTrue(scene.queue.pushes.isEmpty())
                assertFailsWith<IllegalStateException> { draw(3) }
                val matrix = VertexTestFixtures.pushBytes().apply { limit(64) }
                pushConstants(bothStages, matrix)
                assertFailsWith<IllegalStateException> { draw(3) }
                assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Vertex), ByteBuffer.allocate(16), 64) }
                assertFailsWith<IllegalArgumentException> { pushConstants(fragmentStage, ByteBuffer.allocate(16), 64) }
                assertEquals(1, scene.queue.pushes.size)
                assertFailsWith<IllegalStateException> { draw(3) }
                pushConstants(bothStages, ByteBuffer.allocate(16), 64)
                draw(3)
            }
        }
        complete(session, scene.queue); scene.close()
    }

    @Test
    fun prepassBarrierAndCaughtVertexBindFailuresDiscardRecordingAndReleaseAllPins() {
        for (stage in listOf("vertexBarrier", "vertexBind")) {
            val scene = RasterScene()
            val buffer = scene.vertex(36)
            val pipeline = scene.positionPipeline()
            val session = VulkanTransferSession.create(scene.queue)
            scene.queue.failureStage = stage
            assertFailsWith<IllegalStateException> {
                session.record {
                    raster.renderPass(scene.description(), RenderPassResources(vertexBuffers = listOf(GpuBufferView(buffer, 0, 36)))) {
                        if (stage == "vertexBind") {
                            bindPipeline(pipeline)
                            assertFailsWith<IllegalStateException> { bindVertexBuffer(0, GpuBufferView(buffer, 0, 36)) }
                        }
                    }
                }
            }
            assertTrue(scene.queue.calls.none { it.startsWith("submit:") })
            assertEquals(1, scene.queue.calls.count { it.startsWith("destroyRecording:") })
            if (stage == "vertexBarrier") assertTrue(scene.queue.calls.none { it == "beginPass" })
            buffer.requireUnused(); session.close(); scene.queue.failureStage = ""; scene.close()
        }
    }

    @Test
    fun vertexAccessAndZeroCountMissingBindingsRejectBeforeNativeCommands() {
        val scene = RasterScene()
        val buffer = scene.vertex(36)
        val pipeline = scene.positionPipeline()
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            raster.renderPass(scene.description(), RenderPassResources(vertexBuffers = listOf(GpuBufferView(buffer, 0, 36)))) {
                bindPipeline(pipeline)
                pushConstants(setOf(ShaderStage.Vertex, ShaderStage.Fragment), VertexTestFixtures.pushBytes())
                val before = scene.queue.calls.toList()
                assertFailsWith<IllegalStateException> { draw(0) }
                scene.queue.bufferState.accessAllowed = false
                assertFailsWith<IllegalStateException> { bindVertexBuffer(0, GpuBufferView(buffer, 0, 36)) }
                scene.queue.bufferState.accessAllowed = true
                assertEquals(before, scene.queue.calls)
                bindVertexBuffer(0, GpuBufferView(buffer, 0, 36))
                draw(0)
            }
        }
        complete(session, scene.queue); scene.close()
    }

    @Test
    fun unusedForeignAndClosedVertexDeclarationsRejectBeforeNativePassAllocation() {
        val scene = RasterScene()
        val foreignScene = RasterScene()
        val foreign = foreignScene.vertex(36)
        val closed = scene.vertex(36)
        closed.close()
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            val before = scene.queue.calls.toList()
            assertFailsWith<IllegalArgumentException> { raster.renderPass(scene.description(), RenderPassResources(vertexBuffers = listOf(GpuBufferView(foreign, 0, 36)))) {} }
            assertFailsWith<IllegalStateException> { raster.renderPass(scene.description(), RenderPassResources(vertexBuffers = listOf(GpuBufferView(closed, 0, 36)))) {} }
            assertEquals(before, scene.queue.calls)
        }
        complete(session, scene.queue); foreignScene.close(); scene.close()
    }

    @Test
    fun fullClearAllowsSameRecordingReadbackAndRetiresTemporariesBeforePins() {
        val scene = RasterScene()
        val session = VulkanTransferSession.create(scene.queue)
        lateinit var readback: VulkanBufferReadback
        scene.queue.onDestroyFramebuffer = { assertFailsWith<IllegalStateException> { scene.texture.recordUse(0) } }
        session.record {
            raster.renderPass(scene.description()) { bindPipeline(scene.pipeline); pushConstants(fragmentStage, pushBytes()); draw(3) }
            readback = images.readTexture(ImageRegion.Texture(scene.texture))
        }
        assertFailsWith<IllegalStateException> { readback.readBytes() }
        assertFalse(session.pollCompletion())
        scene.queue.completion = VulkanFenceStatus.Complete
        assertTrue(session.pollCompletion())
        assertContentEquals(ByteArray(7 * 3 * 4) { if (it % 4 == 3) 255.toByte() else 0 }, readback.readBytes())
        val tail = scene.queue.calls.dropWhile { !it.startsWith("destroyRecording:") }
        assertTrue(tail[1].startsWith("destroyFramebuffer:"))
        assertTrue(tail[2].startsWith("destroyPass:"))
        val nextUse = scene.texture.recordUse(0)
        assertTrue(nextUse.contentsDefined)
        nextUse.discardRecording()
        session.close()
        scene.close()
    }

    @Test
    fun transfersAndRepeatedPassesHaveBoundaryPreparationAtTheirExecutionPositions() {
        val scene = RasterScene()
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            images.writeTexture(ImageRegion.Texture(scene.texture), ByteArray(84) { 73 })
            raster.renderPass(scene.description()) {}
            raster.renderPass(scene.description()) {}
            images.readTexture(ImageRegion.Texture(scene.texture))
        }
        val boundaries = scene.queue.calls.filter { it.startsWith("imageBarrier:") || it.startsWith("rasterBarrier:") || it == "beginPass" || it == "endPass" || it.startsWith("readback:") }
        assertEquals(listOf("imageBarrier:1000:false", "rasterBarrier:1000:true", "beginPass", "endPass", "rasterBarrier:1000:true", "beginPass", "endPass", "imageBarrier:1000:true", "readback:1000:7:3"), boundaries)
        complete(session, scene.queue)
        scene.close()
    }

    @Test
    fun rejectsCapturedOuterTransfersNestedPassesAndExpiredPassObjects() {
        val scene = RasterScene()
        val session = VulkanTransferSession.create(scene.queue)
        lateinit var escaped: VulkanRasterPass
        session.record {
            val outer = this
            raster.renderPass(scene.description()) {
                escaped = this
                assertFailsWith<IllegalStateException> { outer.images.readTexture(ImageRegion.Texture(scene.texture)) }
                assertFailsWith<IllegalStateException> { outer.readBuffer(GpuBufferView(scene.buffer, 0, 4)) }
                assertFailsWith<IllegalStateException> { outer.raster.renderPass(scene.description()) {} }
                bindPipeline(scene.pipeline)
                pushConstants(fragmentStage, pushBytes())
                draw(3)
            }
            assertFailsWith<IllegalStateException> { escaped.draw(3) }
            assertFailsWith<IllegalStateException> { escaped.withScissor(ScissorRectangle.Empty) {} }
        }
        complete(session, scene.queue)
        assertFailsWith<IllegalStateException> { escaped.bindPipeline(scene.pipeline) }
        scene.close()
    }

    @Test
    fun caughtPassCallbackFailureDiscardsWholeRecordingWithoutEndOrDefinedCommit() {
        val scene = RasterScene()
        val session = VulkanTransferSession.create(scene.queue)
        val expected = IllegalArgumentException("callback")
        assertFailsWith<IllegalStateException> {
            session.record {
                assertSame(expected, assertFailsWith<IllegalArgumentException> { raster.renderPass(scene.description()) { throw expected } })
            }
        }
        assertFalse("endPass" in scene.queue.calls)
        assertTrue(scene.queue.calls.none { it.startsWith("submit:") })
        val use = scene.texture.recordUse(0)
        assertFalse(use.contentsDefined)
        use.discardRecording()
        session.close()
        scene.close()
    }

    @Test
    fun unsupportedPartialAreaSubresourceAndOperationsFailBeforeNativePassCreation() {
        val scene = RasterScene()
        val session = VulkanTransferSession.create(scene.queue)
        assertFailsWith<UnsupportedOperationException> {
            createVulkanTexture(scene.queue.imageFunctions, TextureDescription("layers", 7, 3, format = TextureFormat.Rgba8UnsignedNormalized, arrayLayerCount = 2, usage = setOf(TextureUsage.ColorAttachment)))
        }
        assertFailsWith<UnsupportedOperationException> {
            createVulkanTexture(scene.queue.imageFunctions, TextureDescription("mips", 7, 3, format = TextureFormat.Rgba8UnsignedNormalized, mipLevelCount = 2, usage = setOf(TextureUsage.ColorAttachment)))
        }
        val vertex = createVulkanBuffer(scene.queue.bufferFunctions, BufferDescription("unused indices", 4, setOf(BufferUsage.Index)))
        val before = scene.queue.calls.count { it == "createPass" }
        session.record {
            for (description in listOf(scene.description().derive(renderArea = RenderArea(0, 0, 4, 2)),
                scene.description(operations = AttachmentOperations.Default),
                scene.description(operations = AttachmentOperations(AttachmentLoadOperation.Clear(Color.TransparentBlack), AttachmentStoreOperation.Discard)))) {
                assertFailsWith<UnsupportedOperationException> { raster.renderPass(description) {} }
            }
            assertFailsWith<UnsupportedOperationException> { raster.renderPass(scene.description(), RenderPassResources(indexBuffers = listOf(GpuBufferView(vertex, 0, 4)))) {} }
        }
        assertEquals(before, scene.queue.calls.count { it == "createPass" })
        complete(session, scene.queue)
        vertex.close(); scene.close()
    }

    @Test
    fun pipelineDeviceAndLifetimeChecksPrecedeNativeBinding() {
        val scene = RasterScene()
        val foreign = RasterScene()
        val closed = scene.pipeline(16).also { it.close() }
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            raster.renderPass(scene.description()) {
                val before = scene.queue.calls.count { it.startsWith("bindPipeline:") }
                assertFailsWith<IllegalArgumentException> { bindPipeline(foreign.pipeline) }
                assertFailsWith<IllegalStateException> { bindPipeline(closed) }
                assertEquals(before, scene.queue.calls.count { it.startsWith("bindPipeline:") })
            }
        }
        complete(session, scene.queue)
        scene.close(); foreign.close()
    }

    @Test
    fun pushCapturesHeapDirectReadonlySlicesAndNativeFrameBytesWithoutChangingInputState() {
        val scene = RasterScene()
        val session = VulkanTransferSession.create(scene.queue)
        val expected = ByteArray(16) { (it + 19).toByte() }
        session.record {
            raster.renderPass(scene.description()) {
                bindPipeline(scene.pipeline)
                for (direct in listOf(false, true)) {
                    val storage = if (direct) ByteBuffer.allocateDirect(20) else ByteBuffer.allocate(20)
                    storage.position(2); storage.put(expected); storage.limit(18); storage.position(2)
                    val readonly = storage.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN)
                    pushConstants(fragmentStage, readonly)
                    assertEquals(2, readonly.position()); assertEquals(18, readonly.limit()); assertEquals(ByteOrder.BIG_ENDIAN, readonly.order())
                    storage.put(2, 0)
                    assertContentEquals(expected, scene.queue.pushes.last())
                }
                val memory = MemoryStack(128)
                memory.frame {
                    val address = reserve(16, 4)
                    val buffer = asByteBuffer(address, 16)
                    buffer.put(expected)
                    pushConstants(fragmentStage, address, 16)
                    buffer.put(0, 0)
                }
                assertContentEquals(expected, scene.queue.pushes.last())
                draw(3)
            }
        }
        complete(session, scene.queue)
        scene.close()
    }

    @Test
    fun pushHistorySurvivesPipelineSwitchAndRequiresRewritingOnlyIncompatibleOverwrites() {
        val scene = RasterScene()
        val reserved = scene.pipeline(32)
        val equivalent = scene.pipeline(16)
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            raster.renderPass(scene.description()) {
                bindPipeline(scene.pipeline)
                assertFailsWith<IllegalStateException> { draw(0) }
                pushConstants(fragmentStage, pushBytes())
                bindPipeline(reserved); bindPipeline(scene.pipeline); draw(3)
                bindPipeline(equivalent); draw(3)
                bindPipeline(reserved)
                pushConstants(fragmentStage, ByteBuffer.allocate(4), 4)
                bindPipeline(scene.pipeline)
                assertFailsWith<IllegalStateException> { draw(3) }
                pushConstants(fragmentStage, ByteBuffer.allocate(4), 4)
                draw(3)
                assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Vertex), ByteBuffer.allocate(4)) }
                assertFailsWith<IllegalArgumentException> { pushConstants(fragmentStage, ByteBuffer.allocate(0)) }
            }
        }
        complete(session, scene.queue)
        scene.close()
    }

    @Test
    fun absoluteReflectedPushOffsetRequiresItsBytesButNotUnusedReservedBytes() {
        val scene = RasterScene()
        val offset = scene.pipeline(32, "offset.frag")
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            raster.renderPass(scene.description()) {
                bindPipeline(offset)
                pushConstants(fragmentStage, pushBytes(), 0)
                assertFailsWith<IllegalStateException> { draw(3) }
                pushConstants(fragmentStage, pushBytes(), 16)
                draw(3)
            }
        }
        complete(session, scene.queue)
        scene.close()
    }

    @Test
    fun viewportAndScissorScopesRestoreEnclosingStateAndValidateEvenEmptyDraws() {
        val scene = RasterScene()
        val session = VulkanTransferSession.create(scene.queue)
        session.record {
            raster.renderPass(scene.description()) {
                bindPipeline(scene.pipeline); pushConstants(fragmentStage, pushBytes())
                val temporary = Viewport(1.0F, 1.0F, 5.0F, 1.0F)
                withViewport(temporary) {
                    withScissor(ScissorRectangle(-2, 0, 5, 2)) {
                        withScissor(ScissorRectangle(2, 1, 9, 9)) { draw(3) }
                        draw(3)
                    }
                    draw(3)
                }
                draw(3)
                assertFailsWith<IllegalArgumentException> { draw(-1) }
                scene.queue.viewportSupported = false
                assertFailsWith<UnsupportedOperationException> { withScissor(ScissorRectangle.Empty) { draw(0) } }
                scene.queue.viewportSupported = true
            }
        }
        assertEquals(listOf(ScissorRectangle(2, 1, 1, 1), ScissorRectangle(0, 0, 3, 2), ScissorRectangle(0, 0, 7, 3), ScissorRectangle(0, 0, 7, 3)), scene.queue.regionSelections.map { it.second })
        assertEquals(Viewport(0.0F, 0.0F, 7.0F, 3.0F), scene.queue.regionSelections.last().first)
        complete(session, scene.queue)
        scene.close()
    }

    @Test
    fun nativeCommandFailureCaughtInsidePassStillInvalidatesRecordingAndDestroysTemporaries() {
        for (stage in listOf("rasterBarrier", "beginPass", "push", "draw", "endPass")) {
            val scene = RasterScene()
            val session = VulkanTransferSession.create(scene.queue)
            scene.queue.failureStage = stage
            assertFailsWith<IllegalStateException> {
                session.record {
                    assertFailsWith<IllegalStateException> {
                        raster.renderPass(scene.description()) {
                            bindPipeline(scene.pipeline)
                            if (stage == "push" || stage == "draw") assertFailsWith<IllegalStateException> { pushConstants(fragmentStage, pushBytes()); draw(3) }
                        }
                    }
                }
            }
            scene.queue.failureStage = ""
            assertTrue(scene.queue.calls.none { it.startsWith("submit:") })
            assertTrue(scene.queue.calls.any { it.startsWith("destroyFramebuffer:") })
            val use = scene.texture.recordUse(0); assertFalse(use.contentsDefined); use.discardRecording()
            session.close(); scene.close()
        }
    }

    @Test
    fun unknownSubmitThenIdleReleasesRecordedPinsWithoutPublishingDefinedOrReadback() {
        for (stage in listOf("submit", "submitAfter")) {
            val scene = RasterScene()
            val session = VulkanTransferSession.create(scene.queue)
            scene.queue.failureStage = stage
            lateinit var readback: VulkanBufferReadback
            assertFailsWith<IllegalStateException> {
                session.record { raster.renderPass(scene.description()) { bindPipeline(scene.pipeline) }; readback = images.readTexture(ImageRegion.Texture(scene.texture)) }
            }
            scene.queue.failureStage = ""
            VulkanTransferSession.awaitQueueCompletion(scene.queue, listOf(session))
            assertFailsWith<IllegalStateException> { readback.readBytes() }
            val use = scene.texture.recordUse(0); assertFalse(use.contentsDefined); use.discardRecording()
            session.close()
            val again = VulkanTransferSession.create(scene.queue)
            again.record { raster.renderPass(scene.description()) {}; images.readTexture(ImageRegion.Texture(scene.texture)) }
            assertTrue(scene.queue.calls.any { it == "rasterBarrier:1000:false" })
            complete(again, scene.queue)
            scene.close()
        }
    }

    @Test
    fun knownAcceptedWithQueryOrPromotionFailureCanCommitAfterActualQueueProof() {
        for (promotionFailure in listOf(false, true)) {
            val scene = RasterScene()
            val session = VulkanTransferSession.create(scene.queue)
            lateinit var readback: VulkanBufferReadback
            if (promotionFailure) scene.queue.onSubmit = { scene.queue.pipelineAccess = false }
            if (promotionFailure) assertFailsWith<IllegalStateException> {
                session.record { raster.renderPass(scene.description()) { bindPipeline(scene.pipeline) }; readback = images.readTexture(ImageRegion.Texture(scene.texture)) }
            } else {
                session.record { raster.renderPass(scene.description()) {}; readback = images.readTexture(ImageRegion.Texture(scene.texture)) }
                scene.queue.failureStage = "poll"
                assertFailsWith<IllegalStateException> { session.pollCompletion() }
            }
            scene.queue.pipelineAccess = true; scene.queue.failureStage = ""; scene.queue.onSubmit = {}
            VulkanTransferSession.awaitQueueCompletion(scene.queue, listOf(session))
            assertEquals(84, readback.readBytes().size)
            val use = scene.texture.recordUse(0); assertTrue(use.contentsDefined); use.discardRecording()
            session.close(); scene.close()
        }
    }

    @Test
    fun failedWaitAndForeignQueueProofKeepReservationsUntilSuccessfulBoundWait() {
        val scene = RasterScene()
        val foreign = RasterTestQueue()
        val session = VulkanTransferSession.create(scene.queue)
        session.record { raster.renderPass(scene.description()) { bindPipeline(scene.pipeline) } }
        assertFailsWith<IllegalArgumentException> { VulkanTransferSession.awaitQueueCompletion(foreign, listOf(session)) }
        assertTrue(foreign.calls.none { it == "idle" })
        scene.queue.failureStage = "idle"
        assertFailsWith<IllegalStateException> { VulkanTransferSession.awaitQueueCompletion(scene.queue, listOf(session)) }
        assertFailsWith<IllegalStateException> { scene.texture.recordUse(0) }
        assertTrue(scene.queue.calls.none { it.startsWith("destroyRecording:") })
        scene.queue.failureStage = ""
        VulkanTransferSession.awaitQueueCompletion(scene.queue, listOf(session))
        session.close(); scene.close()
    }

    @Test
    fun proofCapturesOnlyEarlierRecordingsAndRejectsStaleOrActiveRecordings() {
        val scene = RasterScene()
        val first = VulkanTransferSession.create(scene.queue)
        first.record { raster.renderPass(scene.description()) {} }
        val secondTexture = scene.queue.texture()
        val second = VulkanTransferSession.create(scene.queue)
        scene.queue.duringIdle = { second.record { images.writeTexture(ImageRegion.Texture(secondTexture), ByteArray(84)) } }
        VulkanTransferSession.awaitQueueCompletion(scene.queue, listOf(first))
        assertFailsWith<IllegalStateException> { secondTexture.recordUse(0) }
        scene.queue.duringIdle = {}
        complete(second, scene.queue); first.close(); secondTexture.close()
        val ready = VulkanTransferSession.create(scene.queue)
        scene.queue.duringIdle = { ready.discard() }
        assertFailsWith<IllegalStateException> { VulkanTransferSession.awaitQueueCompletion(scene.queue, listOf(ready)) }
        ready.close(); scene.queue.duringIdle = {}
        val submittedLater = VulkanTransferSession.create(scene.queue)
        scene.queue.duringIdle = { submittedLater.record { raster.renderPass(scene.description()) {} } }
        assertFailsWith<IllegalStateException> { VulkanTransferSession.awaitQueueCompletion(scene.queue, listOf(submittedLater)) }
        assertFailsWith<IllegalStateException> { scene.texture.recordUse(0) }
        scene.queue.duringIdle = {}
        complete(submittedLater, scene.queue)
        val active = VulkanTransferSession.create(scene.queue)
        active.record { assertFailsWith<IllegalStateException> { VulkanTransferSession.awaitQueueCompletion(scene.queue, listOf(active)) } }
        complete(active, scene.queue); scene.close()
    }

    @Test
    fun allocationFailureCleansOwnedNativeObjectsAndCloseNeverWaits() {
        val scene = RasterScene()
        val session = VulkanTransferSession.create(scene.queue)
        scene.queue.failureStage = "createFramebuffer"
        session.record { assertFailsWith<IllegalStateException> { raster.renderPass(scene.description()) {} } }
        scene.queue.failureStage = ""
        complete(session, scene.queue)
        assertTrue(scene.queue.calls.any { it.startsWith("destroyPass:") })
        assertTrue(scene.queue.calls.none { it.startsWith("destroyFramebuffer:") })
        val before = scene.queue.calls.count { it == "idle" || it.startsWith("wait:") || it.startsWith("submit:") }
        session.close(); scene.close()
        assertEquals(before, scene.queue.calls.count { it == "idle" || it.startsWith("wait:") || it.startsWith("submit:") })
    }

    @Test
    fun absentRasterCapabilityAndMismatchedQueueFamilyFailBeforeNativePassCreation() {
        val scene = RasterScene()
        val before = scene.queue.calls.count { it == "createPass" }
        val unavailable = object : VulkanTransferFunctions by scene.queue { override val rasterFunctions: VulkanRasterFunctions? = null }
        val missing = VulkanTransferSession.create(unavailable)
        missing.record { assertFailsWith<UnsupportedOperationException> { raster.renderPass(scene.description()) {} } }
        VulkanTransferSession.awaitQueueCompletion(unavailable, listOf(missing)); missing.close()
        val wrong = object : VulkanRasterFunctions by scene.queue { override val queueFamilyIndex = 9 }
        val group = object : VulkanTransferFunctions by scene.queue { override val rasterFunctions = wrong }
        val mismatch = VulkanTransferSession.create(group)
        mismatch.record { assertFailsWith<IllegalArgumentException> { raster.renderPass(scene.description()) {} } }
        VulkanTransferSession.awaitQueueCompletion(group, listOf(mismatch)); mismatch.close()
        assertEquals(before, scene.queue.calls.count { it == "createPass" })
        scene.close()
    }

    @Test
    fun recordedPendingResourcesAndTemporaryDestructionFailuresHaltWithoutWaitOrShutdown() {
        val classpath = listOf(VulkanRasterCommandsTest::class.java, VulkanTransferSession::class.java, Color::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (mode in listOf("recordedVertex", "pendingVertex", "recordedPipeline", "recordedLayout", "pendingPipeline", "pendingLayout", "destroyFramebuffer", "destroyPass", "destroyRecording", "unresolvedSession")) {
            val output = File.createTempFile("vulkan-raster-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanRasterFatalProbe::class.java.name, mode)
                    .redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, mode)
                val log = output.readText()
                assertEquals(1, process.exitValue(), log)
                assertTrue("raster-fatal-start" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("shutdown-hook" in log, log)
                assertFalse("idle" in log, log)
                assertFalse("wait:" in log, log)
                if (mode.endsWith("Pipeline")) assertFalse("destroyPipeline:" in log, log)
                if (mode.endsWith("Layout")) assertFalse("destroyLayout:" in log, log)
            } finally { output.delete() }
        }
    }
}

private val fragmentStage = setOf(ShaderStage.Fragment)
private fun pushBytes(): ByteBuffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply { putFloat(1.0F); putFloat(0.0F); putFloat(0.0F); putFloat(1.0F); flip() }
private fun complete(session: VulkanTransferSession, functions: RasterTestQueue) { functions.completion = VulkanFenceStatus.Complete; check(session.pollCompletion()); session.close() }

private class RasterScene {
    val queue = RasterTestQueue()
    val texture = queue.texture()
    val target = createVulkanAttachmentView(queue.imageFunctions, createVulkanTextureView(queue.imageFunctions, texture, TextureViewDescription()))
    val buffer = createVulkanBuffer(queue.bufferFunctions, BufferDescription("outside transfer", 4, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
    private val modules = mutableListOf<VulkanShaderModule>()
    private val stageSets = mutableListOf<ShaderStages>()
    private val layouts = mutableListOf<PipelineLayout>()
    private val pipelines = mutableListOf<VulkanRenderPipeline>()
    private val geometryBuffers = mutableListOf<VulkanBuffer>()
    val pipeline = pipeline(16)

    fun vertex(sizeBytes: Long): VulkanBuffer = (createVulkanBuffer(queue.bufferFunctions, BufferDescription("vertex", sizeBytes, setOf(BufferUsage.Vertex, BufferUsage.TransferDestination))) as VulkanBuffer).also { geometryBuffers.add(it) }

    fun positionPipeline(vertex: VertexState = VertexTestFixtures.vertexState()): VulkanRenderPipeline {
        val shaderModules = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
            (createVulkanShaderModule(queue.pipelineFunctions.shaderFunctions, VertexTestFixtures.description(stage), VertexTestFixtures.artifact(stage)) as VulkanShaderModule).also { modules.add(it) }
        }
        val stages = createVulkanShaderStages(queue.pipelineFunctions.shaderFunctions, ShaderStagesDescription(shaderModules, "direct position")).also { stageSets.add(it) }
        val layout = createVulkanPipelineLayout(queue.pipelineFunctions, VertexTestFixtures.layoutDescription()).also { layouts.add(it) }
        return (createVulkanRenderPipeline(queue.pipelineFunctions, RenderPipelineDescription("direct vertex", stages, layout, vertex = vertex, colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)))) as VulkanRenderPipeline).also { pipelines.add(it) }
    }


    fun pipeline(sizeBytes: Int, fragment: String = "color.frag"): VulkanRenderPipeline {
        val shaderModules = listOf("triangle.vert", fragment).map { name -> (createVulkanShaderModule(queue.pipelineFunctions.shaderFunctions, RasterTestFixtures.description(name), RasterTestFixtures.artifact(name)) as VulkanShaderModule).also { modules.add(it) } }
        val stages = createVulkanShaderStages(queue.pipelineFunctions.shaderFunctions, ShaderStagesDescription(shaderModules, "raster facts")).also { stageSets.add(it) }
        val layout = createVulkanPipelineLayout(queue.pipelineFunctions, PipelineLayoutDescription(pushConstants = PushConstantLayout(listOf(PushConstantRange(fragmentStage, 0, sizeBytes))), label = "raster layout")).also { layouts.add(it) }
        return (createVulkanRenderPipeline(queue.pipelineFunctions, RenderPipelineDescription("raster pipeline", stages, layout, colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)))) as VulkanRenderPipeline).also { pipelines.add(it) }
    }

    fun description(attachment: RenderAttachment = target, operations: AttachmentOperations<Color> = AttachmentOperations(AttachmentLoadOperation.Clear(Color(0.0F, 0.0F, 0.0F, 1.0F)), AttachmentStoreOperation.Store)): RenderPassDescription =
        RenderPassDescription("full raster", colorAttachments = listOf(RenderPassAttachment(attachment, operations)))

    fun close() {
        pipelines.asReversed().forEach { it.close() }
        layouts.asReversed().forEach { it.close() }
        stageSets.asReversed().forEach { it.close() }
        modules.asReversed().forEach { it.close() }
        geometryBuffers.asReversed().forEach { it.close() }
        texture.close(); buffer.close()
    }
}

object VulkanRasterFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val scene = RasterScene()
        scene.queue.printCalls = true
        println("raster-fatal-start")
        val mode = arguments.single()
        val vertex = scene.vertex(36)
        val session = VulkanTransferSession.create(scene.queue)
        if (mode == "unresolvedSession") scene.queue.failureStage = "submitAfter"
        try {
            session.record {
                raster.renderPass(scene.description(), RenderPassResources(vertexBuffers = listOf(GpuBufferView(vertex, 0, 36)))) {
                    if (mode == "recordedVertex") vertex.close()
                    bindPipeline(scene.pipeline)
                    if (mode == "recordedPipeline") scene.pipeline.close()
                    if (mode == "recordedLayout") scene.pipeline.layout.close()
                }
            }
        } catch (failure: IllegalStateException) { if (mode != "unresolvedSession") throw failure }
        when (mode) {
            "pendingVertex" -> vertex.close()
            "pendingPipeline" -> scene.pipeline.close()
            "pendingLayout" -> scene.pipeline.layout.close()
            "destroyFramebuffer", "destroyPass", "destroyRecording" -> { scene.queue.failureStage = mode; scene.queue.completion = VulkanFenceStatus.Complete; session.pollCompletion() }
            "unresolvedSession" -> session.close()
        }
        println("after-close")
    }
}

/** CPU-only transfer/clear and command trace model; it does not emulate shader rasterization or load a Vulkan driver. */
internal class RasterTestQueue : VulkanTransferFunctions, VulkanImageTransferFunctions, VulkanRasterFunctions {
    val bufferState = FakeVulkanBufferFunctions()
    private var nextPipelineObject = 3000L
    private val framebuffers = mutableMapOf<Long, Long>()
    private val views = mutableMapOf<Long, Long>()
    val pushes = mutableListOf<ByteArray>()
    val regionSelections = mutableListOf<Pair<Viewport, ScissorRectangle>>()
    var onDestroyFramebuffer: () -> Unit = {}
    var duringIdle: () -> Unit = {}
    var onSubmit: () -> Unit = {}
    var viewportSupported = true
    var pipelineAccess = true
    override val rasterFunctions get() = this
    override val pipelineFunctions = object : VulkanPipelineFunctions {
        override val shaderFunctions = object : VulkanShaderModuleFunctions {
            override val deviceIdentity = Any()
            override fun checkAccess() { check(pipelineAccess) { "pipeline access failure" } }
            override fun createShaderModule(words: IntBuffer): Long = nextPipelineObject++
            override fun destroyShaderModule(module: Long) = call("destroyModule", "destroyModule:$module")
        }
        override val maxPushConstantsSize = 256
        override fun validateVertexState(state: VertexState) = Unit
        override fun supportsColorTarget(format: TextureFormat, sampleCount: SampleCount) = true
        override fun createPipelineLayout(description: PipelineLayoutDescription): Long { call("layout", "layout"); return nextPipelineObject++ }
        override fun destroyPipelineLayout(layout: Long) = call("destroyLayout", "destroyLayout:$layout")
        override fun createRenderPass(format: TextureFormat, sampleCount: SampleCount): Long { call("createPass", "createPass"); return nextPipelineObject++ }
        override fun destroyRenderPass(renderPass: Long) = call("destroyPass", "destroyPass:$renderPass")
        override fun createGraphicsPipeline(description: RenderPipelineDescription, stages: List<VulkanPipelineShaderStage>, layout: Long, renderPass: Long): Long = nextPipelineObject++
        override fun destroyPipeline(pipeline: Long) = call("destroyPipeline", "destroyPipeline:$pipeline")
    }

    override fun checkAccess() { bufferFunctions.checkAccess(); pipelineFunctions.checkAccess() }
    override fun validateVertexBinding(slot: Int, offsetBytes: Long) { require(slot in 0..30 && offsetBytes >= 0) }
    override fun beforeVertexBufferRead(recording: VulkanTransferRecording, buffer: Long, offsetBytes: Long, sizeBytes: Long) = call("vertexBarrier", "vertexBarrier:$buffer:$offsetBytes:$sizeBytes")
    override fun bindVertexBuffer(recording: VulkanTransferRecording, slot: Int, buffer: Long, offsetBytes: Long) = call("vertexBind", "vertexBind:$slot:$buffer:$offsetBytes")
    override fun validateFramebufferExtent(width: Int, height: Int) { require(width > 0 && height > 0) }
    override fun validateViewport(viewport: Viewport) { if (!viewportSupported) throw UnsupportedOperationException("viewport limit") }
    override fun createFramebuffer(renderPass: Long, imageView: Long, width: Int, height: Int): Long {
        call("createFramebuffer", "createFramebuffer")
        return nextPipelineObject++.also { framebuffers[it] = views.getValue(imageView) }
    }
    override fun destroyFramebuffer(framebuffer: Long) {
        call("destroyFramebuffer", "destroyFramebuffer:$framebuffer")
        onDestroyFramebuffer()
        framebuffers.remove(framebuffer)
    }
    override fun beforeColorAttachment(recording: VulkanTransferRecording, image: Long, contentsDefined: Boolean) {
        call("rasterBarrier", "rasterBarrier:$image:$contentsDefined")
        commands.getValue(recording.commandBuffer).add { if (contentsDefined) check(generalLayouts.getValue(image)); generalLayouts[image] = true }
    }
    override fun beginRenderPass(recording: VulkanTransferRecording, renderPass: Long, framebuffer: Long, width: Int, height: Int, clear: Color) {
        call("beginPass", "beginPass")
        val image = framebuffers.getValue(framebuffer)
        val color = byteArrayOf(clear.red.toUnorm(), clear.green.toUnorm(), clear.blue.toUnorm(), clear.alpha.toUnorm())
        commands.getValue(recording.commandBuffer).add { for (index in pixels.getValue(image).indices) pixels.getValue(image)[index] = color[index % 4] }
    }
    override fun endRenderPass(recording: VulkanTransferRecording) = call("endPass", "endPass")
    override fun bindPipeline(recording: VulkanTransferRecording, pipeline: Long) = call("bindPipeline", "bindPipeline:$pipeline")
    override fun setRegions(recording: VulkanTransferRecording, viewport: Viewport, scissor: ScissorRectangle) { call("regions", "regions"); regionSelections.add(viewport to scissor) }
    override fun pushConstants(recording: VulkanTransferRecording, layout: Long, stages: Set<ShaderStage>, source: ByteBuffer, destinationOffsetBytes: Int) {
        call("push", "push:$destinationOffsetBytes:${source.remaining()}")
        pushes.add(ByteArray(source.remaining()).also { source.duplicate().get(it) })
    }
    override fun draw(recording: VulkanTransferRecording, vertexCount: Int, firstVertex: Int, instanceCount: Int, firstInstance: Int) = call("draw", "draw:$vertexCount:$firstVertex:$instanceCount:$firstInstance")
    override fun awaitQueueIdle() {
        call("idle", "idle")
        for (fence in submissions.keys.toList()) if (executed.add(fence)) for (command in commands.getValue(submissions.getValue(fence))) command()
        duringIdle()
    }
    private fun Float.toUnorm(): Byte = (coerceIn(0.0F, 1.0F) * 255.0F).roundToInt().toByte()
    private var nextBuffer = 7L
    private var nextMemory = 9L
    private var nextImage = 1000L
    private var nextView = 2000L
    private var nextRecording = 1L
    private var nextFence = 100L
    private val bufferSizes = mutableMapOf<Long, Long>()
    private val bufferMemories = mutableMapOf<Long, Long>()
    private val pixels = mutableMapOf<Long, ByteArray>()
    private val generalLayouts = mutableMapOf<Long, Boolean>()
    private val commands = mutableMapOf<Long, MutableList<() -> Unit>>()
    private val submissions = mutableMapOf<Long, Long>()
    private val executed = mutableSetOf<Long>()
    val calls = mutableListOf<String>()
    var failureStage = ""
    var failure: Throwable = IllegalStateException("image transfer failure")
    var completion = VulkanFenceStatus.Pending
    var submission = VulkanSubmissionStatus.Accepted
    var provideImages = true
    var provideHost = true
    var printCalls = false
    var onDestroyRecording: () -> Unit = {}
    override var queueFamilyIndex = 0

    override val bufferFunctions = object : VulkanBufferFunctions by bufferState {
        override fun createBuffer(sizeBytes: Long, usageFlags: Int): Long {
            call("createBuffer", "createBuffer:$sizeBytes:$usageFlags")
            val buffer = nextBuffer++
            bufferSizes[buffer] = sizeBytes
            return buffer
        }
        override fun <R> withBufferMemoryRequirements(buffer: Long, consume: (Long, Long, Int, Boolean) -> R): R = consume((bufferSizes.getValue(buffer) + 127) / 128 * 128, 128, 3, false)
        override fun allocateMemory(sizeBytes: Long, memoryTypeIndex: Int, dedicatedBuffer: Long): Long {
            call("allocateBuffer", "allocateBuffer:$sizeBytes:$memoryTypeIndex")
            val memory = nextMemory++
            host.allocate(memory, sizeBytes)
            return memory
        }
        override fun bindBufferMemory(buffer: Long, memory: Long, offsetBytes: Long) {
            call("bindBuffer", "bindBuffer:$buffer:$memory")
            bufferMemories[buffer] = memory
            host.bind(buffer, memory)
        }
    }
    val host: FakeVulkanHostMemoryFunctions = FakeVulkanHostMemoryFunctions(bufferFunctions)
    override val hostMemoryFunctions get() = if (provideHost) host else null
    override val imageTransferFunctions get() = if (provideImages) this else null
    override val imageFunctions = object : VulkanImageFunctions {
        override val bufferFunctions get() = this@RasterTestQueue.bufferFunctions
        override fun checkTextureSupport(description: TextureDescription, usageFlags: Int) = Unit
        override fun createImage(description: TextureDescription, usageFlags: Int): Long {
            val image = nextImage++
            pixels[image] = ByteArray(description.width * description.height * 4) { 0xCC.toByte() }
            generalLayouts[image] = false
            return image
        }
        override fun <R> withImageMemoryRequirements(image: Long, consume: (Long, Long, Int, Boolean) -> R): R = consume((pixels.getValue(image).size.toLong() + 127) / 128 * 128, 128, 3, false)
        override fun allocateImageMemory(sizeBytes: Long, memoryTypeIndex: Int, dedicatedImage: Long, label: String): Long = nextMemory++
        override fun bindImageMemory(image: Long, memory: Long, label: String) = Unit
        override fun createImageView(image: Long, description: TextureViewDescription): Long = nextView++.also { views[it] = image }
        override fun destroyImageView(view: Long) = call("destroyView", "destroyView:$view")
        override fun destroyImage(image: Long) = call("destroyImage", "destroyImage:$image")
    }

    fun texture(width: Int = 7, height: Int = 3, usage: Set<TextureUsage> = setOf(TextureUsage.Sampled, TextureUsage.ColorAttachment, TextureUsage.TransferSource, TextureUsage.TransferDestination)): VulkanTexture =
        createVulkanTexture(imageFunctions, TextureDescription("image", width, height, format = TextureFormat.Rgba8UnsignedNormalized, usage = usage)) as VulkanTexture

    fun imageBytes(texture: VulkanTexture): ByteArray = pixels.getValue(texture.handle).copyOf()
    fun imageCommandCount(): Int = calls.count { it.startsWith("imageBarrier:") || it.startsWith("upload:") || it.startsWith("readback:") }
    fun barrierBaselines(): List<Boolean> = calls.filter { it.startsWith("imageBarrier:") }.map { it.endsWith(":true") }

    private fun call(stage: String, details: String) {
        calls.add(details)
        if (printCalls) println(details)
        if (failureStage == stage) throw failure
    }

    override fun createRecording(): VulkanTransferRecording {
        val recording = nextRecording++
        commands[recording] = mutableListOf()
        call("createRecording", "createRecording:$recording")
        return VulkanTransferRecording(recording, recording)
    }
    override fun beforeBufferCopy(recording: VulkanTransferRecording) = call("before", "before:${recording.commandBuffer}")
    override fun afterBufferCopies(recording: VulkanTransferRecording) = call("after", "after:${recording.commandBuffer}")
    override fun endRecording(recording: VulkanTransferRecording) = call("end", "end:${recording.commandBuffer}")
    override fun copyBuffer(recording: VulkanTransferRecording, source: Long, sourceOffsetBytes: Long, destination: Long, destinationOffsetBytes: Long, sizeBytes: Long) {
        call("copyBuffer", "copyBuffer:${recording.commandBuffer}")
        commands.getValue(recording.commandBuffer).add { host.copy(source, sourceOffsetBytes, destination, destinationOffsetBytes, sizeBytes) }
    }
    override fun beforeImageTransfer(recording: VulkanTransferRecording, image: Long, contentsDefined: Boolean) {
        call("imageBarrier", "imageBarrier:$image:$contentsDefined")
        commands.getValue(recording.commandBuffer).add {
            if (contentsDefined) check(generalLayouts.getValue(image)) { "Recorded old layout disagrees with simulated GPU layout" }
            generalLayouts[image] = true
        }
    }
    override fun copyBufferToImage(recording: VulkanTransferRecording, buffer: Long, image: Long, width: Int, height: Int) {
        call("upload", "upload:$image:$width:$height")
        commands.getValue(recording.commandBuffer).add {
            val bytes = ByteArray(width * height * 4)
            host.readMappedBytes(bufferMemories.getValue(buffer), bytes)
            bytes.copyInto(pixels.getValue(image))
        }
    }
    override fun copyImageToBuffer(recording: VulkanTransferRecording, image: Long, buffer: Long, width: Int, height: Int) {
        call("readback", "readback:$image:$width:$height")
        commands.getValue(recording.commandBuffer).add { host.writeMappedBytes(bufferMemories.getValue(buffer), pixels.getValue(image)) }
    }
    override fun createFence(): Long { call("createFence", "createFence"); return nextFence++ }
    override fun submit(recording: VulkanTransferRecording, fence: Long): VulkanSubmissionStatus {
        call("submit", "submit:${recording.commandBuffer}")
        if (submission == VulkanSubmissionStatus.Accepted) submissions[fence] = recording.commandBuffer
        onSubmit()
        if (failureStage == "submitAfter") error("submit wrapper failed after emission")
        return submission
    }
    private fun completion(fence: Long): VulkanFenceStatus {
        if (completion == VulkanFenceStatus.Complete && executed.add(fence)) for (command in commands.getValue(submissions.getValue(fence))) command()
        return completion
    }
    override fun fenceStatus(fence: Long): VulkanFenceStatus { call("poll", "poll:$fence"); return completion(fence) }
    override fun waitForFence(fence: Long, timeoutNanoseconds: Long): VulkanFenceStatus { call("wait", "wait:$timeoutNanoseconds"); return completion(fence) }
    override fun destroyRecording(recording: VulkanTransferRecording) {
        call("destroyRecording", "destroyRecording:${recording.commandBuffer}")
        onDestroyRecording()
        check(commands.remove(recording.commandBuffer) != null)
    }
    override fun destroyFence(fence: Long) = call("destroyFence", "destroyFence:$fence")
}

/** Actual shaderc/SPIRV-Cross 3.4.1 output: Vulkan1.0/SPIR-V1.0, no optimization, RHIF v1. */
internal object RasterTestFixtures {
    private val encoded: Map<String, Pair<String, String>> = mapOf(
        "triangle.vert" to ("AwIjBwAAAQALAA0AKQAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABwAAAAAABAAAAG1haW4AAAAADQAAABsAAAADAAMAAgAAAMIBAAAEAAoAR0xfR09PR0xFX2NwcF9zdHlsZV9saW5lX2RpcmVjdGl2ZQAABAAIAEdMX0dPT0dMRV9pbmNsdWRlX2RpcmVjdGl2ZQAFAAQABAAAAG1haW4AAAAABQAGAAsAAABnbF9QZXJWZXJ0ZXgAAAAABgAGAAsAAAAAAAAAZ2xfUG9zaXRpb24ABgAHAAsAAAABAAAAZ2xfUG9pbnRTaXplAAAAAAYABwALAAAAAgAAAGdsX0NsaXBEaXN0YW5jZQAGAAcACwAAAAMAAABnbF9DdWxsRGlzdGFuY2UABQADAA0AAAAAAAAABQAGABsAAABnbF9WZXJ0ZXhJbmRleAAABQAFAB4AAABpbmRleGFibGUAAABHAAMACwAAAAIAAABIAAUACwAAAAAAAAALAAAAAAAAAEgABQALAAAAAQAAAAsAAAABAAAASAAFAAsAAAACAAAACwAAAAMAAABIAAUACwAAAAMAAAALAAAABAAAAEcABAAbAAAACwAAACoAAAATAAIAAgAAACEAAwADAAAAAgAAABYAAwAGAAAAIAAAABcABAAHAAAABgAAAAQAAAAVAAQACAAAACAAAAAAAAAAKwAEAAgAAAAJAAAAAQAAABwABAAKAAAABgAAAAkAAAAeAAYACwAAAAcAAAAGAAAACgAAAAoAAAAgAAQADAAAAAMAAAALAAAAOwAEAAwAAAANAAAAAwAAABUABAAOAAAAIAAAAAEAAAArAAQADgAAAA8AAAAAAAAAFwAEABAAAAAGAAAAAgAAACsABAAIAAAAEQAAAAMAAAAcAAQAEgAAABAAAAARAAAAKwAEAAYAAAATAAAAAABAvywABQAQAAAAFAAAABMAAAATAAAAKwAEAAYAAAAVAAAAAABAPywABQAQAAAAFgAAABUAAAATAAAAKwAEAAYAAAAXAAAAAAAwPywABQAQAAAAGAAAABMAAAAXAAAALAAGABIAAAAZAAAAFAAAABYAAAAYAAAAIAAEABoAAAABAAAADgAAADsABAAaAAAAGwAAAAEAAAAgAAQAHQAAAAcAAAASAAAAIAAEAB8AAAAHAAAAEAAAACsABAAGAAAAIgAAAAAAAAArAAQABgAAACMAAAAAAIA/IAAEACcAAAADAAAABwAAADYABQACAAAABAAAAAAAAAADAAAA+AACAAUAAAA7AAQAHQAAAB4AAAAHAAAAPQAEAA4AAAAcAAAAGwAAAD4AAwAeAAAAGQAAAEEABQAfAAAAIAAAAB4AAAAcAAAAPQAEABAAAAAhAAAAIAAAAFEABQAGAAAAJAAAACEAAAAAAAAAUQAFAAYAAAAlAAAAIQAAAAEAAABQAAcABwAAACYAAAAkAAAAJQAAACIAAAAjAAAAQQAFACcAAAAoAAAADQAAAA8AAAA+AAMAKAAAACYAAAD9AAEAOAABAA==" to "UkhJRgAAAAEAAAABAAAABG1haW59BurDBZzuUWv8CzoQmEfg383G86AqFb95HfE3umXkTAAAAAAAAAAAAAAAAA=="),
        "color.frag" to ("AwIjBwAAAQALAA0AEgAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABgAEAAAABAAAAG1haW4AAAAACQAAABAAAwAEAAAABwAAAAMAAwACAAAAwgEAAAQACgBHTF9HT09HTEVfY3BwX3N0eWxlX2xpbmVfZGlyZWN0aXZlAAAEAAgAR0xfR09PR0xFX2luY2x1ZGVfZGlyZWN0aXZlAAUABAAEAAAAbWFpbgAAAAAFAAQACQAAAHJlc3VsdAAABQAFAAoAAABQYXJhbWV0ZXJzAAAGAAUACgAAAAAAAABjb2xvcgAAAAUABQAMAAAAcGFyYW1ldGVycwAARwAEAAkAAAAeAAAAAAAAAEcAAwAKAAAAAgAAAEgABQAKAAAAAAAAACMAAAAAAAAAEwACAAIAAAAhAAMAAwAAAAIAAAAWAAMABgAAACAAAAAXAAQABwAAAAYAAAAEAAAAIAAEAAgAAAADAAAABwAAADsABAAIAAAACQAAAAMAAAAeAAMACgAAAAcAAAAgAAQACwAAAAkAAAAKAAAAOwAEAAsAAAAMAAAACQAAABUABAANAAAAIAAAAAEAAAArAAQADQAAAA4AAAAAAAAAIAAEAA8AAAAJAAAABwAAADYABQACAAAABAAAAAAAAAADAAAA+AACAAUAAABBAAUADwAAABAAAAAMAAAADgAAAD0ABAAHAAAAEQAAABAAAAA+AAMACQAAABEAAAD9AAEAOAABAA==" to "UkhJRgAAAAEAAAACAAAABG1haW4F2Mf2nVkkNdxK9M67dymx8nSZm1Al5V7gOsSoy4JJEAAAAAAAAAABAAAABnJlc3VsdAAAAAAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAEAAAADAAAACnBhcmFtZXRlcnP//////////wAAAAAAAAAAAAAAEAEAAAAKUGFyYW1ldGVycwAAAAEAAAAFY29sb3IAAAAAAAAAAAAAABAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAAAAAAAAAA="),
        "constant.frag" to ("AwIjBwAAAQALAA0ADQAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABgAEAAAABAAAAG1haW4AAAAACQAAABAAAwAEAAAABwAAAAMAAwACAAAAwgEAAAQACgBHTF9HT09HTEVfY3BwX3N0eWxlX2xpbmVfZGlyZWN0aXZlAAAEAAgAR0xfR09PR0xFX2luY2x1ZGVfZGlyZWN0aXZlAAUABAAEAAAAbWFpbgAAAAAFAAQACQAAAHJlc3VsdAAARwAEAAkAAAAeAAAAAAAAABMAAgACAAAAIQADAAMAAAACAAAAFgADAAYAAAAgAAAAFwAEAAcAAAAGAAAABAAAACAABAAIAAAAAwAAAAcAAAA7AAQACAAAAAkAAAADAAAAKwAEAAYAAAAKAAAAAACAPysABAAGAAAACwAAAAAAAAAsAAcABwAAAAwAAAAKAAAACwAAAAsAAAAKAAAANgAFAAIAAAAEAAAAAAAAAAMAAAD4AAIABQAAAD4AAwAJAAAADAAAAP0AAQA4AAEA" to "UkhJRgAAAAEAAAACAAAABG1haW53HqaFRNoH4UpuzjZk0aqjtMuqdjjxsbjW+5GDtjCnswAAAAAAAAABAAAABnJlc3VsdAAAAAAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAA="),
        "descriptor.frag" to ("AwIjBwAAAQALAA0AEwAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABgAEAAAABAAAAG1haW4AAAAACQAAABAAAwAEAAAABwAAAAMAAwACAAAAwgEAAAQACgBHTF9HT09HTEVfY3BwX3N0eWxlX2xpbmVfZGlyZWN0aXZlAAAEAAgAR0xfR09PR0xFX2luY2x1ZGVfZGlyZWN0aXZlAAUABAAEAAAAbWFpbgAAAAAFAAQACQAAAHJlc3VsdAAABQAEAA0AAABpbWFnZQAAAEcABAAJAAAAHgAAAAAAAABHAAQADQAAACEAAAAAAAAARwAEAA0AAAAiAAAAAAAAABMAAgACAAAAIQADAAMAAAACAAAAFgADAAYAAAAgAAAAFwAEAAcAAAAGAAAABAAAACAABAAIAAAAAwAAAAcAAAA7AAQACAAAAAkAAAADAAAAGQAJAAoAAAAGAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAGwADAAsAAAAKAAAAIAAEAAwAAAAAAAAACwAAADsABAAMAAAADQAAAAAAAAAXAAQADwAAAAYAAAACAAAAKwAEAAYAAAAQAAAAAAAAPywABQAPAAAAEQAAABAAAAAQAAAANgAFAAIAAAAEAAAAAAAAAAMAAAD4AAIABQAAAD0ABAALAAAADgAAAA0AAABXAAUABwAAABIAAAAOAAAAEQAAAD4AAwAJAAAAEgAAAP0AAQA4AAEA" to "UkhJRgAAAAEAAAACAAAABG1haW4QI874lFISiWkG+x5u/F0KVMXMvJz3nGEnG9EsTT71pQAAAAAAAAABAAAABnJlc3VsdAAAAAAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAEAAAAEAAAABWltYWdlAAAAAAAAAAAAAAAA//////////8AAAAAAAEAAAACAAAAAAAABAAAACAAAAABAAAAAQAAAAA="),
        "offset.frag" to ("AwIjBwAAAQALAA0AEgAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABgAEAAAABAAAAG1haW4AAAAACQAAABAAAwAEAAAABwAAAAMAAwACAAAAwgEAAAQACgBHTF9HT09HTEVfY3BwX3N0eWxlX2xpbmVfZGlyZWN0aXZlAAAEAAgAR0xfR09PR0xFX2luY2x1ZGVfZGlyZWN0aXZlAAUABAAEAAAAbWFpbgAAAAAFAAQACQAAAHJlc3VsdAAABQAFAAoAAABQYXJhbWV0ZXJzAAAGAAUACgAAAAAAAABjb2xvcgAAAAUABQAMAAAAcGFyYW1ldGVycwAARwAEAAkAAAAeAAAAAAAAAEcAAwAKAAAAAgAAAEgABQAKAAAAAAAAACMAAAAQAAAAEwACAAIAAAAhAAMAAwAAAAIAAAAWAAMABgAAACAAAAAXAAQABwAAAAYAAAAEAAAAIAAEAAgAAAADAAAABwAAADsABAAIAAAACQAAAAMAAAAeAAMACgAAAAcAAAAgAAQACwAAAAkAAAAKAAAAOwAEAAsAAAAMAAAACQAAABUABAANAAAAIAAAAAEAAAArAAQADQAAAA4AAAAAAAAAIAAEAA8AAAAJAAAABwAAADYABQACAAAABAAAAAAAAAADAAAA+AACAAUAAABBAAUADwAAABAAAAAMAAAADgAAAD0ABAAHAAAAEQAAABAAAAA+AAMACQAAABEAAAD9AAEAOAABAA==" to "UkhJRgAAAAEAAAACAAAABG1haW45LEvQlbXBGMnhIZnkChEytcR7Q1Jrsoq6SWN2L3CjogAAAAAAAAABAAAABnJlc3VsdAAAAAAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAEAAAADAAAACnBhcmFtZXRlcnP//////////wAAAAAAAAAAAAAAIAEAAAAKUGFyYW1ldGVycwAAAAEAAAAFY29sb3IAAAAQAAAAAAAAABAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAAAAAAAAAA="),
    )

    fun description(name: String): ShaderModuleDescription {
        val code = Base64.getDecoder().decode(encoded.getValue(name).first)
        return ShaderModuleDescription(if (name.endsWith(".vert")) ShaderStage.Vertex else ShaderStage.Fragment,
            ShaderBinary.copyOf(ByteBuffer.wrap(code), ShaderBinaryFormat.SpirV, name))
    }

    fun artifact(name: String): ShaderInterfaceArtifact =
        ShaderInterfaceArtifact.decode(ByteBuffer.wrap(Base64.getDecoder().decode(encoded.getValue(name).second)))
}

/** Hand-authored direct SPIR-V with explicit same-byte facts; no compiler or canonical preparation. */
internal object VertexTestFixtures {
    private fun floatType(components: Int, columns: Int = 1) = ShaderValueDescription(ShaderScalarKind.Float, 32, components, columns)

    fun vertexState(slot: Int = 0, stride: Int = 12, offset: Int = 0): VertexState = VertexState(
        List(slot) { VertexBufferLayout(0, attributes = emptyList()) } + VertexBufferLayout(stride, attributes = listOf(VertexAttribute(0, VertexFormat.Float32x3, offset))),
    )

    fun description(stage: ShaderStage): ShaderModuleDescription {
        val words = mutableListOf(0x07230203, 0x00010000, 0, 32, 0)
        fun instruction(opcode: Int, vararg operands: Int) { words.add(((operands.size + 1) shl 16) or opcode); words.addAll(operands.toList()) }
        instruction(17, 1)
        instruction(14, 0, 1)
        if (stage == ShaderStage.Vertex) instruction(15, 0, 1, 0x6e69616d, 0, 9, 10)
        else { instruction(15, 4, 1, 0x6e69616d, 0, 10); instruction(16, 1, 7) }
        if (stage == ShaderStage.Vertex) instruction(5, 9, *textWords("position"))
        else instruction(5, 10, *textWords("result"))
        instruction(5, 7, *textWords("Surface"))
        instruction(5, 12, *textWords("surface"))
        instruction(6, 7, 0, *textWords("clipFromLocal"))
        instruction(6, 7, 1, *textWords("color"))
        if (stage == ShaderStage.Vertex) { instruction(71, 9, 30, 0); instruction(71, 10, 11, 0) }
        else instruction(71, 10, 30, 0)
        instruction(71, 7, 2)
        instruction(72, 7, 0, 5)
        instruction(72, 7, 0, 7, 16)
        instruction(72, 7, 0, 35, 0)
        instruction(72, 7, 1, 35, 64)
        instruction(19, 2)
        instruction(33, 3, 2)
        instruction(22, 4, 32)
        instruction(23, 5, 4, 4)
        instruction(23, 6, 4, 3)
        instruction(24, 8, 5, 4)
        instruction(30, 7, 8, 5)
        instruction(32, 11, 9, 7)
        instruction(59, 11, 12, 9)
        if (stage == ShaderStage.Vertex) { instruction(32, 13, 1, 6); instruction(59, 13, 9, 1) }
        instruction(32, 14, 3, 5)
        instruction(59, 14, 10, 3)
        instruction(21, 15, 32, 0)
        instruction(43, 15, 16, 0)
        instruction(43, 15, 17, 1)
        instruction(32, 18, 9, 8)
        instruction(32, 19, 9, 5)
        instruction(43, 4, 20, 0x3f800000)
        instruction(54, 2, 1, 0, 3)
        instruction(248, 21)
        if (stage == ShaderStage.Vertex) {
            instruction(61, 6, 22, 9)
            instruction(81, 4, 23, 22, 0)
            instruction(81, 4, 24, 22, 1)
            instruction(81, 4, 25, 22, 2)
            instruction(80, 5, 26, 23, 24, 25, 20)
            instruction(65, 18, 27, 12, 16)
            instruction(61, 8, 28, 27)
            instruction(145, 5, 29, 28, 26)
        } else {
            instruction(65, 19, 30, 12, 17)
            instruction(61, 5, 29, 30)
        }
        instruction(62, 10, 29)
        instruction(253)
        instruction(56)
        val bytes = ByteBuffer.allocate(words.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        words.forEach { bytes.putInt(it) }
        bytes.flip()
        return ShaderModuleDescription(stage, ShaderBinary.copyOf(bytes, ShaderBinaryFormat.SpirV, "direct position/push fixture"))
    }

    private fun textWords(value: String): IntArray {
        val text = value.toByteArray(Charsets.UTF_8)
        val bytes = ByteBuffer.allocate((text.size + 4) / 4 * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put(text)
        return IntArray(bytes.capacity() / 4) { bytes.getInt(it * 4) }
    }

    fun artifact(stage: ShaderStage): ShaderInterfaceArtifact {
        val block = ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "surface", null, null, emptyList(), 80, "Surface", listOf(
            ShaderInterfaceBlockMember("clipFromLocal", 0, 64, floatType(4, 4), 16, 0, false),
            ShaderInterfaceBlockMember("color", 64, 16, floatType(4), 0, 0, false),
        ), null)
        val inputs = if (stage == ShaderStage.Vertex) listOf(ShaderInterfaceVariable("position", 0, floatType(3))) else emptyList()
        val outputs = if (stage == ShaderStage.Fragment) listOf(ShaderInterfaceVariable("result", 0, floatType(4))) else emptyList()
        return ShaderInterfaceArtifact(stage, "main", ShaderInterfaceArtifact.fingerprint((description(stage).code as ShaderBinary).bytes), ShaderInterfaceDescription(inputs, outputs, listOf(block)))
    }

    fun layoutDescription(stages: Set<ShaderStage> = setOf(ShaderStage.Vertex, ShaderStage.Fragment), sizeBytes: Int = 80): PipelineLayoutDescription =
        PipelineLayoutDescription(pushConstants = PushConstantLayout(listOf(PushConstantRange(stages, 0, sizeBytes))), label = "vertex fixture layout")

    fun pushBytes(): ByteBuffer = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN).apply {
        repeat(16) { putFloat(if (it % 5 == 0) 1f else 0f) }
        repeat(4) { putFloat(if (it == 0 || it == 3) 1f else 0f) }
        flip()
    }
}
