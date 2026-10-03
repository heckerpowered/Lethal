/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.command.pass.*
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.opengl.shader.*
import heckerpowered.render.pipeline.*
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.primitive.*
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.shader.binding.*
import heckerpowered.render.terminateOnFailure
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.util.concurrent.TimeUnit
import heckerpowered.render.opengl.function.*
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.pipeline.vertex.*
import heckerpowered.render.resource.buffer.*
import java.nio.DoubleBuffer
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.test.assertSame
import java.lang.reflect.Proxy
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenGLDrawTest {
    @Test
    fun legacyAttributeZeroUsesRetainedGeometryForBothDrawPathsAndResetsForRealInputs() {
        DrawFixture().use { fixture ->
            val driver = fixture.driver
            driver.attributeZeroRequired = true
            driver.instanceAttributesSupported = true
            driver.instancedDrawingSupported = true
            driver.baseVertexSupported = true
            val empty = fixture.pipeline()
            val real = fixture.pipeline(VertexState(listOf(VertexBufferLayout(
                stride = 16,
                attributes = listOf(VertexAttribute(0, VertexFormat.Float32x4, 0)),
            ))))
            withInstancingGeometry(fixture) { vertex, _, indices ->
                val before = driver.snapshot()
                for (repeat in 0..1) fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex), indexBuffers = listOf(indices))) {
                    bindPipeline(empty)
                    draw(3, firstVertex = 1_000_000, instanceCount = Int.MAX_VALUE)
                    assertEquals(Int.MAX_VALUE, driver.divisors.getValue(501)[0])
                    assertEquals(setOf(0), driver.enabled.getValue(501))
                    bindIndexBuffer(indices, IndexFormat.Uint32)
                    drawIndexed(3, firstIndex = 2, baseVertex = -100, instanceCount = 3)
                    assertEquals(Int.MAX_VALUE, driver.divisors.getValue(501)[0])
                    bindPipeline(real)
                    bindVertexBuffer(0, vertex)
                    draw(3)
                    assertEquals(0, driver.divisors.getValue(501)[0])
                    bindPipeline(empty)
                    draw(3)
                    assertEquals(Int.MAX_VALUE, driver.divisors.getValue(501)[0])
                }
                assertEquals(2, driver.bufferCount)
                assertEquals(before, driver.snapshot())
                assertTrue(driver.pointers.filter { it[2] == 0 && it[1] != (vertex.buffer as OpenGLBuffer).name.value }.all {
                    it[3] == 1 && it[it.lastIndex - 1] == Float.SIZE_BYTES && it.last() == 0L
                })
            }
        }
    }

    @Test
    fun legacyMissingDivisorsRejectsProceduralDrawsIncludingZeroWorkAndAllowsRealAttributeZero() {
        DrawFixture().use { fixture ->
            fixture.driver.attributeZeroRequired = true
            val empty = fixture.pipeline()
            val real = fixture.pipeline(VertexState(listOf(VertexBufferLayout(
                stride = 16,
                attributes = listOf(VertexAttribute(0, VertexFormat.Float32x4, 0)),
            ))))
            withInstancingGeometry(fixture) { vertex, _, indices ->
                fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex), indexBuffers = listOf(indices))) {
                    bindPipeline(empty)
                    bindIndexBuffer(indices, IndexFormat.Uint16)
                    assertFailsWith<UnsupportedOperationException> { draw(0) }
                    assertFailsWith<UnsupportedOperationException> { draw(3) }
                    assertFailsWith<UnsupportedOperationException> { drawIndexed(0) }
                    assertFailsWith<UnsupportedOperationException> { drawIndexed(3) }
                    assertTrue(fixture.driver.calls.isEmpty())
                    bindPipeline(real)
                    bindVertexBuffer(0, vertex)
                    draw(3)
                }
                assertEquals(1, fixture.driver.bufferCount)
                assertEquals(1, fixture.driver.draws.size)
            }
        }
    }

    @Test
    fun legacyAttributeZeroFailureSealsEncoderAndRestoresHostState() {
        DrawFixture().use { fixture ->
            val driver = fixture.driver
            driver.attributeZeroRequired = true
            driver.instanceAttributesSupported = true
            val pipeline = fixture.pipeline()
            val before = driver.snapshot()
            driver.divisorError = true
            assertFailsWith<IllegalStateException> { fixture.pass {
                bindPipeline(pipeline)
                assertFailsWith<OpenGLOperationException> { draw(3) }
                assertFailsWith<IllegalStateException> { draw(3) }
            } }
            assertTrue(driver.draws.isEmpty())
            assertEquals(before, driver.snapshot())
        }
    }

    @Test
    fun nonzeroFirstInstanceRejectsForBothDrawPathsAtEveryCount() {
        DrawFixture().use { fixture ->
            fixture.driver.instancedDrawingSupported = true
            withInstancingGeometry(fixture) { _, _, indices ->
                val pipeline = fixture.pipeline()
                fixture.pass(RenderPassResources(indexBuffers = listOf(indices))) {
                    bindPipeline(pipeline)
                    bindIndexBuffer(indices, IndexFormat.Uint16)
                    for (count in listOf(0, 1, 3)) for (geometryCount in listOf(0, 3)) {
                        assertFailsWith<UnsupportedOperationException> { draw(geometryCount, instanceCount = count, firstInstance = 1) }
                        assertFailsWith<UnsupportedOperationException> { drawIndexed(geometryCount, instanceCount = count, firstInstance = 1) }
                    }
                    assertTrue(fixture.driver.calls.isEmpty())
                    draw(3, instanceCount = 3)
                }
                assertEquals(1, fixture.driver.instancedDraws.size)
                assertTrue(fixture.driver.indexedDraws.isEmpty())
            }
        }
    }


    @Test
    fun instancingDrawAndAttributeCapabilitiesAreIndependent() {
        for (drawing in listOf(false, true)) for (attributes in listOf(false, true)) {
            DrawFixture().use { fixture ->
                val driver = fixture.driver
                driver.instancedDrawingSupported = drawing
                driver.instanceAttributesSupported = attributes
                val procedural = fixture.pipeline()
                fixture.pass {
                    bindPipeline(procedural)
                    draw(3)
                    if (drawing) draw(3, firstVertex = 4, instanceCount = 3) else {
                        assertFailsWith<UnsupportedOperationException> { draw(3, instanceCount = 3) }
                        assertFailsWith<UnsupportedOperationException> { draw(0, instanceCount = 3) }
                    }
                }
                assertEquals(if (drawing) listOf(listOf("drawArraysInstanced", PrimitiveMode.Triangles, 4, 3, 3)) else emptyList(), driver.instancedDraws)
                withInstancingGeometry(fixture) { vertex, _, _ ->
                    if (!attributes) {
                        assertFailsWith<UnsupportedOperationException> { fixture.pipeline(instanceOnlyState()) }
                        return@withInstancingGeometry
                    }
                    val pipeline = fixture.pipeline(instanceOnlyState())
                    fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex))) {
                        bindPipeline(pipeline)
                        bindVertexBuffer(0, vertex)
                        draw(3)
                        assertEquals(1, driver.divisors.getValue(501)[3])
                    }
                }
            }
        }
    }

    @Test
    fun mixedVertexAndInstancePointersUseViewOffsetsAndFetchRangesIndependently() {
        DrawFixture().use { fixture ->
            fixture.driver.instancedDrawingSupported = true
            fixture.driver.instanceAttributesSupported = true
            withInstancingGeometry(fixture) { vertex, instances, _ ->
                val pipeline = fixture.pipeline(mixedInstancingState())
                val before = fixture.driver.snapshot()
                fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex, instances))) {
                    bindPipeline(pipeline)
                    bindVertexBuffer(0, vertex)
                    bindVertexBuffer(1, instances)
                    draw(3, firstVertex = 1, instanceCount = 3)
                    assertEquals(mapOf(2 to 0, 3 to 1), fixture.driver.divisors.getValue(501))
                }
                assertEquals(listOf(16L, 148L), fixture.driver.pointers.map { it.last() })
                assertEquals(listOf(listOf<Any>("drawArraysInstanced", PrimitiveMode.Triangles, 1, 3, 3)), fixture.driver.instancedDraws)
                assertEquals(before, fixture.driver.snapshot())
                assertFalse(fixture.driver.divisors.containsKey(501))
            }
        }
    }

    @Test
    fun indexedInstancingPreservesAllWidthsTopologiesAndSignedBaseVertex() {
        DrawFixture().use { fixture ->
            fixture.driver.instancedDrawingSupported = true
            fixture.driver.instanceAttributesSupported = true
            fixture.driver.baseVertexSupported = true
            withInstancingGeometry(fixture) { vertex, instances, indices ->
                for (topology in PrimitiveTopology.entries) for (format in IndexFormat.entries) for (base in listOf(0, -4, 5)) {
                    val pipeline = fixture.pipeline(mixedInstancingState(), topology)
                    val before = fixture.driver.snapshot()
                    fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex, instances), indexBuffers = listOf(indices))) {
                        bindPipeline(pipeline)
                        bindVertexBuffer(0, vertex)
                        bindVertexBuffer(1, instances)
                        bindIndexBuffer(indices, format)
                        drawIndexed(6, firstIndex = 2, baseVertex = base, instanceCount = 3)
                        assertEquals(mapOf(2 to 0, 3 to 1), fixture.driver.divisors.getValue(501))
                    }
                    val mode = when (topology) {
                        PrimitiveTopology.PointList -> PrimitiveMode.Points
                        PrimitiveTopology.LineList -> PrimitiveMode.Lines
                        PrimitiveTopology.LineStrip -> PrimitiveMode.LineStrip
                        PrimitiveTopology.TriangleList -> PrimitiveMode.Triangles
                        PrimitiveTopology.TriangleStrip -> PrimitiveMode.TriangleStrip
                    }
                    val type = when (format) {
                        IndexFormat.Uint8 -> 0x1401
                        IndexFormat.Uint16 -> 0x1403
                        IndexFormat.Uint32 -> 0x1405
                    }
                    val expected = listOf(if (base == 0) "drawElementsInstanced" else "drawElementsInstancedBaseVertex", mode, 6, type, 240L + 2 * format.sizeInBytes, 3)
                    assertEquals(expected + (if (base == 0) emptyList() else listOf(base)) + (indices.buffer as OpenGLBuffer).name.value, fixture.driver.indexedDraws.last())
                    assertEquals(before, fixture.driver.snapshot())
                }
                assertEquals(45, fixture.driver.indexedDraws.size)
            }
        }
    }

    @Test
    fun instanceDivisorIsResetWhenLocationChangesToVertexAndAfterBeingUnused() {
        DrawFixture().use { fixture ->
            fixture.driver.instancedDrawingSupported = true
            fixture.driver.instanceAttributesSupported = true
            withInstancingGeometry(fixture) { vertex, _, _ ->
                val instancePipeline = fixture.pipeline(instanceOnlyState())
                val vertexPipeline = fixture.pipeline(instanceOnlyState(VertexStepMode.Vertex))
                val emptyPipeline = fixture.pipeline()
                val before = fixture.driver.snapshot()
                fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex))) {
                    bindVertexBuffer(0, vertex)
                    bindPipeline(instancePipeline)
                    draw(3, instanceCount = 3)
                    assertEquals(1, fixture.driver.divisors.getValue(501)[3])
                    bindPipeline(emptyPipeline)
                    draw(3)
                    assertTrue(fixture.driver.enabled.getValue(501).isEmpty())
                    bindPipeline(vertexPipeline)
                    draw(3, instanceCount = 3)
                    assertEquals(0, fixture.driver.divisors.getValue(501)[3])
                    bindPipeline(instancePipeline)
                    draw(3)
                    assertEquals(1, fixture.driver.divisors.getValue(501)[3])
                }
                assertEquals(listOf(1, 0, 1), fixture.driver.divisorCalls.map { it.last() })
                assertEquals(before, fixture.driver.snapshot())
            }
        }
    }

    @Test
    fun finalInstanceAttributeBytesNeedNoTrailingStridePaddingAndInvalidDrawCanBeRetried() {
        DrawFixture().use { fixture ->
            fixture.driver.instancedDrawingSupported = true
            fixture.driver.instanceAttributesSupported = true
            withInstancingGeometry(fixture) { vertex, instances, indices ->
                val pipeline = fixture.pipeline(mixedInstancingState())
                fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex, instances), indexBuffers = listOf(indices))) {
                    bindPipeline(pipeline)
                    bindVertexBuffer(0, vertex)
                    bindVertexBuffer(1, instances.copy(sizeBytes = 83))
                    bindIndexBuffer(indices, IndexFormat.Uint16)
                    assertFailsWith<IllegalArgumentException> { draw(3, instanceCount = 3) }
                    assertFailsWith<IllegalArgumentException> { drawIndexed(6, baseVertex = -4, instanceCount = 3) }
                    assertTrue(fixture.driver.calls.isEmpty())
                    bindVertexBuffer(1, instances)
                    draw(3, instanceCount = 3)
                    drawIndexed(6, instanceCount = 3)
                }
                assertEquals(1, fixture.driver.instancedDraws.size)
                assertEquals(1, fixture.driver.indexedDraws.size)
            }
        }
    }

    @Test
    fun instancedZeroDrawStillChecksBindingsPushConstantsDescriptorsAndFirstInstance() {
        DrawFixture().use { fixture ->
            fixture.driver.instancedDrawingSupported = true
            fixture.driver.instanceAttributesSupported = true
            withInstancingGeometry(fixture) { vertex, instances, indices ->
                val pipeline = fixture.pipeline(mixedInstancingState())
                val push = fixture.pipeline(push = true)
                val descriptors = fixture.pipeline(descriptor = true)
                fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex, instances), indexBuffers = listOf(indices))) {
                    bindPipeline(pipeline)
                    assertFailsWith<IllegalStateException> { draw(0, instanceCount = 3) }
                    bindVertexBuffer(0, vertex)
                    bindVertexBuffer(1, instances)
                    bindIndexBuffer(indices, IndexFormat.Uint16)
                    assertFailsWith<UnsupportedOperationException> { draw(0, instanceCount = 3, firstInstance = 1) }
                    assertFailsWith<UnsupportedOperationException> { drawIndexed(0, instanceCount = 3, firstInstance = 1) }
                    draw(0, instanceCount = 3)
                    draw(3, instanceCount = 0)
                    drawIndexed(0, instanceCount = 3)
                    drawIndexed(6, instanceCount = 0)
                    bindPipeline(push)
                    assertFailsWith<IllegalStateException> { draw(0, instanceCount = 3) }
                    bindPipeline(descriptors)
                    assertFailsWith<IllegalStateException> { drawIndexed(0, instanceCount = 3) }
                }
                assertTrue(fixture.driver.calls.isEmpty())
                assertTrue(fixture.driver.divisorCalls.isEmpty())
                assertTrue(fixture.driver.indexedDraws.isEmpty())
                assertTrue(fixture.driver.instancedDraws.isEmpty())
            }
        }
    }

    @Test
    fun divisorFailureSealsEncoderAndRestoresHostStateBeforeAnyDraw() {
        for (nativeError in listOf(false, true)) DrawFixture().use { fixture ->
            fixture.driver.instanceAttributesSupported = true
            withInstancingGeometry(fixture) { vertex, _, _ ->
                val pipeline = fixture.pipeline(instanceOnlyState())
                val before = fixture.driver.snapshot()
                val failure = AssertionError("divisor mutation failed")
                fixture.driver.divisorError = nativeError
                fixture.driver.divisorFailure = if (nativeError) null else failure
                assertFailsWith<IllegalStateException> { fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex))) {
                    bindPipeline(pipeline)
                    bindVertexBuffer(0, vertex)
                    if (nativeError) assertFailsWith<OpenGLOperationException> { draw(3) } else assertSame(failure, assertFailsWith<AssertionError> { draw(3) })
                    assertFailsWith<IllegalStateException> { draw(3) }
                } }
                assertTrue(fixture.driver.draws.isEmpty())
                assertEquals(before, fixture.driver.snapshot())
                assertFalse(fixture.driver.divisors.containsKey(501))
            }
        }
    }

    @Test
    fun instancedNativeDrawFailureSealsAndRestoresForArraysAndIndexed() {
        for (indexed in listOf(false, true)) for (nativeError in listOf(false, true)) DrawFixture().use { fixture ->
            fixture.driver.instancedDrawingSupported = true
            fixture.driver.instanceAttributesSupported = true
            fixture.driver.baseVertexSupported = true
            withInstancingGeometry(fixture) { vertex, instances, indices ->
                val pipeline = fixture.pipeline(mixedInstancingState())
                val before = fixture.driver.snapshot()
                val failure = AssertionError("instanced draw failed")
                fixture.driver.drawError = nativeError
                fixture.driver.drawFailure = if (nativeError) null else failure
                assertFailsWith<IllegalStateException> { fixture.pass(RenderPassResources(vertexBuffers = listOf(vertex, instances), indexBuffers = listOf(indices))) {
                    bindPipeline(pipeline)
                    bindVertexBuffer(0, vertex)
                    bindVertexBuffer(1, instances)
                    bindIndexBuffer(indices, IndexFormat.Uint16)
                    val command = { if (indexed) drawIndexed(6, baseVertex = -4, instanceCount = 3) else draw(3, instanceCount = 3) }
                    if (nativeError) assertFailsWith<OpenGLOperationException>(block = command) else assertSame(failure, assertFailsWith<AssertionError>(block = command))
                    assertFailsWith<IllegalStateException> { draw(3) }
                } }
                assertEquals(before, fixture.driver.snapshot())
            }
        }
    }


    @Test
    fun stencilReferenceBeforeBindingIsIgnoredAndStillChecksPassLifetime() {
        DrawFixture().use { fixture ->
            val pipeline = fixture.pipeline()
            var escaped: RenderPass? = null
            fixture.pass {
                escaped = this
                setStencilReference(0u)
                bindPipeline(pipeline)
                setStencilReference(255u)
                draw(3)
            }
            assertEquals(1, fixture.driver.draws.size)
            assertFailsWith<IllegalStateException> { checkNotNull(escaped).setStencilReference(0u) }
        }
    }

    @Test
    fun hostUserClippingAndCompatibilityStippleAreDisabledDuringDrawAndRestored() {
        for (legacy in listOf(false, true)) {
            val driver = DrawStateDriver(
                viewportArrays = true,
                legacyRaster = legacy,
                clipCount = if (legacy) 6 else 8,
            )
            for (index in 0 until driver.clipCount) driver.enables[0x3000 + index] = index % 2 == 0
            if (legacy) {
                driver.enables[0x0B24] = true
                driver.enables[0x0B42] = true
            }
            DrawFixture(driver).use { fixture ->
                for (topology in PrimitiveTopology.entries) {
                    val pipeline = fixture.pipeline(topology = topology)
                    val before = driver.snapshot()
                    fixture.pass {
                        bindPipeline(pipeline)
                        draw(3)
                    }
                    assertEquals(before, driver.snapshot())
                }
            }
        }
    }

    @Test
    fun compatibilityStippleIsIsolatedWhenHostUserClippingIsAlreadyDisabled() {
        val driver = DrawStateDriver(
            viewportArrays = false,
            legacyRaster = true,
            clipCount = 6,
        )
        driver.viewports[0] = doubleArrayOf(1.0, 3.0, 100.0, 200.0)
        for (index in 0 until driver.clipCount) driver.enables[0x3000 + index] = false
        driver.enables[0x0B24] = true
        driver.enables[0x0B42] = true
        DrawFixture(driver).use { fixture ->
            val pipeline = fixture.pipeline(topology = PrimitiveTopology.LineList)
            val before = driver.snapshot()
            fixture.pass {
                bindPipeline(pipeline)
                draw(2)
            }
            assertEquals(before, driver.snapshot())
        }
    }

    @Test
    fun nativeDrawFailureStillRestoresHostClippingAndStipple() {
        val driver = DrawStateDriver(
            viewportArrays = true,
            legacyRaster = true,
            clipCount = 6,
        )
        driver.enables[0x3000] = true
        driver.enables[0x3005] = true
        driver.enables[0x0B24] = true
        driver.enables[0x0B42] = true
        DrawFixture(driver).use { fixture ->
            val pipeline = fixture.pipeline()
            val before = driver.snapshot()
            driver.drawError = true
            assertFailsWith<OpenGLOperationException> {
                fixture.pass {
                    bindPipeline(pipeline)
                    draw(3)
                }
            }
            assertEquals(before, driver.snapshot())
        }
    }

    @Test
    fun publicPointerFailureRestoresImmediateBindingAndFailsTheEncoding() {
        for (throws in listOf(false, true)) {
            DrawFixture().use { fixture ->
                fixture.device.createBuffer(BufferDescription(
                    label = "failing input",
                    sizeBytes = 64,
                    usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
                )).use { buffer ->
                    val view = GpuBufferView(
                        buffer,
                        0,
                        64,
                    )
                    val pipeline = fixture.pipeline(VertexState(listOf(VertexBufferLayout(
                        stride = 16,
                        attributes = listOf(VertexAttribute(
                            2,
                            VertexFormat.Float32x3,
                            0,
                        )),
                    ))))
                    val before = fixture.driver.snapshot()
                    val failure = AssertionError("vertex pointer failure")
                    assertFailsWith<IllegalStateException> {
                        fixture.pass(RenderPassResources(vertexBuffers = listOf(view))) {
                            bindPipeline(pipeline)
                            bindVertexBuffer(0, view)
                            if (throws) fixture.driver.pointerFailure = failure else fixture.driver.pointerError = true
                            if (throws) assertSame(failure, assertFailsWith<AssertionError> { draw(3) })
                            else assertFailsWith<OpenGLOperationException> { draw(3) }
                            assertEquals(91, fixture.driver.arrayBuffer)
                            assertFailsWith<IllegalStateException> { draw(0) }
                        }
                    }
                    assertEquals(before, fixture.driver.snapshot())
                    assertTrue(fixture.driver.draws.isEmpty())
                }
            }
        }
    }

    @Test
    fun pipelineSwitchDisablesPreviousPrivateAttributesWithoutChangingHostArrays() {
        DrawFixture().use { fixture ->
            fixture.device.createBuffer(BufferDescription(
                label = "switch input",
                sizeBytes = 64,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    0,
                    64,
                )
                val first = fixture.pipeline(VertexState(listOf(VertexBufferLayout(
                    stride = 16,
                    attributes = listOf(VertexAttribute(
                        2,
                        VertexFormat.Float32x3,
                        0,
                    )),
                ))))
                val second = fixture.pipeline()
                fixture.pass(RenderPassResources(vertexBuffers = listOf(view))) {
                    bindPipeline(first)
                    bindVertexBuffer(0, view)
                    draw(3)
                    assertEquals(setOf(2), fixture.driver.enabled.getValue(501).toSet())
                    bindPipeline(second)
                    draw(3)
                    assertTrue(fixture.driver.enabled.getValue(501).isEmpty())
                    assertEquals(setOf(11), fixture.driver.enabled.getValue(7).toSet())
                }
                assertEquals(2, fixture.driver.draws.size)
            }
        }
    }

    @Test
    fun activeUniformBuffersRequireDescriptorSelectionsEvenWithNoVertices() {
        DrawFixture().use { fixture ->
            val pipeline = fixture.pipeline(descriptor = true)
            fixture.pass {
                bindPipeline(pipeline)
                assertFailsWith<IllegalStateException> { draw(0) }
                assertFailsWith<IllegalStateException> { draw(3) }
            }
            assertTrue(fixture.driver.draws.isEmpty())
            assertTrue(fixture.driver.calls.none { it == "createVAO" })
        }
    }

    @Test
    fun indexedWidthsTopologiesAndSignedBaseOffsetsUsePrivateElementBinding() {
        DrawFixture().use { fixture ->
            fixture.driver.baseVertexSupported = true
            fixture.device.createBuffer(BufferDescription(
                label = "indices",
                sizeBytes = 128,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    16,
                    64,
                )
                val before = fixture.driver.snapshot()
                for (format in IndexFormat.entries) for (topology in PrimitiveTopology.entries) {
                    val pipeline = fixture.pipeline(topology = topology)
                    fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                        bindPipeline(pipeline)
                        bindIndexBuffer(view, format)
                        for (base in listOf(0, -3, 9)) drawIndexed(3, firstIndex = 2, baseVertex = base)
                        draw(3)
                    }
                    assertEquals(before, fixture.driver.snapshot())
                    val mode = when (topology) {
                        PrimitiveTopology.PointList -> PrimitiveMode.Points
                        PrimitiveTopology.LineList -> PrimitiveMode.Lines
                        PrimitiveTopology.LineStrip -> PrimitiveMode.LineStrip
                        PrimitiveTopology.TriangleList -> PrimitiveMode.Triangles
                        PrimitiveTopology.TriangleStrip -> PrimitiveMode.TriangleStrip
                    }
                    val type = when (format) {
                        IndexFormat.Uint8 -> 0x1401
                        IndexFormat.Uint16 -> 0x1403
                        IndexFormat.Uint32 -> 0x1405
                    }
                    val offset = 16L + 2L * format.sizeInBytes
                    assertEquals(listOf("drawElements", mode, 3, type, offset, 61), fixture.driver.indexedDraws.takeLast(3)[0])
                    for ((index, base) in listOf(-3, 9).withIndex()) {
                        assertEquals(listOf("drawElementsBaseVertex", mode, 3, type, offset, base, 61), fixture.driver.indexedDraws.takeLast(3)[index + 1])
                    }
                }
            }
        }
    }

    @Test
    fun indexedLongOffsetsAndZeroRangeChecksNeverNarrowOrAllocateEmptyDrawState() {
        DrawFixture().use { fixture ->
            fixture.device.createBuffer(BufferDescription(
                label = "large metadata-only indices",
                sizeBytes = Long.MAX_VALUE,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    Long.MAX_VALUE - 32L,
                    32,
                )
                val pipeline = fixture.pipeline()
                fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                    bindPipeline(pipeline)
                    bindIndexBuffer(view, IndexFormat.Uint8)
                    drawIndexed(0, firstIndex = 32)
                    drawIndexed(3, instanceCount = 0)
                    assertFailsWith<IllegalArgumentException> { drawIndexed(0, firstIndex = 33) }
                    assertFailsWith<IllegalArgumentException> { drawIndexed(33, instanceCount = 0) }
                    assertTrue(fixture.driver.calls.isEmpty())
                    drawIndexed(3, firstIndex = 2)
                }
                assertEquals(Long.MAX_VALUE - 30L, fixture.driver.indexedDraws.single()[4])
            }
            fixture.device.createBuffer(BufferDescription(
                label = "wide first index",
                sizeBytes = 8589934600L,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    0,
                    8589934600L,
                )
                val pipeline = fixture.pipeline()
                fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                    bindPipeline(pipeline)
                    bindIndexBuffer(view, IndexFormat.Uint32)
                    drawIndexed(1, firstIndex = Int.MAX_VALUE)
                }
                assertEquals(8589934588L, fixture.driver.indexedDraws.last()[4])
            }
        }
    }

    @Test
    fun indexedZeroDrawRequiresLiveDeclaredBindingsParametersAndOptionalCapabilities() {
        DrawFixture().use { fixture ->
            fixture.device.createBuffer(BufferDescription(
                label = "indices",
                sizeBytes = 64,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    0,
                    64,
                )
                val pipeline = fixture.pipeline()
                fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                    assertFailsWith<IllegalStateException> { drawIndexed(0) }
                    bindPipeline(pipeline)
                    assertFailsWith<IllegalStateException> { drawIndexed(0) }
                    bindIndexBuffer(view, IndexFormat.Uint16)
                    assertFailsWith<IllegalArgumentException> { drawIndexed(-1) }
                    assertFailsWith<IllegalArgumentException> { drawIndexed(0, firstIndex = -1) }
                    assertFailsWith<IllegalArgumentException> { drawIndexed(0, instanceCount = -1) }
                    assertFailsWith<IllegalArgumentException> { drawIndexed(0, firstInstance = -1) }
                    assertFailsWith<UnsupportedOperationException> { drawIndexed(0, baseVertex = -1) }
                    assertFailsWith<UnsupportedOperationException> { drawIndexed(0, instanceCount = 2) }
                    assertFailsWith<UnsupportedOperationException> { drawIndexed(0, firstInstance = 1) }
                    drawIndexed(0, firstIndex = 32)
                }
                assertTrue(fixture.driver.calls.isEmpty())
                assertTrue(fixture.driver.indexedDraws.isEmpty())
            }
        }
        for (push in listOf(false, true)) DrawFixture().use { fixture ->
            fixture.device.createBuffer(BufferDescription(
                "indices",
                8,
                setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    0,
                    8,
                )
                val pipeline = fixture.pipeline(push = push, descriptor = !push)
                fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                    bindPipeline(pipeline)
                    bindIndexBuffer(view, IndexFormat.Uint8)
                    if (push) {
                        assertFailsWith<IllegalStateException> { drawIndexed(0) }
                        val values = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f)
                        values.flip()
                        pushConstants(setOf(ShaderStage.Fragment), values)
                        drawIndexed(3)
                        assertEquals(listOf(1f, 0f, 0f, 1f), fixture.driver.uniformValues.last())
                    } else assertFailsWith<IllegalStateException> { drawIndexed(0) }
                }
            }
        }
    }

    @Test
    fun indexedBindingUsesDeviceIdentityAndRevalidatesBorrowedShadersEvenWithoutWork() {
        DrawFixture().use { fixture ->
            DrawStateDriver(true).device.use { other ->
                val description = BufferDescription(
                    "indices",
                    16,
                    setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
                )
                fixture.device.createBuffer(description).use { buffer ->
                    other.createBuffer(description).use { foreign ->
                        val view = GpuBufferView(
                            buffer,
                            0,
                            16,
                        )
                        val foreignView = GpuBufferView(
                            foreign,
                            0,
                            16,
                        )
                        val pipeline = fixture.pipeline()
                        fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                            assertFailsWith<IllegalArgumentException> { bindIndexBuffer(foreignView, IndexFormat.Uint8) }
                            bindPipeline(pipeline)
                            bindIndexBuffer(view, IndexFormat.Uint8)
                            fixture.closeBorrowedShaderModule()
                            assertFailsWith<IllegalStateException> { drawIndexed(0) }
                        }
                        assertTrue(fixture.driver.calls.isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun indexedNativeBindingAndDrawingFailuresRestoreHostElementStateAndSealEncoding() {
        for (kind in listOf("elementError", "elementThrow", "drawError", "drawThrow")) DrawFixture().use { fixture ->
            fixture.device.createBuffer(BufferDescription(
                "indices",
                64,
                setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    0,
                    64,
                )
                val pipeline = fixture.pipeline()
                val before = fixture.driver.snapshot()
                fixture.driver.elementBindingError = kind == "elementError"
                fixture.driver.elementBindingFailure = if (kind == "elementThrow") AssertionError("element binding failed") else null
                fixture.driver.drawError = kind == "drawError"
                fixture.driver.drawFailure = if (kind == "drawThrow") AssertionError("indexed draw failed") else null
                assertFailsWith<IllegalStateException> {
                    fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                        bindPipeline(pipeline)
                        bindIndexBuffer(view, IndexFormat.Uint8)
                        if (kind.endsWith("Throw")) assertFailsWith<AssertionError> { drawIndexed(3) } else assertFailsWith<OpenGLOperationException> { drawIndexed(3) }
                        assertFailsWith<IllegalStateException> { drawIndexed(0) }
                    }
                }
                assertEquals(before, fixture.driver.snapshot())
            }
        }
    }

    @Test
    fun indexedBindingsCanChangeViewsAndBuffersAcrossArrayDrawsWithoutTouchingTheHostVao() {
        DrawFixture().use { fixture ->
            val description = BufferDescription(
                "indices",
                64,
                setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )
            fixture.device.createBuffer(description).use { first ->
                fixture.device.createBuffer(description).use { second ->
                    val firstView = GpuBufferView(
                        first,
                        4,
                        32,
                    )
                    val secondView = GpuBufferView(
                        second,
                        8,
                        32,
                    )
                    val before = fixture.driver.snapshot()
                    val pipeline = fixture.pipeline()
                    fixture.pass(RenderPassResources(indexBuffers = listOf(firstView, secondView))) {
                        bindPipeline(pipeline)
                        bindIndexBuffer(firstView, IndexFormat.Uint8)
                        drawIndexed(3, firstIndex = 2)
                        draw(3)
                        bindIndexBuffer(secondView, IndexFormat.Uint16)
                        drawIndexed(3, firstIndex = 1)
                    }
                    assertEquals(listOf(6L, 10L), fixture.driver.indexedDraws.map { it[4] })
                    assertEquals(listOf(61, 62), fixture.driver.indexedDraws.map { it.last() })
                    assertEquals(before, fixture.driver.snapshot())
                }
            }
        }
    }

    @Test
    fun indexedBindingChecksUsageAlignmentCoverageAndRequiredVertexInputsBeforeZeroWork() {
        DrawFixture().use { fixture ->
            val description = BufferDescription(
                "combined indices and vertices",
                64,
                setOf(heckerpowered.render.resource.buffer.BufferUsage.Index, heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            )
            fixture.device.createBuffer(description).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    0,
                    32,
                )
                val unaligned = GpuBufferView(
                    buffer,
                    1,
                    8,
                )
                val partial = GpuBufferView(
                    buffer,
                    0,
                    3,
                )
                val uncovered = GpuBufferView(
                    buffer,
                    32,
                    32,
                )
                val pipeline = fixture.pipeline(VertexState(listOf(VertexBufferLayout(
                    stride = 12,
                    attributes = listOf(VertexAttribute(
                        0,
                        VertexFormat.Float32x3,
                        0,
                    )),
                ))))
                fixture.pass(RenderPassResources(indexBuffers = listOf(view), vertexBuffers = listOf(view))) {
                    assertFailsWith<IllegalArgumentException> { bindIndexBuffer(unaligned, IndexFormat.Uint16) }
                    assertFailsWith<IllegalArgumentException> { bindIndexBuffer(partial, IndexFormat.Uint16) }
                    assertFailsWith<IllegalArgumentException> { bindIndexBuffer(uncovered, IndexFormat.Uint8) }
                    bindPipeline(pipeline)
                    bindIndexBuffer(view, IndexFormat.Uint8)
                    assertFailsWith<IllegalStateException> { drawIndexed(0) }
                    bindVertexBuffer(0, view)
                    drawIndexed(0)
                    drawIndexed(6)
                }
                assertEquals(6, fixture.driver.indexedDraws.single()[2])
            }
        }
    }

    @Test
    fun restartQueryFailureIsRetryableAndMutationFailureSealsWhileRestorationRemainsFatal() {
        for (operation in listOf("isEnabled", "disable")) DrawFixture().use { fixture ->
            fixture.driver.restartSupported = true
            fixture.driver.enables[0x8F9D] = true
            val pipeline = fixture.pipeline()
            val before = fixture.driver.snapshot()
            fixture.driver.restartFailure = operation
            val commands: RenderPass.() -> Unit = {
                assertFailsWith<OpenGLOperationException> { bindPipeline(pipeline) }
                if (operation == "isEnabled") bindPipeline(pipeline) else assertFailsWith<IllegalStateException> { drawIndexed(0) }
            }
            if (operation == "isEnabled") fixture.pass(commands = commands) else assertFailsWith<IllegalStateException> { fixture.pass(commands = commands) }
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun indexedHostRestartEnablesAreOptionalNeutralizedAndRestoredWithoutChangingMarkers() {
        for (cap in listOf(0x8F9D, 0x8D69, 0x8558)) for (host in listOf(false, true)) DrawFixture().use { fixture ->
            fixture.driver.restartSupported = cap == 0x8F9D
            fixture.driver.fixedRestartSupported = cap == 0x8D69
            fixture.driver.clientRestartSupported = cap == 0x8558
            fixture.driver.enables[cap] = host
            fixture.device.createBuffer(BufferDescription(
                "indices",
                8,
                setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    0,
                    8,
                )
                val before = fixture.driver.snapshot()
                val pipeline = fixture.pipeline(topology = PrimitiveTopology.TriangleStrip)
                fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                    bindPipeline(pipeline)
                    bindIndexBuffer(view, IndexFormat.Uint8)
                    assertFalse(fixture.driver.enables.getValue(cap))
                    drawIndexed(3)
                }
                assertEquals(before, fixture.driver.snapshot())
            }
        }
    }

    @Test
    fun emptyDescriptorSelectionsAndDeclaredIndexBindingAreAccepted() {
        DrawFixture().use { fixture ->
            val descriptors = DescriptorSet(
                DescriptorSetLayout.Empty,
                emptyList(),
            )
            var entered = false
            fixture.pass(RenderPassResources(descriptors = listOf(descriptors))) { entered = true }
            assertEquals(true, entered)
            fixture.device.createBuffer(BufferDescription(
                label = "indices",
                sizeBytes = 64,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Index),
            )).use { buffer ->
                val view = GpuBufferView(
                    buffer,
                    0,
                    64,
                )
                fixture.pass(RenderPassResources(indexBuffers = listOf(view))) {
                    bindIndexBuffer(view, IndexFormat.Uint16)
                    bindDescriptorSet(0, descriptors)
                }
            }
        }
    }

    @Test
    fun closedBorrowedShaderResourcesAreRevalidatedAtZeroDraw() {
        for (module in listOf(false, true)) {
            DrawFixture().use { fixture ->
                val pipeline = fixture.pipeline()
                fixture.pass {
                    bindPipeline(pipeline)
                    if (module) fixture.closeBorrowedShaderModule() else fixture.closeBorrowedShaderStages()
                    assertFailsWith<IllegalStateException> { draw(0) }
                }
                assertTrue(fixture.driver.draws.isEmpty())
            }
        }
    }

    @Test
    fun nativeViewportQueryErrorInvalidatesTheEncoderBeforeVertexArrayAllocation() {
        DrawFixture().use { fixture ->
            val pipeline = fixture.pipeline()
            val before = fixture.driver.snapshot()
            assertFailsWith<IllegalStateException> {
                fixture.pass {
                    bindPipeline(pipeline)
                    fixture.driver.viewportQueryError = true
                    assertFailsWith<OpenGLOperationException> { draw(0) }
                    assertFailsWith<IllegalStateException> { draw(0) }
                }
            }
            assertEquals(before, fixture.driver.snapshot())
            assertTrue(fixture.driver.calls.none { it == "createVAO" })
        }
    }

    @Test
    fun legacyPublicDrawRejectsFractionsAndAllowsAValidRetry() {
        val driver = DrawStateDriver(false)
        driver.viewports[0] = doubleArrayOf(1.0, 3.0, 100.0, 200.0)
        DrawFixture(driver).use { fixture ->
            val pipeline = fixture.pipeline()
            val before = driver.snapshot()
            fixture.pass {
                bindPipeline(pipeline)
                assertFailsWith<UnsupportedOperationException> {
                    withViewport(Viewport(
                        0.25F,
                        2F,
                        16F,
                        10F,
                    )) { draw(0) }
                }
                draw(3)
            }
            assertEquals(1, driver.draws.size)
            assertEquals(before, driver.snapshot())
        }
    }

    @Test
    fun publicDrawDispatchesEveryTopologyAndRestoresHostState() {
        val modes = listOf(PrimitiveMode.Points, PrimitiveMode.Lines, PrimitiveMode.LineStrip, PrimitiveMode.Triangles, PrimitiveMode.TriangleStrip)
        DrawFixture().use { fixture ->
            val before = fixture.driver.snapshot()
            for ([index, topology] in PrimitiveTopology.entries.withIndex()) {
                val pipeline = fixture.pipeline(topology = topology)
                fixture.pass {
                    bindPipeline(pipeline)
                    draw(3, 2)
                }
                assertEquals(modes[index], fixture.driver.draws.last().mode)
                assertEquals(2, fixture.driver.draws.last().first)
                assertEquals(3, fixture.driver.draws.last().count)
                assertEquals(listOf(2.0, 41.0, 30.0, 20.0), fixture.driver.draws.last().viewport)
                assertEquals(listOf(2, 41, 30, 20), fixture.driver.draws.last().scissor)
                assertEquals(before, fixture.driver.snapshot())
            }
        }
    }

    @Test
    fun publicVertexDrawUsesDeclaredSubrangeWithPaddedStrideAndFirstVertex() {
        DrawFixture().use { fixture ->
            fixture.device.createBuffer(BufferDescription(
                label = "vertices",
                sizeBytes = 256,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            )).use { buffer ->
                val vertex = VertexState(listOf(VertexBufferLayout(
                    stride = 32,
                    attributes = listOf(VertexAttribute(
                        location = 2,
                        format = VertexFormat.Float32x3,
                        offset = 4,
                    )),
                )))
                val pipeline = fixture.pipeline(vertex)
                val view = GpuBufferView(
                    buffer,
                    40,
                    80,
                )
                val declaration = RenderPassResources(vertexBuffers = listOf(GpuBufferView(
                    buffer,
                    32,
                    128,
                )))
                val before = fixture.driver.snapshot()
                fixture.pass(declaration) {
                    bindPipeline(pipeline)
                    bindVertexBuffer(0, view)
                    draw(2, 1)
                    assertFailsWith<IllegalArgumentException> { draw(3, 1) }
                }
                assertEquals(1, fixture.driver.draws.size)
                assertEquals(listOf(501, 61, 2, 3, 0x1406, false, 32, 44L), fixture.driver.pointers.single())
                assertEquals(before, fixture.driver.snapshot())
            }
        }
    }

    @Test
    fun publicDrawPreservesNestedRegionsAndStillExecutesAnEmptyScissor() {
        DrawFixture().use { fixture ->
            val pipeline = fixture.pipeline()
            val before = fixture.driver.snapshot()
            fixture.pass {
                bindPipeline(pipeline)
                withViewport(Viewport(
                    0.25F,
                    2.5F,
                    16.75F,
                    10.25F,
                    0.8F,
                    0.2F,
                )) {
                    withScissor(ScissorRectangle(
                        4,
                        5,
                        6,
                        7,
                    )) { draw(3) }
                    withScissor(ScissorRectangle.Empty) { draw(3) }
                }
                draw(3)
            }
            assertEquals(3, fixture.driver.draws.size)
            assertEquals(listOf(0.25, 51.25, 16.75, 10.25), fixture.driver.draws[0].viewport)
            assertEquals(listOf(0.8F.toDouble(), 0.2F.toDouble()), fixture.driver.draws[0].depth)
            assertEquals(listOf(4, 52, 6, 7), fixture.driver.draws[0].scissor)
            assertEquals(listOf(0, 64, 0, 0), fixture.driver.draws[1].scissor)
            assertEquals(listOf(2.0, 41.0, 30.0, 20.0), fixture.driver.draws[2].viewport)
            assertEquals(listOf(2, 41, 30, 20), fixture.driver.draws[2].scissor)
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun zeroWorkRequiresBindingsPushInitializationAndValidViewportBeforeOmittingNativeWork() {
        DrawFixture().use { fixture ->
            val pipeline = fixture.pipeline(push = true)
            val before = fixture.driver.snapshot()
            fixture.pass {
                assertFailsWith<IllegalStateException> { draw(0) }
                bindPipeline(pipeline)
                assertFailsWith<IllegalStateException> { draw(0) }
                val bytes = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder())
                for (value in listOf(1F, 2F, 3F, 4F)) bytes.putFloat(value)
                bytes.flip()
                pushConstants(setOf(ShaderStage.Fragment), bytes)
                draw(0)
                draw(3, instanceCount = 0)
                assertFailsWith<UnsupportedOperationException> {
                    withViewport(Viewport(
                        0F,
                        0F,
                        4097F,
                        16F,
                    )) { draw(0) }
                }
            }
            assertTrue(fixture.driver.draws.isEmpty())
            assertTrue(fixture.driver.calls.none { it == "createVAO" })
            assertEquals(listOf(listOf(1F, 2F, 3F, 4F)), fixture.driver.uniformValues)
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun unboundVertexSlotIsRejectedEvenWhenVertexOrInstanceCountIsZero() {
        DrawFixture().use { fixture ->
            val pipeline = fixture.pipeline(VertexState(listOf(VertexBufferLayout(
                stride = 16,
                attributes = listOf(VertexAttribute(
                    1,
                    VertexFormat.Float32x3,
                    0,
                )),
            ))))
            fixture.pass {
                bindPipeline(pipeline)
                assertFailsWith<IllegalStateException> { draw(0) }
                assertFailsWith<IllegalStateException> { draw(3, instanceCount = 0) }
            }
            assertTrue(fixture.driver.draws.isEmpty())
        }
    }

    @Test
    fun undeclaredAndForeignVertexBindingsAreRejectedWithoutNativeInputMutation() {
        DrawFixture().use { fixture ->
            DrawStateDriver(true).device.use { otherDevice ->
                val description = BufferDescription(
                    label = "binding bounds",
                    sizeBytes = 256,
                    usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
                )
                fixture.device.createBuffer(description).use { buffer ->
                    otherDevice.createBuffer(description).use { other ->
                        fixture.pass {
                            assertFailsWith<IllegalArgumentException> { bindVertexBuffer(0, GpuBufferView(
                                buffer,
                                0,
                                buffer.sizeBytes,
                            )) }
                            assertFailsWith<IllegalArgumentException> { bindVertexBuffer(0, GpuBufferView(
                                other,
                                0,
                                other.sizeBytes,
                            )) }
                        }
                    }
                }
            }
            assertTrue(fixture.driver.pointers.isEmpty())
        }
    }

    @Test
    fun closedDeclaredBufferIsRejectedBeforeEnteringThePass() {
        DrawFixture().use { fixture ->
            val buffer = fixture.device.createBuffer(BufferDescription(
                label = "closed declaration",
                sizeBytes = 64,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            ))
            val resources = RenderPassResources(vertexBuffers = listOf(GpuBufferView(
                                buffer,
                                0,
                                buffer.sizeBytes,
                            )))
            buffer.close()
            var entered = false
            assertFailsWith<IllegalStateException> { fixture.pass(resources) { entered = true } }
            assertEquals(false, entered)
            assertEquals(11, fixture.driver.drawFramebuffer)
        }
    }

    @Test
    fun closedPipelineIsRevalidatedAtZeroDrawWithLiveBoundBuffer() {
        DrawFixture().use { fixture ->
            val buffer = fixture.device.createBuffer(BufferDescription(
                label = "bound lifetime",
                sizeBytes = 64,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            ))
            val view = GpuBufferView(
                                buffer,
                                0,
                                buffer.sizeBytes,
                            )
            val pipeline = fixture.pipeline(VertexState(listOf(VertexBufferLayout(
                stride = 16,
                attributes = listOf(VertexAttribute(
                    1,
                    VertexFormat.Float32x3,
                    0,
                )),
            ))))
            fixture.pass(RenderPassResources(vertexBuffers = listOf(view))) {
                bindPipeline(pipeline)
                bindVertexBuffer(0, view)
                pipeline.close()
                assertFailsWith<IllegalStateException> { draw(0) }
            }
            buffer.close()
            assertTrue(fixture.driver.draws.isEmpty())
        }
    }

    @Test
    fun activeGeometryReferencesHaltBeforeDeletionAndUnrelatedBuffersCanClose() {
        for (kind in listOf("vertex-declared", "vertex-bound", "index-declared", "index-bound", "attribute-zero")) {
            val marker = File.createTempFile("lethal-buffer-$kind", ".marker").apply { delete() }
            val process = ProcessBuilder(
                File(
                    System.getProperty("java.home"),
                    "bin/java",
                ).absolutePath,
                "-cp",
                System.getProperty("java.class.path"),
                "heckerpowered.render.opengl.OpenGLGeometryLifetimeFatalProbe",
                kind,
                marker.absolutePath,
            ).redirectErrorStream(true).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(1, process.exitValue(), output)
            assertTrue("active render pass" in output, output)
            assertFalse("delete-referenced-buffer" in output, output)
            assertFalse(marker.exists(), output)
        }
        DrawFixture().use { fixture ->
            val buffer = fixture.device.createBuffer(BufferDescription(
                "unrelated",
                64,
                setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            ))
            fixture.pass { buffer.close() }
            val retained = fixture.device.createBuffer(BufferDescription(
                "released declaration",
                64,
                setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            ))
            fixture.pass(RenderPassResources(vertexBuffers = listOf(GpuBufferView(
                retained,
                0,
                64,
            )))) {}
            retained.close()
        }
    }

    @Test
    fun unsupportedCapabilitiesAndDrawKindsRemainExplicitEvenForZeroCounts() {
        DrawFixture(DrawStateDriver(
            true,
            false,
        )).use { fixture ->
            val pipeline = fixture.pipeline()
            fixture.pass {
                bindPipeline(pipeline)
                assertFailsWith<UnsupportedOperationException> { draw(0) }
                assertFailsWith<IllegalStateException> { drawIndexed(0) }
            }
            assertTrue(fixture.driver.draws.isEmpty())
        }
        DrawFixture().use { fixture ->
            val pipeline = fixture.pipeline()
            fixture.pass {
                bindPipeline(pipeline)
                assertFailsWith<IllegalArgumentException> { draw(-1) }
                assertFailsWith<IllegalArgumentException> { draw(0, firstVertex = -1) }
                assertFailsWith<IllegalArgumentException> { draw(0, instanceCount = -1) }
                assertFailsWith<IllegalArgumentException> { draw(0, firstInstance = -1) }
                assertFailsWith<UnsupportedOperationException> { draw(0, instanceCount = 2) }
                assertFailsWith<UnsupportedOperationException> { draw(0, firstInstance = 1) }
                draw(3)
            }
            assertEquals(1, fixture.driver.draws.size)
        }
    }

    @Test
    fun zeroStrideConstantAddressIsRejectedWhenCreatingThePipeline() {
        DrawFixture().use { fixture ->
            assertFailsWith<UnsupportedOperationException> {
                fixture.pipeline(VertexState(listOf(VertexBufferLayout(
                    stride = 0,
                    attributes = listOf(VertexAttribute(
                        1,
                        VertexFormat.Float32x3,
                        0,
                    )),
                ))))
            }
            assertTrue(fixture.driver.pointers.isEmpty())
        }
    }

    @Test
    fun nativeDrawErrorsAndThrowablesInvalidateTheEncoderAndRestoreHostState() {
        for (throws in listOf(false, true)) {
            DrawFixture().use { fixture ->
                val pipeline = fixture.pipeline()
                val before = fixture.driver.snapshot()
                val failure = AssertionError("native draw failure")
                assertFailsWith<IllegalStateException> {
                    fixture.pass {
                        bindPipeline(pipeline)
                        if (throws) fixture.driver.drawFailure = failure else fixture.driver.drawError = true
                        if (throws) assertSame(failure, assertFailsWith<AssertionError> { draw(3) })
                        else assertFailsWith<OpenGLOperationException> { draw(3) }
                        assertFailsWith<IllegalStateException> { draw(3) }
                    }
                }
                assertEquals(before, fixture.driver.snapshot())
            }
        }
    }

    @Test
    fun failedNativeVertexArrayAllocationReleasesTheNameAndInvalidatesTheEncoder() {
        DrawFixture().use { fixture ->
            val pipeline = fixture.pipeline()
            val before = fixture.driver.snapshot()
            fixture.driver.allocationError = true
            assertFailsWith<OpenGLOperationException> { fixture.pass { bindPipeline(pipeline); draw(3) } }
            assertEquals(1, fixture.driver.calls.count { it == "deleteVAO:501" })
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun drawCleanupFailureHaltsAnIndependentJvmWithoutRunningShutdownHooks() {
        for (kind in listOf("bind", "delete", "restart")) {
            val marker = File.createTempFile("lethal-draw-cleanup-$kind", ".marker")
            marker.delete()
            val java = File(System.getProperty("java.home"), "bin/java").absolutePath
            val process = ProcessBuilder(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                "heckerpowered.render.opengl.OpenGLDrawFatalProbe",
                kind,
                marker.absolutePath,
            )
                .redirectErrorStream(true).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.exitValue() != 0, output)
            assertTrue("draw-completed" in output, output)
            val expected = when (kind) {
                "bind" -> "host VAO restore failed"
                "delete" -> "owned VAO release failed"
                else -> "restart restore failed"
            }
            assertTrue(expected in output, output)
            assertEquals(false, marker.exists(), output)
        }
    }

    @Test
    fun ownedVertexArrayRestoresHostBindingsAndLeavesOtherViewportEntriesUntouched() {
        val driver = DrawStateDriver(true)
        driver.device.use {
            val before = driver.snapshot()
            val state = OpenGLDrawState.capture(driver.device)
            try {
                state.configure(VertexState.Empty, emptyMap())
                state.apply(OpenGLDrawViewport.validate(driver.functions, Viewport(
                    0.25F,
                    2.5F,
                    16.75F,
                    10.25F,
                    0.8F,
                    0.2F,
                ), 64), ScissorRectangle(
                    3,
                    4,
                    5,
                    6,
                ), 64)
                assertEquals(501, driver.vertexArray)
                assertContentEquals(doubleArrayOf(0.25, 51.25, 16.75, 10.25), driver.viewports[0])
                assertEquals(before.viewports[1], driver.viewports[1].toList())
                assertEquals(before.depths[1], driver.depths[1].toList())
                assertEquals(before.scissors[1], driver.scissors[1].toList())
                assertTrue(driver.scissorEnabled[0])
            } finally {
                state.close()
            }
            assertEquals(before, driver.snapshot())
            state.close()
            assertEquals(1, driver.calls.count { it == "deleteVAO:501" })
            assertTrue(driver.calls.indexOf("bindVAO:7") < driver.calls.indexOf("deleteVAO:501"))
        }
    }

    @Test
    fun legacyRestorationKeepsOriginalIntegerViewportAndDoubleDepthPrecision() {
        val driver = DrawStateDriver(false)
        driver.viewports[0] = doubleArrayOf(16777217.0, 3.0, 100.0, 200.0)
        driver.device.use {
            val before = driver.snapshot()
            val state = OpenGLDrawState.capture(driver.device)
            try {
                state.apply(OpenGLDrawViewport.validate(driver.functions, Viewport(
                    1F,
                    2F,
                    16F,
                    10F,
                ), 64), ScissorRectangle(
                    3,
                    4,
                    5,
                    6,
                ), 64)
            } finally {
                state.close()
            }
            assertEquals(before, driver.snapshot())
        }
    }

    @Test
    fun attributePointerCapturesDeclaredViewOffsetAndImmediatelyRestoresArrayBuffer() {
        val driver = DrawStateDriver(true)
        driver.device.use {
            driver.device.createBuffer(BufferDescription(
                label = "draw vertices",
                sizeBytes = 256,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            )).use { buffer ->
                val before = driver.snapshot()
                val state = OpenGLDrawState.capture(driver.device)
                try {
                    state.configure(VertexState(listOf(VertexBufferLayout(
                        stride = 32,
                        attributes = listOf(
                            VertexAttribute(
                                2,
                                VertexFormat.Float32x3,
                                4,
                            ),
                            VertexAttribute(
                                5,
                                VertexFormat.Uint8x4Normalized,
                                16,
                            ),
                        ),
                    ))), mapOf(0 to GpuBufferView(
                        buffer,
                        40,
                        128,
                    )))
                    assertEquals(91, driver.arrayBuffer)
                    assertEquals(listOf(501, 61, 2, 3, 0x1406, false, 32, 44L), driver.pointers[0])
                    assertEquals(listOf(501, 61, 5, 4, 0x1401, true, 32, 56L), driver.pointers[1])
                    state.configure(VertexState.Empty, emptyMap())
                    assertEquals(setOf(11), driver.enabled.getValue(7).toSet())
                    assertEquals(emptySet<Int>(), driver.enabled.getValue(501).toSet())
                } finally {
                    state.close()
                }
                assertEquals(before, driver.snapshot())
            }
        }
    }

    @Test
    fun pointerFailureRestoresArrayBufferBeforePassStateCleanup() {
        val driver = DrawStateDriver(true)
        driver.device.use {
            driver.device.createBuffer(BufferDescription(
                label = "failing draw vertices",
                sizeBytes = 256,
                usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex),
            )).use { buffer ->
                val before = driver.snapshot()
                val state = OpenGLDrawState.capture(driver.device)
                val failure = AssertionError("pointer failure")
                try {
                    driver.pointerFailure = failure
                    val thrown = assertFailsWith<AssertionError> {
                        state.configure(VertexState(listOf(VertexBufferLayout(
                            stride = 16,
                            attributes = listOf(VertexAttribute(
                                2,
                                VertexFormat.Float32x3,
                                4,
                            )),
                        ))), mapOf(0 to GpuBufferView(
                            buffer,
                            40,
                            128,
                        )))
                    }
                    assertSame(failure, thrown)
                    assertEquals(91, driver.arrayBuffer)
                } finally {
                    state.close()
                }
                assertEquals(before, driver.snapshot())
            }
        }
    }

    @Test
    fun missingVertexArrayCapabilityIsRejectedWithoutCaptureOrAllocation() {
        val driver = DrawStateDriver(
            true,
            false,
        )
        driver.device.use {
            assertFailsWith<UnsupportedOperationException> { OpenGLDrawState.capture(driver.device) }
            assertTrue(driver.calls.isEmpty())
        }
    }

    @Test
    fun viewportArrayKeepsFractionalOriginAndReversedDepth() {
        val driver = ViewportDriver(true)
        val viewport = OpenGLDrawViewport.validate(driver.functions, Viewport(
            0.25F,
            2.5F,
            16.75F,
            10.25F,
            0.8F,
            0.2F,
        ), 64)
        assertEquals(0.25, viewport.x)
        assertEquals(51.25, viewport.y)
        assertEquals(16.75, viewport.width)
        assertEquals(10.25, viewport.height)
        assertEquals(0.8F.toDouble(), viewport.minDepth)
        assertEquals(0.2F.toDouble(), viewport.maxDepth)
        assertEquals(listOf("getIntegers", "getFloats"), driver.queries)
    }

    @Test
    fun legacyViewportRejectsFractionsInsteadOfTruncating() {
        val driver = ViewportDriver(false)
        assertFailsWith<UnsupportedOperationException> {
            OpenGLDrawViewport.validate(driver.functions, Viewport(
                0.25F,
                2F,
                16F,
                10F,
            ), 64)
        }
        assertEquals(listOf("getIntegers"), driver.queries)
    }

    @Test
    fun legacyViewportKeepsLargeIntegerNativeYWithoutFloatRounding() {
        val driver = ViewportDriver(false)
        val viewport = OpenGLDrawViewport.validate(driver.functions, Viewport(
            0F,
            0F,
            16F,
            10F,
        ), 16777217)
        assertEquals(16777207.0, viewport.y)
    }

    @Test
    fun dimensionClampingIsRejectedBeforeAnyNativeMutation() {
        val driver = ViewportDriver(true)
        assertFailsWith<UnsupportedOperationException> {
            OpenGLDrawViewport.validate(driver.functions, Viewport(
                0F,
                0F,
                4097F,
                10F,
            ), 64)
        }
        assertEquals(listOf("getIntegers", "getFloats"), driver.queries)
    }

    @Test
    fun viewportArrayOriginClampingIsRejected() {
        val driver = ViewportDriver(true)
        assertFailsWith<UnsupportedOperationException> {
            OpenGLDrawViewport.validate(driver.functions, Viewport(
                32769F,
                0F,
                16F,
                10F,
            ), 64)
        }
        assertEquals(listOf("getIntegers", "getFloats"), driver.queries)
    }

    @Test
    fun legacyViewportRejectsNativeYOutsideSignedIntegerRange() {
        val driver = ViewportDriver(false)
        assertFailsWith<UnsupportedOperationException> {
            OpenGLDrawViewport.validate(driver.functions, Viewport(
                0F,
                -2147483648F,
                16F,
                10F,
            ), 64)
        }
    }
}

private class ViewportDriver(private val arrays: Boolean) {
    val queries = mutableListOf<String>()
    val functions = Proxy.newProxyInstance(OpenGLFunctions::class.java.classLoader, arrayOf(OpenGLFunctions::class.java)) { _, method, arguments ->
        when (method.name) {
            "getError" -> 0
            "getSupportsViewportArrays" -> arrays
            "getIntegers" -> {
                queries.add(method.name)
                assertEquals(0x0D3A, arguments[0])
                val destination = arguments[1] as IntBuffer
                check(destination.remaining() >= 16)
                destination.put(0, 4096).put(1, 4096)
                Unit
            }
            "getFloats" -> {
                queries.add(method.name)
                assertEquals(0x825D, arguments[0])
                val destination = arguments[1] as FloatBuffer
                check(destination.remaining() >= 16)
                destination.put(0, -32768F).put(1, 32768F)
                Unit
            }
            else -> error("Unexpected native call ${method.name}")
        }
    } as OpenGLFunctions
}

internal class DrawStateDriver(
    private val viewportArrays: Boolean,
    private val hasVertexArrays: Boolean = true,
    private val legacyRaster: Boolean = false,
    val clipCount: Int = 8,
) {
    var vertexArray = 7
    var arrayBuffer = 91
    val elementBuffers = mutableMapOf(7 to 92)
    var baseVertexSupported = false
    var instancedDrawingSupported = false
    var instanceAttributesSupported = false
    var divisorError = false
    var divisorFailure: AssertionError? = null
    val divisors = mutableMapOf(7 to mutableMapOf(11 to 7))
    val divisorCalls = mutableListOf<List<Int>>()
    val instancedDraws = mutableListOf<List<Any>>()
    var restartSupported = false
    var fixedRestartSupported = false
    var clientRestartSupported = false
    var restartFailure: String? = null
    var fatalRestartRestore = false
    var elementBindingError = false
    var elementBindingFailure: AssertionError? = null
    var attributeZeroRequired = false
    var bufferCount = 0
    val indexedDraws = mutableListOf<List<Any>>()
    var program = 97
    var drawFramebuffer = 11
    var readFramebuffer = 10
    var textureBinding = 41
    var pointSize = 7F
    var lineWidth = 3F
    var depthFunction = 0x0204
    var depthWrite = false
    var nativeError = 0
    var drawError = false
    var drawFailure: Throwable? = null
    var allocationError = false
    var viewportQueryError = false
    var restoreFailure: String? = null
    val enables = mutableMapOf(0x8642 to true, 0x0B44 to true, 0x0BE2 to true)
    var cullMode = 0x0404
    var frontFace = 0x0900
    var polygonModes = listOf(0x1B01, 0x1B02)
    var blendFactors = listOf(0x0300, 0x0301, 0x0302, 0x0303)
    var blendEquations = listOf(0x800A, 0x800B)
    var writeMask = listOf(true, false, true, false)
    val draws = mutableListOf<DrawCall>()
    val uniformValues = mutableListOf<List<Float>>()
    val viewports = mutableListOf(doubleArrayOf(1.25, 3.5, 100.0, 200.0), doubleArrayOf(9.25, 8.5, 50.0, 60.0))
    val depths = mutableListOf(doubleArrayOf(0.123456789012345, 0.987654321098765), doubleArrayOf(0.3, 0.6))
    val scissors = mutableListOf(intArrayOf(1, 2, 3, 4), intArrayOf(5, 6, 7, 8))
    val scissorEnabled = mutableListOf(false, true)
    val enabled = mutableMapOf(7 to mutableSetOf(11))
    var observeDeletedBuffer: Int? = null
    val calls = mutableListOf<String>()
    val pointers = mutableListOf<List<Any>>()
    var pointerFailure: AssertionError? = null
    var pointerError = false
    private fun validateCapability(capability: Int) {
        if (capability in listOf(0x0BC0, 0x0B41, 0x0B10, 0x0B24, 0x0B42)) check(legacyRaster) { "Legacy capability queried in core profile" }
        when (capability) {
            0x8F9D -> check(restartSupported)
            0x8D69 -> check(fixedRestartSupported)
            0x8558 -> check(clientRestartSupported)
        }
        if (capability in 0x3000..0x30FF) check(capability < 0x3000 + clipCount) { "Clip capability exceeds native limit" }
    }
    private val arrays = object : OpenGLVertexArrayFunctions {
        override fun createVertexArray(): VertexArrayName {
            calls.add("createVAO")
            enabled[501] = mutableSetOf()
            elementBuffers[501] = 0
            divisors[501] = mutableMapOf()
            if (allocationError) nativeError = 0x0502
            return VertexArrayName(501)
        }
        override fun getBoundVertexArray(): VertexArrayName = VertexArrayName(vertexArray)
        override fun bindVertexArray(vertexArray: VertexArrayName) {
            calls.add("bindVAO:${vertexArray.value}")
            this@DrawStateDriver.vertexArray = vertexArray.value
            if (restoreFailure == "bind" && vertexArray.value == 7) throw AssertionError("host VAO restore failed")
        }
        override fun deleteVertexArray(vertexArray: VertexArrayName) {
            calls.add("deleteVAO:${vertexArray.value}")
            enabled.remove(vertexArray.value)
            elementBuffers.remove(vertexArray.value)
            divisors.remove(vertexArray.value)
            if (restoreFailure == "delete") throw AssertionError("owned VAO release failed")
        }
    }
    private val framebuffers = Proxy.newProxyInstance(OpenGLFramebufferFunctions::class.java.classLoader, arrayOf(OpenGLFramebufferFunctions::class.java)) { _, method, arguments ->
        when (method.name.substringBefore('-')) {
            "getBoundDrawFramebuffer" -> drawFramebuffer
            "getBoundReadFramebuffer" -> readFramebuffer
            "createFramebuffer" -> 20
            "bindDrawFramebuffer" -> { drawFramebuffer = arguments[0] as Int; null }
            "bindReadFramebuffer" -> { readFramebuffer = arguments[0] as Int; null }
            "framebufferTexture2D", "deleteFramebuffer" -> null
            "checkFramebufferStatus" -> 0x8CD5
            else -> error("Unexpected framebuffer call ${method.name}")
        }
    } as OpenGLFramebufferFunctions
    private val uniformBuffers = Proxy.newProxyInstance(OpenGLUniformBufferFunctions::class.java.classLoader, arrayOf(OpenGLUniformBufferFunctions::class.java)) { _, method, _ ->
        if (method.name == "getMaximumBindings") 16 else error("Unexpected uniform-buffer call ${method.name}")
    } as OpenGLUniformBufferFunctions
    val functions = Proxy.newProxyInstance(OpenGLFunctions::class.java.classLoader, arrayOf(OpenGLFunctions::class.java)) { _, method, rawArguments ->
        val arguments = rawArguments ?: emptyArray()
        when (method.name.substringBefore('-')) {
            "checkCurrentContext", "bufferData", "bufferSubData", "deleteShader", "deleteProgram", "textureParameter", "textureImage2D", "deleteTexture", "flush" -> null
            "deleteBuffer" -> { if (arguments[0] == observeDeletedBuffer) println("delete-referenced-buffer"); null }
            "getError" -> nativeError.also { nativeError = 0 }
            "getSupportsSeparateBlendEquations", "getSupportsNonPowerOfTwoTextures" -> true
            "getSupportsPixelBuffers", "getSupportsMultisample", "getSupportsClipControl", "getSupportsRasterizerDiscard", "getSupportsDepthBoundsTest" -> false
            "getSupportsLegacyRasterState" -> legacyRaster
            "getSupportsBaseVertex" -> baseVertexSupported
            "getSupportsInstancedDrawing" -> instancedDrawingSupported
            "getRequiresVertexAttributeZero" -> attributeZeroRequired
            "getSupportsInstanceAttributes" -> instanceAttributesSupported
            "getSupportsPrimitiveRestart" -> restartSupported
            "getSupportsFixedIndexPrimitiveRestart" -> fixedRestartSupported
            "getSupportsClientPrimitiveRestart" -> clientRestartSupported
            "getShaderColorClamping" -> null
            "getUniformBuffers" -> uniformBuffers
            "createTexture" -> 71
            "getBoundTexture2D" -> textureBinding
            "bindTexture2D" -> { textureBinding = arguments[0] as Int; null }
            "getTextureLevelParameter" -> if (arguments[1] == 0x1003) 0x8058 else 64
            "getCurrentProgram" -> program
            "getProgramInteger" -> if (arguments[1] == 0x8B80) 0 else 1
            "useProgram" -> { program = arguments[0] as Int; null }
            "getInteger" -> when (arguments[0]) {
                0x0B74 -> depthFunction
                0x0B72 -> if (depthWrite) 1 else 0
                0x0D32 -> clipCount
                0x0D33 -> 4096
                0x8869, 0x8A2B, 0x8A2D, 0x8A2E -> 16
                0x80C9 -> blendFactors[0]
                0x80C8 -> blendFactors[1]
                0x80CB -> blendFactors[2]
                0x80CA -> blendFactors[3]
                0x8009 -> blendEquations[0]
                0x883D -> blendEquations[1]
                0x0B45 -> cullMode
                0x0B46 -> frontFace
                else -> error("Unexpected scalar query ${arguments[0]}")
            }
            "isEnabled" -> {
                val capability = arguments[0] as Int
                validateCapability(capability)
                if (capability in listOf(0x8F9D, 0x8D69, 0x8558) && restartFailure == "isEnabled") { restartFailure = null; nativeError = 0x0502 }
                enables[capability] ?: false
            }
            "enable", "disable" -> {
                val capability = arguments[0] as Int
                validateCapability(capability)
                if (capability in listOf(0x8F9D, 0x8D69, 0x8558)) {
                    if (fatalRestartRestore && method.name == "enable") throw AssertionError("restart restore failed")
                    if (restartFailure == method.name) { restartFailure = null; nativeError = 0x0502 }
                }
                enables[capability] = method.name == "enable"
                null
            }
            "setBlendEnabled" -> { assertEquals(0, arguments[0]); enables[0x0BE2] = arguments[1] as Boolean; null }
            "blendFunctionSeparate" -> { assertEquals(5, arguments.size); blendFactors = arguments.takeLast(4).map { it as Int }; null }
            "blendEquationSeparate" -> { assertEquals(0, arguments[0]); blendEquations = arguments.takeLast(2).map { it as Int }; null }
            "colorMask" -> { assertEquals(5, arguments.size); writeMask = arguments.takeLast(4).map { it as Boolean }; null }
            "depthFunction" -> { depthFunction = arguments[0] as Int; null }
            "depthMask" -> { depthWrite = arguments[0] as Boolean; null }
            "cullFace" -> { cullMode = arguments[0] as Int; null }
            "frontFace" -> { frontFace = arguments[0] as Int; null }
            "polygonMode" -> {
                val face = arguments[0] as Int
                val mode = arguments[1] as Int
                polygonModes = polygonModes.mapIndexed { index, old -> if (face == 0x0408 || face == 0x0404 && index == 0 || face == 0x0405 && index == 1) mode else old }
                null
            }
            "pointSize" -> { pointSize = arguments[0] as Float; null }
            "lineWidth" -> { lineWidth = arguments[0] as Float; null }
            "uniformFloat4" -> { uniformValues.add(arguments.takeLast(4).map { it as Float }); null }
            "drawArrays", "drawArraysInstanced" -> {
                if (method.name == "drawArraysInstanced") {
                    assertTrue(instancedDrawingSupported)
                    instancedDraws.add(listOf(method.name) + arguments.toList().map { checkNotNull(it) })
                }
                assertEquals(501, vertexArray)
                assertEquals(1, program)
                assertEquals(20, drawFramebuffer)
                assertEquals(91, arrayBuffer)
                assertEquals(1F, pointSize)
                assertEquals(1F, lineWidth)
                assertEquals(false, enables[0x8642])
                for (index in 0 until clipCount) assertEquals(false, enables[0x3000 + index])
                if (legacyRaster) {
                    assertEquals(false, enables[0x0B24])
                    assertEquals(false, enables[0x0B42])
                }
                draws.add(DrawCall(
                    arguments[0] as PrimitiveMode,
                    arguments[1] as Int,
                    arguments[2] as Int,
                    viewports[0].toList(),
                    depths[0].toList(),
                    scissors[0].toList(),
                ))
                drawFailure?.let { throw it }
                if (drawError) nativeError = 0x0502
                null
            }
            "drawElements", "drawElementsBaseVertex", "drawElementsInstanced", "drawElementsInstancedBaseVertex" -> {
                if (method.name.contains("Instanced")) assertTrue(instancedDrawingSupported)
                assertEquals(501, vertexArray)
                assertEquals(1, program)
                assertEquals(20, drawFramebuffer)
                assertEquals(91, arrayBuffer)
                assertEquals(1F, pointSize)
                assertEquals(1F, lineWidth)
                for (cap in listOf(0x8F9D, 0x8D69, 0x8558)) assertFalse(enables[cap] == true)
                if (method.name.endsWith("BaseVertex")) assertTrue(baseVertexSupported)
                indexedDraws.add(listOf(method.name) + arguments.toList().map { checkNotNull(it) } + elementBuffers.getValue(vertexArray))
                drawFailure?.let { throw it }
                if (drawError) nativeError = 0x0502
                null
            }
            "getFramebuffers" -> framebuffers
            "getVertexArrays" -> if (hasVertexArrays) arrays else null
            "getSupportsViewportArrays" -> viewportArrays
            "getMaximumBufferSizeBytes" -> Long.MAX_VALUE
            "createBuffer" -> 61 + bufferCount++
            "getBoundBuffer" -> if (arguments[0] == BufferTarget.Array) arrayBuffer else elementBuffers.getValue(vertexArray)
            "bindBuffer" -> {
                if (arguments[0] == BufferTarget.Array) arrayBuffer = arguments[1] as Int else {
                    assertEquals(501, vertexArray)
                    elementBuffers[vertexArray] = arguments[1] as Int
                    elementBindingFailure?.let { throw it }
                    if (elementBindingError) nativeError = 0x0502
                }
                null
            }
            "getIntegers" -> {
                val destination = arguments[1] as IntBuffer
                check(destination.remaining() >= 16)
                val values = when (arguments[0]) {
                    0x0BA2 -> viewports[0].map(Double::toInt)
                    0x0D3A -> listOf(4096, 4096)
                    0x0C23 -> writeMask.map { if (it) 1 else 0 }
                    0x0B40 -> polygonModes
                    else -> error("Unexpected integer query")
                }
                values.forEachIndexed { index, value -> destination.put(index, value) }
                if (viewportQueryError && arguments[0] == 0x0D3A) nativeError = 0x0502
                null
            }
            "getFloats" -> {
                val destination = arguments[1] as FloatBuffer
                when (arguments[0]) {
                    0x825D -> destination.put(0, -32768F).put(1, 32768F)
                    0x0B11 -> destination.put(0, pointSize)
                    0x0B21 -> destination.put(0, lineWidth)
                    else -> error("Unexpected float query")
                }
                null
            }
            "getIndexedFloats" -> {
                assertEquals(0x0BA2, arguments[0]); assertEquals(0, arguments[1])
                val destination = arguments[2] as FloatBuffer
                check(destination.remaining() >= 16)
                viewports[0].forEachIndexed { index, value -> destination.put(index, value.toFloat()) }
                null
            }
            "getDoubles", "getIndexedDoubles" -> {
                assertEquals(0x0B70, arguments[0])
                val destination = arguments.last() as DoubleBuffer
                check(destination.remaining() >= 16)
                depths[0].forEachIndexed { index, value -> destination.put(index, value) }
                null
            }
            "getScissorBox" -> {
                assertEquals(0, arguments[0])
                val destination = arguments[1] as IntBuffer
                check(destination.remaining() >= 16)
                scissors[0].forEachIndexed { index, value -> destination.put(index, value) }
                null
            }
            "isScissorEnabled" -> scissorEnabled[arguments[0] as Int]
            "setScissorEnabled" -> { scissorEnabled[arguments[0] as Int] = arguments[1] as Boolean; null }
            "viewport" -> {
                if (arguments.size == 5) assertEquals(0, arguments[0])
                viewports[0] = arguments.takeLast(4).map { (it as Number).toDouble() }.toDoubleArray()
                null
            }
            "depthRange" -> { assertEquals(0, arguments[0]); depths[0] = doubleArrayOf(arguments[1] as Double, arguments[2] as Double); null }
            "scissor" -> { assertEquals(0, arguments[0]); scissors[0] = arguments.takeLast(4).map { it as Int }.toIntArray(); null }
            "vertexAttributePointer" -> {
                pointerFailure?.let { throw it }
                pointers.add(listOf(vertexArray, arrayBuffer) + arguments.toList())
                if (pointerError) nativeError = 0x0502
                null
            }
            "vertexAttributeDivisor" -> {
                assertTrue(instanceAttributesSupported)
                assertEquals(501, vertexArray)
                val location = arguments[0] as Int
                val divisor = arguments[1] as Int
                divisors.getValue(vertexArray)[location] = divisor
                divisorCalls.add(listOf(vertexArray, location, divisor))
                divisorFailure?.let { throw it }
                if (divisorError) nativeError = 0x0502
                null
            }
            "enableVertexAttribute" -> { enabled.getValue(vertexArray).add(arguments[0] as Int); null }
            "disableVertexAttribute" -> { enabled.getValue(vertexArray).remove(arguments[0] as Int); null }
            else -> error("Unexpected native call ${method.name}")
        }
    } as OpenGLFunctions
    val device = OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler())

    data class Snapshot(
        val vertexArray: Int,
        val arrayBuffer: Int,
        val hostElementBuffer: Int,
        val viewports: List<List<Double>>,
        val depths: List<List<Double>>,
        val scissors: List<List<Int>>,
        val scissorEnabled: List<Boolean>,
        val hostAttributes: Set<Int>,
        val hostDivisors: Map<Int, Int>,
        val program: Int,
        val drawFramebuffer: Int,
        val readFramebuffer: Int,
        val pointSize: Float,
        val lineWidth: Float,
        val enables: Map<Int, Boolean>,
        val cullMode: Int,
        val frontFace: Int,
        val polygonModes: List<Int>,
        val blendFactors: List<Int>,
        val blendEquations: List<Int>,
        val writeMask: List<Boolean>,
        val depthFunction: Int,
        val depthWrite: Boolean,
    )

    fun snapshot(): Snapshot = Snapshot(
        vertexArray,
        arrayBuffer,
        elementBuffers.getValue(7),
        viewports.map { it.toList() },
        depths.map { it.toList() },
        scissors.map { it.toList() },
        scissorEnabled.toList(),
        enabled.getValue(7).toSet(),
        divisors.getValue(7).toMap(),
        program,
        drawFramebuffer,
        readFramebuffer,
        pointSize,
        lineWidth,
        enables.filterValues { it },
        cullMode,
        frontFace,
        polygonModes.toList(),
        blendFactors.toList(),
        blendEquations.toList(),
        writeMask.toList(),
        depthFunction,
        depthWrite,
    )
}

internal data class DrawCall(
    val mode: PrimitiveMode,
    val first: Int,
    val count: Int,
    val viewport: List<Double>,
    val depth: List<Double>,
    val scissor: List<Int>,
)

private fun instanceOnlyState(stepMode: VertexStepMode = VertexStepMode.Instance): VertexState = VertexState(listOf(VertexBufferLayout(
    stride = 32,
    stepMode = stepMode,
    attributes = listOf(VertexAttribute(
        3,
        VertexFormat.Float32x4,
        4,
    )),
)))

private fun mixedInstancingState(): VertexState = VertexState(listOf(
    VertexBufferLayout(
        stride = 32,
        attributes = listOf(VertexAttribute(
            2,
            VertexFormat.Float32x4,
            4,
        )),
    ),
    instanceOnlyState().buffers.single(),
))

private fun withInstancingGeometry(fixture: DrawFixture, commands: (GpuBufferView, GpuBufferView, GpuBufferView) -> Unit) {
    fixture.device.createBuffer(BufferDescription(
        label = "instancing geometry",
        sizeBytes = 320,
        usage = setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex, heckerpowered.render.resource.buffer.BufferUsage.Index),
    )).use { buffer ->
        commands(GpuBufferView(
            buffer,
            12,
            116,
        ), GpuBufferView(
            buffer,
            144,
            84,
        ), GpuBufferView(
            buffer,
            240,
            64,
        ))
    }
}

private class DrawFixture(val driver: DrawStateDriver = DrawStateDriver(true)) : AutoCloseable {
    val device get() = driver.device
    private val modules = mutableListOf<OpenGLShaderModule>()
    private val stages = mutableListOf<OpenGLShaderStages>()
    private val layouts = mutableListOf<PipelineLayout>()
    private val pipelines = mutableListOf<OpenGLRenderPipeline>()
    private val texture = device.createTexture(TextureDescription(
        label = "draw target",
        width = 64,
        height = 64,
        format = TextureFormat.Rgba8UnsignedNormalized,
        usage = setOf(TextureUsage.ColorAttachment),
    ))
    val attachment = device.createAttachmentView(device.createTextureView(texture, TextureViewDescription()))

    fun pipeline(vertex: VertexState = VertexState.Empty, topology: PrimitiveTopology = PrimitiveTopology.TriangleList, push: Boolean = false, descriptor: Boolean = false): OpenGLRenderPipeline {
        val pair = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
            OpenGLShaderModule(
                device,
                ShaderName(stage.ordinal + 1),
                ShaderModuleDescription(
                    stage,
                    ShaderSource(
                        ShaderLanguage.Glsl,
                        "void main() {}",
                        "synthetic draw",
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
            ).also { modules.add(it) }
        }
        val member = if (push) listOf(OpenGLProgramPushConstant(
            ShaderStage.Fragment,
            OpenGLPushConstantMember(
                0,
                4,
                1,
                0,
                false,
                "color",
            ),
            UniformLocation(7),
        )) else emptyList()
        val shader = OpenGLShaderStages(
            device,
            ProgramName(1),
            pair,
            "synthetic draw",
            resourceInterface = OpenGLShaderInterface(
                if (descriptor) listOf(OpenGLProgramBinding(
                    OpenGLShaderBinding(
                        0,
                        0,
                        1,
                        OpenGLShaderBindingKind.UniformBuffer,
                        16,
                        "active block",
                    ),
                    setOf(ShaderStage.Fragment),
                    UniformBlockIndex(0),
                    requiredSizeBytes = 16,
                )) else emptyList(),
                member,
                vertex.buffers.flatMap { it.attributes }.map { OpenGLShaderInput(
                    it.location,
                    4,
                    "input${it.location}",
                ) },
            ),
        ).also { stages.add(it) }
        val layout = if (push || descriptor) device.createPipelineLayout(PipelineLayoutDescription(
            label = "draw layout",
            descriptorSets = if (descriptor) listOf(DescriptorSetLayout(listOf(DescriptorBindingLayout(
                binding = 0,
                type = DescriptorType.UniformBuffer(),
                stages = setOf(ShaderStage.Fragment),
            )))) else emptyList(),
            pushConstants = if (push) PushConstantLayout(listOf(PushConstantRange(
                stages = setOf(ShaderStage.Fragment),
                offsetBytes = 0,
                sizeBytes = 16,
            ))) else null,
        )).also { layouts.add(it) } else null
        return (device.createRenderPipeline(RenderPipelineDescription(
            label = "draw pipeline",
            shaders = shader,
            layout = layout,
            vertex = vertex,
            primitive = PrimitiveState(topology),
            colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)),
        )) as OpenGLRenderPipeline).also { pipelines.add(it) }
    }

    fun pass(resources: RenderPassResources = RenderPassResources.Empty, commands: RenderPass.() -> Unit) {
        device.encode("draw") {
            renderPass(RenderPassDescription(
                label = "draw pass",
                colorAttachments = listOf(RenderPassAttachment(attachment)),
                renderArea = RenderArea(
                    2,
                    3,
                    30,
                    20,
                ),
            ), resources, commands)
        }
    }

    fun closeBorrowedShaderModule() = modules.last().close()
    fun closeBorrowedShaderStages() = stages.last().close()

    override fun close() = terminateOnFailure {
        pipelines.forEach { it.close() }
        layouts.forEach { it.close() }
        stages.forEach { it.close() }
        modules.forEach { it.close() }
        texture.close()
        device.close()
    }
}

object OpenGLDrawFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { File(arguments[1]).writeText("hook ran") })
        DrawFixture().use { fixture ->
            if (arguments[0] == "restart") {
                fixture.driver.restartSupported = true
                fixture.driver.enables[0x8F9D] = true
            }
            val pipeline = fixture.pipeline()
            fixture.pass {
                bindPipeline(pipeline)
                draw(3)
                println("draw-completed")
                fixture.driver.restoreFailure = arguments[0]
                fixture.driver.fatalRestartRestore = arguments[0] == "restart"
            }
        }
    }
}

object OpenGLGeometryLifetimeFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { File(arguments[1]).writeText("hook ran") })
        DrawFixture().use { fixture ->
            val vertex = arguments[0].startsWith("vertex")
            val attributeZero = arguments[0] == "attribute-zero"
            fixture.driver.attributeZeroRequired = attributeZero
            fixture.driver.instanceAttributesSupported = attributeZero
            val buffer = if (attributeZero) fixture.device.primitives.unitQuad else fixture.device.createBuffer(BufferDescription(
                "geometry lifetime",
                64,
                setOf(heckerpowered.render.resource.buffer.BufferUsage.Vertex, heckerpowered.render.resource.buffer.BufferUsage.Index, heckerpowered.render.resource.buffer.BufferUsage.Uniform),
            ))
            val view = GpuBufferView(
                buffer,
                0,
                buffer.sizeBytes,
            )
            fixture.driver.observeDeletedBuffer = (buffer as OpenGLBuffer).name.value
            fixture.pass(RenderPassResources(
                vertexBuffers = if (vertex) listOf(view) else emptyList(),
                indexBuffers = if (vertex || attributeZero) emptyList() else listOf(view),
            )) {
                if (arguments[0].endsWith("bound")) {
                    if (vertex) bindVertexBuffer(0, view) else bindIndexBuffer(view, IndexFormat.Uint8)
                }
                buffer.close()
            }
        }
        error("Fatal boundary returned")
    }
}
