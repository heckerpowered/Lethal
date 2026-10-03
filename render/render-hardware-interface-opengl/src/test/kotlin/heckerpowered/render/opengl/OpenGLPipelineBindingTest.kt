/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.*
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.memory.directBufferAddress
import heckerpowered.render.opengl.function.*
import heckerpowered.render.opengl.shader.*
import heckerpowered.render.pipeline.*
import heckerpowered.render.pipeline.color.*
import heckerpowered.render.pipeline.rasterization.*
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure
import java.io.File
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.nio.IntBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import kotlin.test.*

class OpenGLPipelineBindingTest {
    @Test
    fun indexedBindingPreservesOtherDrawBuffersAndRestoresAsymmetricHostState() {
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            val otherTargets = driver.targets.drop(1).map { it.snapshot() }
            driver.pass {
                bindPipeline(pipeline)
                assertEquals(1, driver.program)
                assertEquals(listOf(0x0302, 0x0303, 1, 0x0303), driver.targets[0].factors)
                assertEquals(listOf(0x8006, 0x8006), driver.targets[0].equations)
                assertEquals(listOf(true, true, true, false), driver.targets[0].mask)
                assertEquals(otherTargets, driver.targets.drop(1).map { it.snapshot() })
                assertTrue(driver.enables.values.none { it })
                assertEquals(0x0404, driver.cullMode)
                assertEquals(0x0901, driver.frontFace)
                assertEquals(listOf(0x1B02, 0x1B02), driver.polygonModes)
                assertEquals(listOf(0, 0), driver.clamps)
                assertEquals(listOf(0x8CA1, 0x935E), driver.clip)
                assertSame(pipeline, (this as OpenGLClearPass).selectedPipeline)
            }
            assertEquals(original, driver.nativeState())
            assertEquals(listOf("mut:polygon:1032:6914", "mut:polygon:1028:6913", "mut:polygon:1029:6914"), driver.calls.filter { it.startsWith("mut:polygon:") })
            assertTrue(driver.calls.indexOf("mut:program:97") < driver.calls.indexOf("fbo:draw:11"))
            assertEquals(11, driver.drawFramebuffer)
            assertEquals(10, driver.readFramebuffer)
        }
    }

    @Test
    fun sharedGlobalFallbackRestoresEveryAffectedDrawBuffer() {
        BindingDriver(BlendAccess.Shared).use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            driver.pass {
                bindPipeline(pipeline)
                assertTrue(driver.targets.all { it.snapshot() == driver.targets[0].snapshot() })
            }
            assertEquals(original, driver.nativeState())
            assertTrue(driver.calls.contains("mut:sharedFactors"))
            assertTrue(driver.calls.contains("mut:sharedEquations"))
            assertTrue(driver.calls.contains("mut:sharedBlend"))
        }
    }

    @Test
    fun indexedEnableWithSharedFactorsNeverOverwritesOtherIndependentFlagsOrMasks() {
        BindingDriver(BlendAccess.IndexedEnable).use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            val otherFlags = driver.targets.drop(1).map { it.enabled to it.mask.toList() }
            driver.pass {
                bindPipeline(pipeline)
                assertEquals(otherFlags, driver.targets.drop(1).map { it.enabled to it.mask.toList() })
                assertTrue(driver.targets.all { it.factors == driver.targets[0].factors && it.equations == driver.targets[0].equations })
            }
            assertEquals(original, driver.nativeState())
            assertTrue(driver.calls.contains("mut:sharedFactors"))
            assertTrue(driver.calls.contains("mut:indexedBlend:0"))
        }
    }

    @Test
    fun separateEquationsAreAcceptedOnlyWhenTheNormalizedCapabilityExists() {
        val blend = BlendState(
            BlendComponent(
                BlendFactor.One,
                BlendFactor.Zero,
                BlendOperation.Subtract,
            ),
            BlendComponent(
                BlendFactor.One,
                BlendFactor.One,
                BlendOperation.ReverseSubtract,
            ),
        )
        for (access in BlendAccess.entries) {
            BindingDriver(
                access,
                separate = true,
            ).use { driver ->
                val pipeline = driver.pipeline(driver.description(blend))
                val original = driver.nativeState()
                driver.pass {
                    bindPipeline(pipeline)
                    assertEquals(listOf(0x800A, 0x800B), driver.targets[0].equations)
                }
                assertEquals(original, driver.nativeState())
            }
        }
        BindingDriver(
            BlendAccess.Shared,
            separate = false,
        ).use { driver ->
            assertFailsWith<UnsupportedOperationException> { driver.pipeline(driver.description(blend)) }
            assertTrue(driver.calls.none { it.startsWith("mut:") })
        }
    }

    @Test
    fun hostExtensionEquationsAndFactorsRetainTheirRawTokens() {
        BindingDriver().use { driver ->
            driver.targets[0].equations = listOf(0x9285, 0x9285)
            driver.targets[0].factors = listOf(0x8001, 0x8002, 0x88F9, 0x88FA)
            val original = driver.nativeState()
            val pipeline = driver.pipeline()
            driver.pass { bindPipeline(pipeline) }
            assertEquals(original, driver.nativeState())
        }
    }

    @Test
    fun absentOptionalCapabilitiesNeverQueryOrMutateUnsupportedTokens() {
        BindingDriver(
            BlendAccess.Shared,
            separate = false,
            optional = false,
        ).use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            driver.pass { bindPipeline(pipeline) }
            assertEquals(original, driver.nativeState())
            assertTrue(driver.calls.none { it == "query:int:34877" || it == "query:int:37724" || it == "query:int:37725" })
            assertTrue(driver.calls.none { it.startsWith("mut:clip:") || it.startsWith("mut:clamp:") })
        }
    }

    @Test
    fun pendingDeletionOrFailedRelinkHostProgramRejectsBeforeAnyMutation() {
        for (pendingDeletion in listOf(false, true)) {
            BindingDriver().use { driver ->
                val pipeline = driver.pipeline()
                driver.programPendingDeletion = pendingDeletion
                driver.programLinked = pendingDeletion
                val original = driver.nativeState()
                driver.pass { assertFailsWith<UnsupportedOperationException> { bindPipeline(pipeline) } }
                assertEquals(original, driver.nativeState())
                assertTrue(driver.calls.none { it.startsWith("mut:") })
            }
        }
    }

    @Test
    fun absentHostProgramRestoresZeroWithoutQueryingAnInvalidObject() {
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            driver.program = 0
            driver.pass { bindPipeline(pipeline) }
            assertEquals(0, driver.program)
            assertTrue(driver.calls.none { it.startsWith("query:programStatus:") })
        }
    }

    @Test
    fun completeCapturePrecedesTheFirstMutationAndEveryVectorQueryHasLwjgl2Capacity() {
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            driver.pass { bindPipeline(pipeline) }
            val firstMutation = driver.calls.indexOfFirst { it.startsWith("mut:") }
            val capture = driver.calls.take(firstMutation).filter { it.startsWith("query:") }
            assertEquals(listOf(
                "query:program", "query:programStatus:35712", "query:programStatus:35714", "query:int:2932", "query:int:2930", "query:enable:3042", "query:int:32969", "query:int:32968",
                "query:int:32971", "query:int:32970", "query:int:32777", "query:int:34877",
                "query:vector:3107", "query:enable:2884", "query:int:2885", "query:int:2886",
                "query:vector:2880", "query:int:3378", "query:enable:2929", "query:enable:2960", "query:enable:3024",
                "query:enable:3058", "query:enable:32823", "query:enable:10754", "query:enable:10753",
                "query:enable:2848", "query:enable:35977", "query:enable:3008", "query:enable:2881",
                "query:enable:2832", "query:enable:2852", "query:enable:2882", "query:enable:32925", "query:enable:32926", "query:enable:32927",
                "query:enable:32928",
                "query:enable:12288", "query:enable:12289", "query:enable:12290", "query:enable:12291",
                "query:enable:12292", "query:enable:12293", "query:enable:12294", "query:enable:12295",
                "query:int:35098", "query:int:35099", "query:int:37724", "query:int:37725",
            ), capture)
            assertEquals(listOf(16, 16), driver.queryCapacities)
        }
    }

    @Test
    fun captureErrorsLeaveNativeStateAndLogicalSelectionUntouched() {
        for (query in listOf("query:program", "query:int:2932", "query:int:2930", "query:int:32969", "query:vector:3107", "query:vector:2880", "query:enable:3008", "query:int:3378", "query:enable:2852", "query:enable:2882", "query:enable:12295", "query:int:37725")) {
            BindingDriver().use { driver ->
                val pipeline = driver.pipeline()
                val original = driver.nativeState()
                driver.queryFailure = query
                driver.pass {
                    assertFailsWith<OpenGLOperationException> { bindPipeline(pipeline) }
                    assertNull((this as OpenGLClearPass).selectedPipeline)
                }
                assertEquals(original, driver.nativeState())
                assertTrue(driver.calls.none { it.startsWith("mut:") })
                assertEquals(1, driver.flushes)
            }
        }
    }

    @Test
    fun captureFailureCanBeCaughtBeforeAValidSubsequentBinding() {
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            driver.queryFailure = "query:program"
            driver.pass {
                assertFailsWith<OpenGLOperationException> { bindPipeline(pipeline) }
                bindPipeline(pipeline)
            }
            assertEquals(original, driver.nativeState())
        }
    }

    @Test
    fun nativeMutationErrorRestoresHostStateAndDoesNotFlushFailedEncoding() {
        for (mutation in listOf("mut:frontFace:2305", "mut:disable:2852", "mut:disable:2882", "mut:disable:12295")) {
            BindingDriver().use { driver ->
                val pipeline = driver.pipeline()
                val original = driver.nativeState()
                driver.mutationFailure = mutation
                assertFailsWith<OpenGLOperationException> { driver.pass { bindPipeline(pipeline) } }
                assertEquals(original, driver.nativeState())
                assertEquals(0, driver.flushes)
            }
        }
    }

    @Test
    fun exceptionAfterNativeMutationRestoresStateAndPreservesTheOperationalException() {
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            val failure = IllegalStateException("binding interrupted")
            driver.mutationFailure = "mut:frontFace:2305"
            driver.mutationException = failure
            assertSame(failure, assertFailsWith<IllegalStateException> { driver.pass { bindPipeline(pipeline) } })
            assertEquals(original, driver.nativeState())
        }
    }

    @Test
    fun caughtMutationFailureInvalidatesEncoderAndPassUntilTheirExit() {
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            driver.mutationFailure = "mut:frontFace:2305"
            assertFailsWith<IllegalStateException> {
                driver.pass {
                    assertFailsWith<OpenGLOperationException> { bindPipeline(pipeline) }
                    assertFailsWith<IllegalStateException> { bindPipeline(pipeline) }
                    assertFailsWith<IllegalStateException> { (this as OpenGLClearPass).checkActive() }
                    assertNull((this as OpenGLClearPass).selectedPipeline)
                }
            }
            assertEquals(original, driver.nativeState())
            assertEquals(0, driver.flushes)
            driver.pass { bindPipeline(pipeline) }
            assertEquals(original, driver.nativeState())
        }
    }

    @Test
    fun exceptionalCallbackStillRestoresStateBeforeFramebufferCleanup() {
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            val failure = IllegalArgumentException("callback failed")
            assertSame(failure, assertFailsWith<IllegalArgumentException> {
                driver.pass { bindPipeline(pipeline); throw failure }
            })
            assertEquals(original, driver.nativeState())
            assertTrue(driver.calls.indexOf("mut:program:97") < driver.calls.indexOf("fbo:draw:11"))
        }
    }

    @Test
    fun switchingOverloadsRetainsRegionScopesAndRestoresOnlyTheOriginalHostSnapshot() {
        BindingDriver().use { driver ->
            val first = driver.pipeline()
            val second = driver.description(null).copy(rasterization = RasterizationState(
                CullMode.Front,
                FrontFace.Clockwise,
                PolygonMode.Line,
            ))
            val original = driver.nativeState()
            var escaped: RenderPass? = null
            driver.pass {
                escaped = this
                bindPipeline(first)
                withViewport(Viewport(
                    0F,
                    0F,
                    1F,
                    1F,
                )) {
                    withScissor(ScissorRectangle(
                        0,
                        0,
                        1,
                        1,
                    )) {
                        bindPipeline(second)
                        assertFalse(driver.targets[0].enabled)
                        assertEquals(0x0900, driver.frontFace)
                        bindPipeline(first)
                    }
                }
            }
            assertEquals(original, driver.nativeState())
            assertEquals(1, driver.calls.count { it == "query:program" })
            assertEquals(1, driver.calls.count { it == "mut:program:97" })
            assertFailsWith<IllegalStateException> { escaped!!.bindPipeline(first) }
        }
    }

    @Test
    fun bindingNeedsNoEncoderStackSpaceAndPreservesFullStackClientBytes() {
        BindingDriver(stackBytes = 8).use { driver ->
            val pipeline = driver.pipeline()
            val description = driver.passDescription()
            val original = driver.nativeState()
            driver.device.encode("full stack") {
                val stack = memoryStack
                stack.frame {
                    val source = reserve(8, 4)
                    val bytes = asByteBuffer(source, 8).putLong(0, 0x123456789L)
                    val end = reserve(0, 1)
                    renderPass(description, RenderPassResources.Empty) { bindPipeline(pipeline) }
                    assertEquals(end, reserve(0, 1))
                    assertEquals(0x123456789L, bytes.getLong(0))
                }
            }
            assertEquals(original, driver.nativeState())
        }
    }

    @Test
    fun foreignClosedAndAlienPipelinesRejectBeforeStateCapture() {
        BindingDriver().use { driver ->
            BindingDriver().use { other ->
                val foreign = other.pipeline()
                val closed = driver.pipeline().also { it.close() }
                val alien = bindingProxy<RenderPipeline> { name, _ -> error("Unexpected alien pipeline call: $name") }
                driver.pass {
                    assertFailsWith<IllegalArgumentException> { bindPipeline(foreign) }
                    assertFailsWith<IllegalStateException> { bindPipeline(closed) }
                    assertFailsWith<IllegalArgumentException> { bindPipeline(alien) }
                }
                assertTrue(driver.calls.none { it.startsWith("query:") || it.startsWith("mut:") })
            }
        }
    }

    @Test
    fun closedBorrowedModuleRejectsBeforeNativeStateCapture() {
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            driver.modules.first().close()
            driver.pass { assertFailsWith<IllegalStateException> { bindPipeline(pipeline) } }
            assertTrue(driver.calls.none { it.startsWith("query:") || it.startsWith("mut:") })
        }
    }

    @Test
    fun passWithoutPipelineNeverCapturesOrChangesPipelineState() {
        BindingDriver().use { driver ->
            val original = driver.nativeState()
            driver.pass { assertNull((this as OpenGLClearPass).selectedPipeline) }
            assertEquals(original, driver.nativeState())
            assertTrue(driver.calls.none { it.startsWith("query:") || it.startsWith("mut:") })
        }
    }

    @Test
    fun artifactCoordinateConversionPreservesVisualWindingWithoutASecondFaceFlip() {
        val rhi = listOf(0F to 1F, 1F to 1F, 0.5F to 0F)
        fun area(points: List<Pair<Float, Float>>): Float = points.indices.sumOf { index ->
            val next = points[(index + 1) % points.size]
            (points[index].first * next.second - next.first * points[index].second).toDouble()
        }.toFloat()
        assertTrue(area(rhi) < 0)
        assertTrue(area(rhi.map { it.first to 1F - it.second }) > 0)
        BindingDriver().use { driver ->
            for (face in FrontFace.entries) {
                for (mode in PolygonMode.entries) {
                    val pipeline = driver.pipeline(driver.description().copy(rasterization = RasterizationState(
                        CullMode.None,
                        face,
                        mode,
                    )))
                    driver.pass {
                        bindPipeline(pipeline)
                        assertEquals(if (face == FrontFace.CounterClockwise) 0x0901 else 0x0900, driver.frontFace)
                        assertEquals(List(2) { when (mode) { PolygonMode.Fill -> 0x1B02; PolygonMode.Line -> 0x1B01 } }, driver.polygonModes)
                    }
                }
            }
        }
    }

    @Test
    fun bufferPushSnapshotsHeapDirectReadOnlyAndSlicesWithoutChangingSelection() {
        for (kind in 0..3) {
            pushDriver().use { driver ->
                val pipeline = driver.pipeline()
                val owner = if (kind == 1) ByteBuffer.allocateDirect(32) else ByteBuffer.allocate(32)
                owner.order(ByteOrder.nativeOrder())
                for (index in 0..3) owner.putFloat(8 + index * 4, index + 0.25f)
                owner.position(8).limit(24)
                val source = when (kind) {
                    2 -> owner.asReadOnlyBuffer()
                    3 -> owner.slice()
                    else -> owner
                }.order(ByteOrder.BIG_ENDIAN)
                val position = source.position()
                val limit = source.limit()
                driver.pass {
                    bindPipeline(pipeline)
                    pushConstants(setOf(ShaderStage.Fragment), source, 0)
                    assertEquals(position, source.position())
                    assertEquals(limit, source.limit())
                    assertEquals(listOf(0.25f, 1.25f, 2.25f, 3.25f), driver.uniformUploads.last().second)
                    owner.putFloat(8, 99f)
                    bindPipeline(pipeline)
                    assertEquals(listOf(0.25f, 1.25f, 2.25f, 3.25f), driver.uniformUploads.last().second)
                }
            }
        }
    }

    @Test
    fun partialPushUploadsOnlyAfterAllReadWordsExistAndRetainsEarlierBytes() {
        pushDriver().use { driver ->
            val pipeline = driver.pipeline()
            driver.pass {
                bindPipeline(pipeline)
                pushConstants(setOf(ShaderStage.Fragment), floats(1f, 2f), 0)
                assertTrue(driver.uniformUploads.isEmpty())
                pushConstants(setOf(ShaderStage.Fragment), floats(3f, 4f), 8)
                assertEquals(listOf(1f, 2f, 3f, 4f), driver.uniformUploads.last().second)
                pushConstants(setOf(ShaderStage.Fragment), floats(9f), 4)
                assertEquals(listOf(1f, 9f, 3f, 4f), driver.uniformUploads.last().second)
            }
            driver.uniformUploads.clear()
            driver.pass { bindPipeline(pipeline); assertTrue(driver.uniformUploads.isEmpty()) }
        }
    }

    @Test
    fun bufferPushValidatesPipelineStagesOffsetAndLifecycleBeforeUpload() {
        pushDriver().use { driver ->
            val pipeline = driver.pipeline()
            var retained: RenderPass? = null
            driver.pass {
                retained = this
                assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), floats(1f), 0) }
                bindPipeline(pipeline)
                assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Vertex), floats(1f), 0) }
                assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Fragment), floats(1f), 2) }
                assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Fragment), floats(1f), 16) }
                assertTrue(driver.uniformUploads.isEmpty())
                pipeline.close()
                assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), floats(1f), 0) }
            }
            assertFailsWith<IllegalStateException> { retained!!.pushConstants(setOf(ShaderStage.Fragment), floats(1f), 0) }
        }
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            driver.pass {
                bindPipeline(pipeline)
                assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), floats(1f), 0) }
            }
        }
    }

    @Test
    fun uniformFailureInvalidatesEncoderEvenWhenCaughtAndRestoresHostProgram() {
        for (throws in listOf(false, true)) {
            pushDriver().use { driver ->
                val pipeline = driver.pipeline()
                val original = driver.nativeState()
                assertFailsWith<IllegalStateException> {
                    driver.pass {
                        bindPipeline(pipeline)
                        driver.mutationFailure = "mut:uniform:7"
                        val failure = IllegalStateException("synthetic uniform failure")
                        if (throws) driver.mutationException = failure
                        val actual = assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), floats(1f, 2f, 3f, 4f), 0) }
                        if (throws) assertSame(failure, actual) else assertIs<OpenGLOperationException>(actual)
                        assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), floats(1f, 2f, 3f, 4f), 0) }
                    }
                }
                assertEquals(original, driver.nativeState())
                assertEquals(0, driver.flushes)
            }
        }
    }

    @Test
    fun pipelineSwitchKeepsHistoryButIncompatibleWritesPreventOldValuesFromUploading() {
        pushDriver().use { driver ->
            val first = driver.pipeline()
            val layout = driver.device.createPipelineLayout(PipelineLayoutDescription(
                label = "incompatible push",
                pushConstants = PushConstantLayout(listOf(PushConstantRange(
                    stages = setOf(ShaderStage.Fragment),
                    offsetBytes = 0,
                    sizeBytes = 20,
                ))),
            ))
            try {
                val second = driver.pipeline(driver.description().copy(layout = layout))
                driver.pass {
                    bindPipeline(first)
                    pushConstants(setOf(ShaderStage.Fragment), floats(1f, 2f, 3f, 4f), 0)
                    bindPipeline(second)
                    assertEquals(1, driver.uniformUploads.size)
                    bindPipeline(first)
                    assertEquals(2, driver.uniformUploads.size)
                    bindPipeline(second)
                    pushConstants(setOf(ShaderStage.Fragment), floats(9f), 0)
                    bindPipeline(first)
                    assertEquals(2, driver.uniformUploads.size)
                    pushConstants(setOf(ShaderStage.Fragment), floats(8f), 0)
                    assertEquals(listOf(8f, 2f, 3f, 4f), driver.uniformUploads.last().second)
                }
            } finally {
                layout.close()
            }
        }
    }

    @Test
    fun disjointStageUpdatesKeepTheirOwnUniformValues() {
        BindingDriver(
            pushLayout = PushConstantLayout(listOf(
                PushConstantRange(
                    stages = setOf(ShaderStage.Fragment),
                    offsetBytes = 0,
                    sizeBytes = 16,
                ),
                PushConstantRange(
                    stages = setOf(ShaderStage.Vertex),
                    offsetBytes = 16,
                    sizeBytes = 16,
                ),
            )),
            pushMembers = listOf(pushMember(0, 4), pushMember(16, 4, stage = ShaderStage.Vertex)),
        ).use { driver ->
            val pipeline = driver.pipeline()
            driver.pass {
                bindPipeline(pipeline)
                pushConstants(setOf(ShaderStage.Fragment), floats(1f, 2f, 3f, 4f), 0)
                pushConstants(setOf(ShaderStage.Vertex), floats(5f, 6f, 7f, 8f), 16)
                pushConstants(setOf(ShaderStage.Fragment), floats(9f), 0)
                assertEquals(listOf(7, 8, 7), driver.uniformUploads.map { it.first })
                assertEquals(listOf(5f, 6f, 7f, 8f), driver.uniformUploads[1].second)
            }
        }
    }

    @Test
    fun uploadsAllFloatShapesAndPaddedRowMajorMatrixUsingAbsoluteOffsets() {
        val members = listOf(
            pushMember(64, 1),
            pushMember(68, 2),
            pushMember(76, 3),
            pushMember(88, 4),
            pushMember(104, 4, 4, 20, true),
        )
        BindingDriver(
            pushLayout = PushConstantLayout(listOf(PushConstantRange(
                stages = setOf(ShaderStage.Fragment),
                offsetBytes = 64,
                sizeBytes = 116,
            ))),
            pushMembers = members,
        ).use { driver ->
            val pipeline = driver.pipeline()
            val source = ByteBuffer.allocate(116).order(ByteOrder.nativeOrder())
            for (index in 0..9) source.putFloat(index * 4, index + 1f)
            for (row in 0..3) for (column in 0..3) source.putFloat(40 + row * 20 + column * 4, row * 4 + column + 11f)
            driver.pass { bindPipeline(pipeline); pushConstants(setOf(ShaderStage.Fragment), source, 64) }
            assertEquals(listOf(listOf(1f), listOf(2f, 3f), listOf(4f, 5f, 6f), listOf(7f, 8f, 9f, 10f),
                listOf(11f, 15f, 19f, 23f, 12f, 16f, 20f, 24f, 13f, 17f, 21f, 25f, 14f, 18f, 22f, 26f)), driver.uniformUploads.map { it.second })
        }
    }

    @Test
    fun nativePushCopiesLiveDirectSliceBeforeTheCallerReusesItsBytes() {
        pushDriver().use { driver ->
            val pipeline = driver.pipeline()
            val owner = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder())
            for (index in 0..3) owner.putFloat(5 + index * 4, index + 0.5f)
            owner.position(5).limit(21)
            val slice = owner.slice().asReadOnlyBuffer()
            slice.position(2).limit(12)
            val address = directBufferAddress(slice)
            driver.pass {
                bindPipeline(pipeline)
                pushConstants(setOf(ShaderStage.Fragment), address, 16, 0)
                assertEquals(listOf(0.5f, 1.5f, 2.5f, 3.5f), driver.uniformUploads.last().second)
                assertEquals(2, slice.position())
                assertEquals(12, slice.limit())
                owner.putFloat(5, 99f)
                bindPipeline(pipeline)
                assertEquals(listOf(0.5f, 1.5f, 2.5f, 3.5f), driver.uniformUploads.last().second)
            }
        }
    }

    @Test
    fun nativePushHistorySurvivesTheSourceFrameEndingAndBeingReused() {
        pushDriver().use { driver ->
            val pipeline = driver.pipeline()
            driver.pass {
                bindPipeline(pipeline)
                memoryStack.frame {
                    val address = reserve(16, 4)
                    asByteBuffer(address, 16).put(floats(1f, 2f, 3f, 4f))
                    pushConstants(setOf(ShaderStage.Fragment), address, 16, 0)
                }
                memoryStack.frame { asByteBuffer(reserve(16, 4), 16).put(floats(9f, 9f, 9f, 9f)) }
                bindPipeline(pipeline)
                assertEquals(listOf(1f, 2f, 3f, 4f), driver.uniformUploads.last().second)
            }
        }
    }

    @Test
    fun nativePushChecksPassAndLayoutBeforeReadingAndRejectsNullOrWrappingNumbers() {
        val nullAddress = rejectedNativeAddress(0)
        val wrappingAddress = rejectedNativeAddress(-2)
        pushDriver().use { driver ->
            val pipeline = driver.pipeline()
            var retained: RenderPass? = null
            driver.pass {
                retained = this
                assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), nullAddress, 16, 0) }
                bindPipeline(pipeline)
                val stageFailure = assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Vertex), nullAddress, 4, 0) }
                assertTrue(stageFailure.message!!.contains("not fully exposed"))
                val offsetFailure = assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Fragment), nullAddress, 4, 2) }
                assertTrue(offsetFailure.message!!.contains("write offset"))
                val sizeFailure = assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Fragment), nullAddress, -4, 0) }
                assertTrue(sizeFailure.message!!.contains("write size"))
                assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Fragment), nullAddress, 4, 0) }
                assertFailsWith<IllegalArgumentException> { pushConstants(setOf(ShaderStage.Fragment), wrappingAddress, 4, 0) }
                assertTrue(driver.uniformUploads.isEmpty())
                memoryStack.frame {
                    val address = reserve(16, 4)
                    asByteBuffer(address, 16).put(floats(1f, 2f, 3f, 4f))
                    pushConstants(setOf(ShaderStage.Fragment), address, 16, 0)
                }
                pipeline.close()
                assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), nullAddress, 4, 0) }
            }
            assertFailsWith<IllegalStateException> { retained!!.pushConstants(setOf(ShaderStage.Fragment), nullAddress, 4, 0) }
        }
        BindingDriver().use { driver ->
            val pipeline = driver.pipeline()
            driver.pass {
                bindPipeline(pipeline)
                assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), nullAddress, 4, 0) }
            }
        }
    }

    @Test
    fun nativePushUsesTheSameUniformFailureAndEncoderInvalidationBoundary() {
        pushDriver().use { driver ->
            val pipeline = driver.pipeline()
            val original = driver.nativeState()
            val failure = IllegalStateException("native push uniform failure")
            assertFailsWith<IllegalStateException> {
                driver.pass {
                    bindPipeline(pipeline)
                    memoryStack.frame {
                        val address = reserve(16, 4)
                        asByteBuffer(address, 16).put(floats(1f, 2f, 3f, 4f))
                        driver.mutationFailure = "mut:uniform:7"
                        driver.mutationException = failure
                        assertSame(failure, assertFailsWith<IllegalStateException> { pushConstants(setOf(ShaderStage.Fragment), address, 16, 0) })
                    }
                    assertFailsWith<IllegalStateException> { bindPipeline(pipeline) }
                }
            }
            assertEquals(original, driver.nativeState())
            assertEquals(0, driver.flushes)
        }
    }

    @Test
    fun restorationFailureHaltsItsIndependentJvmWithoutRunningShutdownHooks() {
        val entries = mutableSetOf<String>()
        var loader: ClassLoader? = javaClass.classLoader
        while (loader != null) {
            if (loader is URLClassLoader) entries.addAll(loader.getURLs().map { File(it.toURI()).path })
            loader = loader.parent
        }
        entries.addAll(System.getProperty("java.class.path").split(File.pathSeparator))
        for (restoration in listOf("program", "clip", "lineStipple", "polygonStipple")) {
            val process = ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").path,
                "-cp",
                entries.joinToString(File.pathSeparator),
                "heckerpowered.render.opengl.PipelineRestorationFailureProbe",
                restoration,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                fail("Restoration failure probe did not terminate")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(1, process.exitValue(), output)
            assertTrue(output.contains("restoration armed:$restoration"), output)
            assertTrue(output.contains("pipeline state restoration"), output)
            assertFalse(output.contains("shutdown hook ran"), output)
        }
    }
}

internal object PipelineRestorationFailureProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown hook ran") })
        val driver = BindingDriver()
        val pipeline = driver.pipeline()
        driver.pass {
            bindPipeline(pipeline)
            driver.mutationFailure = when (arguments.single()) {
                "program" -> "mut:program:97"
                "clip" -> "mut:enable:12295"
                "lineStipple" -> "mut:enable:2852"
                "polygonStipple" -> "mut:enable:2882"
                else -> error("Unknown restoration probe")
            }
            println("restoration armed:${arguments.single()}")
        }
        error("Restoration failure returned")
    }
}

private enum class BlendAccess {
    Shared,
    IndexedEnable,
    IndexedAll,
}

private class DrawBufferState(
    var enabled: Boolean,
    var factors: List<Int>,
    var equations: List<Int>,
    var mask: List<Boolean>,
) {
    fun snapshot(): List<Any> = listOf(enabled, factors.toList(), equations.toList(), mask.toList())
}

private class BindingDriver(
    private val access: BlendAccess = BlendAccess.IndexedAll,
    private val separate: Boolean = true,
    private val optional: Boolean = true,
    stackBytes: Int = 64,
    private val pushLayout: PushConstantLayout? = null,
    private val pushMembers: List<OpenGLProgramPushConstant> = emptyList(),
) : AutoCloseable {
    val calls = mutableListOf<String>()
    val uniformUploads = mutableListOf<Pair<Int, List<Float>>>()
    val queryCapacities = mutableListOf<Int>()
    var queryFailure: String? = null
    var mutationFailure: String? = null
    var mutationException: IllegalStateException? = null
    private var error = 0
    var flushes = 0
    var program = 97
    var programPendingDeletion = false
    var programLinked = true
    var cullMode = 0x0404
    var frontFace = 0x0900
    var polygonModes = if (optional) listOf(0x1B01, 0x1B02) else listOf(0x1B02, 0x1B02)
    var clamps = listOf(1, 0x891D)
    var clip = listOf(0x8CA2, 0x935F)
    var drawFramebuffer = 11
    var readFramebuffer = 10
    private var textureBinding = 41
    val targets = List(3) { index ->
        DrawBufferState(
            enabled = if (access == BlendAccess.Shared) false else index != 0,
            factors = if (access == BlendAccess.IndexedAll) when (index) {
                0 -> listOf(0x0300, 0x0301, 0x0302, 0x0303)
                1 -> listOf(0x0306, 0x0307, 0x0304, 0x0305)
                else -> listOf(1, 0, 1, 0)
            } else listOf(1, 0, 1, 0),
            equations = if (access == BlendAccess.IndexedAll) listOf(0x800A, 0x800B) else listOf(0x8006, if (separate) 0x800A else 0x8006),
            mask = if (access == BlendAccess.Shared) listOf(false, true, false, true) else List(4) { (it + index) % 2 == 0 },
        )
    }
    val enables = (listOf(0x0B44, 0x0B71, 0x0B90, 0x0BD0, 0x0BF2, 0x8037, 0x2A02, 0x2A01, 0x0B20) +
            (0 until 8).map { 0x3000 + it } + if (optional) listOf(0x8C89, 0x0BC0, 0x0B41, 0x0B10, 0x0B24, 0x0B42, 0x809D, 0x809E, 0x809F, 0x80A0) else emptyList())
        .associateWith { true }.toMutableMap()

    private fun query(name: String) {
        calls.add(name)
        if (queryFailure == name) { queryFailure = null; error = 0x0502 }
    }

    private fun mutate(name: String, operation: () -> Unit) {
        calls.add(name)
        operation()
        if (mutationFailure == name) {
            mutationFailure = null
            mutationException?.let { throw it }
            error = 0x0502
        }
    }

    private fun factorTargets(index: Int): List<DrawBufferState> = if (access == BlendAccess.IndexedAll) listOf(targets[index]) else targets
    private fun maskTargets(index: Int): List<DrawBufferState> = if (access == BlendAccess.Shared) targets else listOf(targets[index])
    private val clamping = bindingProxy<OpenGLShaderColorClampingFunctions> { name, arguments ->
        val mode = when (arguments!![0] as ColorClampMode) { ColorClampMode.Always -> 1; ColorClampMode.Never -> 0; ColorClampMode.FixedOnly -> 0x891D }
        val index = if (name == "clampVertexColor") 0 else 1
        mutate("mut:clamp:$index:$mode") { clamps = clamps.toMutableList().also { it[index] = mode } }
        null
    }
    private val framebuffers = bindingProxy<OpenGLFramebufferFunctions> { name, arguments ->
        when (name) {
            "getBoundDrawFramebuffer" -> drawFramebuffer
            "getBoundReadFramebuffer" -> readFramebuffer
            "createFramebuffer" -> 20
            "bindDrawFramebuffer" -> { drawFramebuffer = arguments!![0] as Int; calls.add("fbo:draw:$drawFramebuffer"); null }
            "bindReadFramebuffer" -> { readFramebuffer = arguments!![0] as Int; calls.add("fbo:read:$readFramebuffer"); null }
            "framebufferTexture2D", "deleteFramebuffer" -> null
            "checkFramebufferStatus" -> 0x8CD5
            else -> error("Unexpected framebuffer call: $name")
        }
    }
    private var depthFunction = 0x0204
    private var depthWrite = false
    private val functions = bindingProxy<OpenGLFunctions> { name, arguments ->
        val args = arguments ?: emptyArray()
        when (name) {
            "checkCurrentContext", "deleteShader", "deleteProgram", "deleteTexture", "textureParameter", "textureImage2D" -> null
            "getError" -> error.also { error = 0 }
            "flush" -> { flushes++; null }
            "getFramebuffers" -> framebuffers
            "getShaderColorClamping" -> if (optional) clamping else null
            "getSupportsSeparateBlendEquations" -> separate
            "getSupportsMultisample", "getSupportsLegacyRasterState", "getSupportsClipControl", "getSupportsRasterizerDiscard" -> optional
            "getSupportsNonPowerOfTwoTextures" -> true
            "getRequiresVertexAttributeZero", "getSupportsPixelBuffers", "getSupportsDepthBoundsTest", "getSupportsPrimitiveRestart", "getSupportsFixedIndexPrimitiveRestart", "getSupportsClientPrimitiveRestart" -> false
            "createTexture" -> 7
            "getBoundTexture2D" -> textureBinding
            "bindTexture2D" -> { textureBinding = args[0] as Int; null }
            "getTextureLevelParameter" -> if (args[1] == 0x1003) 0x8058 else 2
            "getCurrentProgram" -> { query("query:program"); program }
            "getProgramInteger" -> {
                assertTrue(program != 0)
                assertEquals(program, args[0])
                val parameter = args[1] as Int
                query("query:programStatus:$parameter")
                when (parameter) { 0x8B80 -> if (programPendingDeletion) 1 else 0; 0x8B82 -> if (programLinked) 1 else 0; else -> error("Unexpected host program query") }
            }
            "getInteger" -> {
                val parameter = args[0] as Int
                if (parameter == 0x0D33) 4096 else {
                    query("query:int:$parameter")
                    when (parameter) {
                        0x0B74 -> depthFunction
                        0x0B72 -> if (depthWrite) 1 else 0
                        0x0D32 -> 8
                        0x80C9 -> targets[0].factors[0]
                        0x80C8 -> targets[0].factors[1]
                        0x80CB -> targets[0].factors[2]
                        0x80CA -> targets[0].factors[3]
                        0x8009 -> targets[0].equations[0]
                        0x883D -> { check(separate); targets[0].equations[1] }
                        0x0B45 -> cullMode
                        0x0B46 -> frontFace
                        0x891A -> { check(optional); clamps[0] }
                        0x891B -> { check(optional); clamps[1] }
                        0x935C -> { check(optional); clip[0] }
                        0x935D -> { check(optional); clip[1] }
                        else -> error("Unexpected scalar query: $parameter")
                    }
                }
            }
            "getIntegers" -> {
                val parameter = args[0] as Int
                val buffer = args[1] as IntBuffer
                assertTrue(buffer.remaining() >= 16)
                queryCapacities.add(buffer.remaining())
                query("query:vector:$parameter")
                val values = when (parameter) { 0x0C23 -> targets[0].mask.map { if (it) 1 else 0 }; 0x0B40 -> polygonModes; else -> error("Unexpected vector query") }
                values.forEachIndexed { index, value -> buffer.put(index, value) }
                null
            }
            "isEnabled" -> {
                val capability = args[0] as Int
                query("query:enable:$capability")
                if (capability == 0x0BE2) targets[0].enabled else enables.getValue(capability)
            }
            "uniformFloat", "uniformFloat2", "uniformFloat3", "uniformFloat4", "uniformMatrix4" -> {
                val location = args[0] as Int
                assertEquals(1, program)
                val values = if (name == "uniformMatrix4") {
                    assertEquals(false, args[1])
                    val source = args[2] as FloatBuffer
                    assertTrue(source.isDirect)
                    assertEquals(16, source.remaining())
                    List(16) { source[source.position() + it] }
                } else args.drop(1).map { it as Float }
                mutate("mut:uniform:$location") { uniformUploads.add(location to values) }
                null
            }
            "useProgram" -> { val value = args[0] as Int; mutate("mut:program:$value") { program = value }; null }
            "enable", "disable" -> {
                val capability = args[0] as Int
                check(capability != 0x0BE2) { "Global blend enable would overwrite independent entries" }
                mutate("mut:$name:$capability") { enables[capability] = name == "enable" }
                null
            }
            "setBlendEnabled" -> {
                val index = args[0] as Int
                mutate(if (access == BlendAccess.Shared) "mut:sharedBlend" else "mut:indexedBlend:$index") { maskTargets(index).forEach { it.enabled = args[1] as Boolean } }
                null
            }
            "blendFunctionSeparate" -> {
                assertEquals(5, args.size)
                val index = args[0] as Int
                mutate(if (access == BlendAccess.IndexedAll) "mut:indexedFactors:$index" else "mut:sharedFactors") { factorTargets(index).forEach { it.factors = args.drop(1).map { value -> value as Int } } }
                null
            }
            "blendEquationSeparate" -> {
                val index = args[0] as Int
                val equations = listOf(args[1] as Int, args[2] as Int)
                check(separate || equations[0] == equations[1])
                mutate(if (access == BlendAccess.IndexedAll) "mut:indexedEquations:$index" else "mut:sharedEquations") { factorTargets(index).forEach { it.equations = equations } }
                null
            }
            "colorMask" -> {
                assertEquals(5, args.size)
                val index = args[0] as Int
                mutate("mut:mask:$index") { maskTargets(index).forEach { it.mask = args.drop(1).map { value -> value as Boolean } } }
                null
            }
            "depthFunction" -> { depthFunction = args[0] as Int; null }
            "depthMask" -> { depthWrite = args[0] as Boolean; null }
            "cullFace" -> { mutate("mut:cull:${args[0]}") { cullMode = args[0] as Int }; null }
            "frontFace" -> { mutate("mut:frontFace:${args[0]}") { frontFace = args[0] as Int }; null }
            "polygonMode" -> {
                val face = args[0] as Int
                val mode = args[1] as Int
                mutate("mut:polygon:$face:$mode") { polygonModes = polygonModes.toMutableList().also { if (face != 0x0405) it[0] = mode; if (face != 0x0404) it[1] = mode } }
                null
            }
            "clipControl" -> { check(optional); mutate("mut:clip:${args[0]}:${args[1]}") { clip = listOf(args[0] as Int, args[1] as Int) }; null }
            else -> error("Unexpected native call: $name")
        }
    }
    val device = OpenGLGraphicsDevice(
        functions,
        MemoryStack(stackBytes),
        canonicalShaderCompiler = RecordingCompiler(),
    )
    val modules = listOf(ShaderStage.Vertex, ShaderStage.Fragment).mapIndexed { index, stage ->
        OpenGLShaderModule(
            device,
            ShaderName(index + 1),
            ShaderModuleDescription(
                stage,
                ShaderSource(
                    ShaderLanguage.Glsl,
                    "void main() {}",
                    "synthetic",
                ),
            ),
            OpenGLShaderArtifact(
                stage,
                120,
                "",
                "",
                emptyList(),
                if (stage == ShaderStage.Fragment) listOf(OpenGLShaderInput(
                    0,
                    4,
                    "color",
                )) else emptyList(),
                emptyList(),
                emptyList(),
            ),
        )
    }
    private val stages = OpenGLShaderStages(
        device,
        ProgramName(1),
        modules,
        "synthetic",
        resourceInterface = OpenGLShaderInterface(
            emptyList(),
            pushMembers,
            emptyList(),
        ),
    )
    private val pushPipelineLayout = pushLayout?.let { device.createPipelineLayout(PipelineLayoutDescription(
        label = "push test",
        pushConstants = it,
    )) }
    private val pipelines = mutableListOf<OpenGLRenderPipeline>()
    private val texture = device.createTexture(TextureDescription(
        "target",
        2,
        2,
        format = TextureFormat.Rgba8UnsignedNormalized,
        usage = setOf(TextureUsage.ColorAttachment),
    ))
    private val attachment = device.createAttachmentView(device.createTextureView(texture, TextureViewDescription()))

    fun description(blend: BlendState? = BlendState.StraightAlpha): RenderPipelineDescription = RenderPipelineDescription(
        label = "binding test",
        shaders = stages,
        layout = pushPipelineLayout,
        rasterization = RasterizationState(
            CullMode.None,
            FrontFace.CounterClockwise,
            PolygonMode.Fill,
        ),
        colorTargets = listOf(ColorTargetState(
            TextureFormat.Rgba8UnsignedNormalized,
            blend,
            ColorWriteMask.Rgb,
        )),
    )

    fun pipeline(description: RenderPipelineDescription = description()): OpenGLRenderPipeline =
        (device.createRenderPipeline(description) as OpenGLRenderPipeline).also { pipelines.add(it) }

    fun passDescription(): RenderPassDescription = RenderPassDescription(
        "binding test",
        colorAttachments = listOf(RenderPassAttachment(attachment)),
    )

    fun pass(commands: RenderPass.() -> Unit) {
        val description = passDescription()
        calls.clear()
        queryCapacities.clear()
        device.encode("binding test") { renderPass(description, RenderPassResources.Empty, commands) }
    }

    fun nativeState(): List<Any> = listOf(program, depthFunction, depthWrite, cullMode, frontFace, polygonModes.toList(), enables.toMap(), targets.map { it.snapshot() }, clamps.toList(), clip.toList())

    override fun close() = terminateOnFailure {
        pipelines.forEach { it.close() }
        pushPipelineLayout?.close()
        stages.close()
        modules.forEach { it.close() }
        texture.close()
        device.close()
    }
}

private inline fun <reified T> bindingProxy(crossinline invoke: (String, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments -> invoke(method.name.substringBefore('-'), arguments) } as T

private fun floats(vararg values: Float): ByteBuffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
    values.forEach { putFloat(it) }
    flip()
}

private fun pushMember(offset: Int, components: Int, columns: Int = 1, stride: Int = 0, rowMajor: Boolean = false, stage: ShaderStage = ShaderStage.Fragment): OpenGLProgramPushConstant = OpenGLProgramPushConstant(
    stage = stage,
    member = OpenGLPushConstantMember(
        offsetBytes = offset,
        components = components,
        columns = columns,
        matrixStrideBytes = stride,
        rowMajor = rowMajor,
        name = "member_$offset",
    ),
    location = UniformLocation(if (stage == ShaderStage.Fragment) 7 else 8),
)

private fun pushDriver(): BindingDriver = BindingDriver(
    pushLayout = PushConstantLayout(listOf(PushConstantRange(
        stages = setOf(ShaderStage.Fragment),
        offsetBytes = 0,
        sizeBytes = 16,
    ))),
    pushMembers = listOf(pushMember(0, 4)),
)

private fun rejectedNativeAddress(rawValue: Long): NativeAddress {
    require(rawValue == 0L || rawValue == -2L)
    return NativeAddress::class.java.getMethod("box-impl", Long::class.javaPrimitiveType).invoke(null, rawValue) as NativeAddress
}
