/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.IndexedRange
import heckerpowered.render.engine.geometry.VertexRange
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.command.pass.RenderPassRegions
import heckerpowered.render.command.pass.RenderPassResources
import heckerpowered.render.engine.pass.PreparedRenderPass
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.PipelineLayoutDescription
import heckerpowered.render.pipeline.PushConstantLayout
import heckerpowered.render.pipeline.PushConstantRange
import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.pipeline.vertex.VertexAttribute
import heckerpowered.render.pipeline.vertex.VertexBufferLayout
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.ShaderStages
import heckerpowered.render.shader.binding.DescriptorBinding
import heckerpowered.render.shader.binding.DescriptorBindingLayout
import heckerpowered.render.shader.binding.DescriptorResource
import heckerpowered.render.shader.binding.DescriptorSet
import heckerpowered.render.shader.binding.DescriptorSetLayout
import heckerpowered.render.shader.binding.DescriptorType
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.test.*

class PreparedDrawCommandTest {
    @Test
    fun fullscreenAndIndexedShaderGeneratedDrawsNeedNoVertexBuffers() {
        val indices = IndexInput(bufferView(16, BufferUsage.Index), IndexFormat.Uint16)
        val sequence = preparedPass(listOf(
            preparedDraw(pipeline("fullscreen"), emptyMap(), null, emptyMap(), emptyList(), VertexRange(3)),
            preparedDraw(
                pipeline("generated"),
                indexInput = indices,
                arguments = IndexedRange(
                    6,
                    firstIndex = 2,
                    baseVertex = -7,
                    instanceCount = 3,
                    firstInstance = 4
                )
            ),
        ))
        val recording = Recording(sequence.resources)
        sequence.encode(recording.pass)
        assertEquals(listOf(
            "pipeline:fullscreen",
            "draw:3,0,1,0",
            "pipeline:generated",
            "index:Uint16",
            "indexed:6,2,-7,3,4",
        ), recording.events)
        assertTrue(sequence.resources.vertexBuffers.isEmpty())
        assertEquals(listOf(indices.view), sequence.resources.indexBuffers)
    }

    @Test
    fun preparationRejectsMissingAndOutOfRangeGeometryBeforeEncoding() {
        val vertices = bufferView(36, BufferUsage.Vertex)
        val pipeline = pipeline("triangle", positionState())
        assertFailsWith<IllegalStateException> {
            preparedDraw(pipeline, emptyMap(), null, emptyMap(), emptyList(), VertexRange(3))
        }
        assertFailsWith<IllegalArgumentException> {
            preparedDraw(pipeline, mapOf(0 to vertices), null, emptyMap(), emptyList(), VertexRange(3, 1))
        }
        assertFailsWith<IllegalArgumentException> {
            preparedDraw(pipeline, mapOf(1 to vertices), null, emptyMap(), emptyList(), VertexRange(3))
        }
        assertFailsWith<IllegalStateException> {
            preparedDraw(pipeline("missing-index"), emptyMap(), null, emptyMap(), emptyList(), IndexedRange(0))
        }
        assertFailsWith<IllegalArgumentException> {
            preparedDraw(pipeline("extra-index"),
                indexInput = IndexInput(bufferView(6, BufferUsage.Index), IndexFormat.Uint16),
                arguments = VertexRange(3))
        }
        assertFailsWith<IllegalArgumentException> {
            preparedDraw(pipeline("index-overrun"),
                indexInput = IndexInput(bufferView(6, BufferUsage.Index), IndexFormat.Uint16),
                arguments = IndexedRange(3, 1))
        }
    }

    @Test
    fun argumentAndIndexValidationPreservesSignedBaseVertex() {
        assertEquals(Int.MIN_VALUE, IndexedRange(0, 0, Int.MIN_VALUE).baseVertex)
        assertFailsWith<IllegalArgumentException> { VertexRange(-1) }
        assertFailsWith<IllegalArgumentException> { VertexRange(3, 0, 1, -1) }
        assertFailsWith<IllegalArgumentException> { IndexedRange(3, -1) }
        assertFailsWith<IllegalArgumentException> { IndexedRange(3, 0, 0, -1) }
        assertFailsWith<IllegalArgumentException> { IndexInput(bufferView(6, BufferUsage.Vertex), IndexFormat.Uint16) }
        assertFailsWith<IllegalArgumentException> { IndexInput(bufferView(3, BufferUsage.Index), IndexFormat.Uint16) }
    }

    @Test
    fun preparedCommandsRetainTheOriginalVertexAndIndexSelections() {
        val ranges = listOf(VertexRange(3, 7, 2, 5), IndexedRange(6, 3, -7, 4, 2))
        val recording = Recording(RenderPassResources.Empty)
        for (range in ranges) {
            val index = if (range is IndexedRange) IndexInput(bufferView(32, BufferUsage.Index), IndexFormat.Uint16) else null
            val command = preparedDraw(pipeline("selection"), indexInput = index, arguments = range)
            assertSame(range, command.arguments)
        }
        val zeroInstances = VertexRange(3, instanceCount = 0)
        assertTrue(zeroInstances.isEmpty)
        preparedDraw(pipeline("zero instances"), arguments = zeroInstances).encode(recording.pass)
        assertEquals(listOf("pipeline:zero instances", "draw:3,0,0,0"), recording.events)
    }

    @Test
    fun bindingsWritesAndSequenceAreSnapshotsOfCallerCollections() {
        val vertices = bufferView(36, BufferUsage.Vertex)
        val uniform = descriptor(bufferView(16, BufferUsage.Uniform))
        val vertexBindings = linkedMapOf(0 to vertices)
        val sets = linkedMapOf(2 to uniform)
        val stages = mutableSetOf(ShaderStage.Vertex)
        val bytes = byteArrayOf(1, 2, 3, 4)
        val write = PreparedPushConstantWrite(stages, bytes, 8)
        val writes = mutableListOf(write)
        val layoutDescription = PipelineLayoutDescription(
            descriptorSets = listOf(DescriptorSetLayout.Empty, DescriptorSetLayout.Empty, uniform.layout),
            pushConstants = PushConstantLayout(listOf(PushConstantRange(setOf(ShaderStage.Vertex), 8, 4))),
            label = "snapshot",
        )
        val layout = proxy<PipelineLayout> { name, _ -> error("Unexpected layout access: $name") }
        val draw = preparedDraw(
            pipeline("triangle", positionState(), layout),
            vertexBindings,
            descriptorSets = sets,
            pushConstants = writes,
            arguments = VertexRange(3)
        )
        val draws = mutableListOf(draw)
        val sequence = preparedPass(draws)
        assertSame(layout, draw.pipeline.layout)

        vertexBindings.clear()
        sets.clear()
        stages.clear()
        bytes.fill(9)
        writes.clear()
        draws.clear()

        val recording = Recording(sequence.resources, mapOf(layout to layoutDescription))
        sequence.encode(recording.pass)
        assertEquals(listOf(
            "pipeline:triangle",
            "vertex:0",
            "descriptor:2",
            "push:Vertex:8:1,2,3,4",
            "draw:3,0,1,0"
        ), recording.events)
        assertSame(draw, sequence.draws.single())
        assertFailsWith<UnsupportedOperationException> { (draw.vertexBuffers as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (draw.descriptorSets as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (draw.pushConstants as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (sequence.draws as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (write.stages as MutableSet).clear() }
    }

    @Test
    fun resourceCollectionPreservesRangeHolesAndSeparateAccessRoles() {
        val buffer = buffer(64, setOf(BufferUsage.Vertex, BufferUsage.Index, BufferUsage.Uniform))
        val first = GpuBufferView(buffer, 0, 12)
        val second = GpuBufferView(buffer, 24, 12)
        val uniformView = GpuBufferView(buffer, 40, 16)
        val indices = IndexInput(GpuBufferView(buffer, 16, 6), IndexFormat.Uint16)
        val uniform = descriptor(uniformView)
        val sequence = preparedPass(listOf(
            preparedDraw(
                pipeline("first", positionState()),
                mapOf(0 to first),
                descriptorSets = mapOf(0 to uniform),
                arguments = VertexRange(1)
            ),
            preparedDraw(
                pipeline("second", positionState()),
                mapOf(0 to second),
                indexInput = indices,
                arguments = IndexedRange(3)
            ),
        ))
        val resources = sequence.resources
        resources.validateVertexBuffer(first)
        resources.validateVertexBuffer(second)
        resources.validateIndexBuffer(indices.view)
        resources.validateDescriptorSet(uniform)
        assertEquals(listOf(first, second), resources.vertexBuffers)
        assertEquals(listOf(indices.view), resources.indexBuffers)
        assertFailsWith<IllegalArgumentException> { resources.validateVertexBuffer(GpuBufferView(buffer, 0, 36)) }
        assertFailsWith<IllegalArgumentException> { resources.validateIndexBuffer(first) }
        assertFailsWith<IllegalArgumentException> { resources.validateVertexBuffer(uniformView) }
        assertFailsWith<IllegalArgumentException> { resources.validateDescriptorSet(descriptor(first)) }
    }

    @Test
    fun emptySequencesAndZeroCountDrawsRetainTheirDifferentMeanings() {
        val empty = preparedPass(emptyList())
        assertSame(RenderPassResources.Empty, empty.resources)
        val emptyRecording = Recording(empty.resources)
        empty.encode(emptyRecording.pass)
        assertTrue(emptyRecording.events.isEmpty())

        val zero = preparedPass(listOf(
            preparedDraw(pipeline("zero"), emptyMap(), null, emptyMap(), emptyList(), VertexRange(0)),
        ))
        val recording = Recording(zero.resources)
        zero.encode(recording.pass)
        assertEquals(listOf("pipeline:zero", "draw:0,0,1,0"), recording.events)
    }

    @Test
    fun pushWritesRejectUnalignedOverflowingAndUndersizedSelections() {
        val vertex = setOf(ShaderStage.Vertex)
        assertFailsWith<IllegalArgumentException> { PreparedPushConstantWrite(emptySet(), ByteArray(4)) }
        assertFailsWith<IllegalArgumentException> { PreparedPushConstantWrite(vertex, ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { PreparedPushConstantWrite(vertex, ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { PreparedPushConstantWrite(vertex, ByteArray(4), 2) }
        assertFailsWith<IllegalArgumentException> { PreparedPushConstantWrite(vertex, ByteArray(4), Int.MAX_VALUE - 3) }
        val write = PreparedPushConstantWrite(vertex, byteArrayOf(1, 2, 3, 4))
        assertFailsWith<IllegalArgumentException> { write.copyTo(ByteBuffer.allocate(3)) }
        val destination = ByteBuffer.allocate(8).apply { position(2) }
        write.copyTo(destination)
        assertContentEquals(byteArrayOf(0, 0, 1, 2, 3, 4, 0, 0), destination.array())
    }

    @Test
    fun pushWritesCanBeRecordedAgainAfterFailureWithoutAccessingMemoryStack() {
        val write = PreparedPushConstantWrite(setOf(ShaderStage.Vertex), byteArrayOf(1, 2, 3, 4))
        val draw = preparedDraw(
            pipeline("push"),
            pushConstants = listOf(write),
            arguments = VertexRange(3)
        )
        val recording = Recording(RenderPassResources.Empty)
        recording.failPush = true
        assertFailsWith<IllegalStateException> { draw.encode(recording.pass) }
        recording.failPush = false
        repeat(20) { draw.encode(recording.pass) }
        assertEquals(20, recording.events.count { it == "draw:3,0,1,0" })
        assertEquals(0, recording.memoryStackRequests)
    }

    @Test
    fun preparedPushBytesExposeOnlyIndependentBoundedReadOnlyViews() {
        val input = byteArrayOf(1, 2, 3, 4)
        val write = PreparedPushConstantWrite(setOf(ShaderStage.Vertex), input)
        input.fill(9)
        val first = write.asByteBuffer()
        assertTrue(first.isReadOnly)
        assertFalse(first.isDirect)
        assertEquals(4, first.capacity())
        assertEquals(java.nio.ByteOrder.nativeOrder(), first.order())
        assertFailsWith<java.nio.ReadOnlyBufferException> { first.put(0, 9) }
        assertFailsWith<java.nio.ReadOnlyBufferException> { first.array() }
        first.position(2)
        val second = write.asByteBuffer()
        assertEquals(0, second.position())
        assertEquals(4, second.limit())
        val observed = ByteArray(4)
        second.get(observed)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), observed)
        assertEquals(2, first.position())
    }

    @Test
    fun consecutiveCommandsApplyTheirRasterStateAndRestoreRegionsAfterFailure() {
        val viewport = Viewport(8f, 12f, 24f, 16f)
        val scissor = ScissorRectangle(10, 14, 6, 4)
        val first = preparedDraw(
            pipeline("first"),
            arguments = VertexRange(3),
            viewport = viewport,
            scissor = scissor,
            stencilReference = 7u
        )
        val second = preparedDraw(pipeline("second"), arguments = VertexRange(3))
        val recording = Recording(RenderPassResources.Empty)
        first.encode(recording.pass)
        second.encode(recording.pass)
        assertEquals(listOf(
            Triple(viewport, scissor, 7.toUByte()),
            Triple(second.viewport, second.scissor, 0.toUByte()),
        ), recording.rasterStates)
        assertEquals(Viewport.from(recording.area), recording.regions.viewport)
        assertEquals(ScissorRectangle.from(recording.area), recording.regions.scissor)

        val write = PreparedPushConstantWrite(setOf(ShaderStage.Vertex), byteArrayOf(1, 2, 3, 4))
        val failing = preparedDraw(
            pipeline("failing"),
            pushConstants = listOf(write),
            arguments = VertexRange(3),
            viewport = viewport,
            scissor = scissor
        )
        recording.failPush = true
        assertFailsWith<IllegalStateException> { failing.encode(recording.pass) }
        assertEquals(Viewport.from(recording.area), recording.regions.viewport)
        assertEquals(ScissorRectangle.from(recording.area), recording.regions.scissor)
    }

    private fun preparedPass(draws: List<PreparedDrawCommand>) = PreparedRenderPass(
        RenderPassDescription("test", renderArea = RenderArea(0, 0, 1, 1)),
        emptyList(),
        draws,
    )

    private fun preparedDraw(pipeline: RenderPipelineDescription, vertexBuffers: Map<Int, GpuBufferView> = emptyMap(), indexInput: IndexInput? = null, descriptorSets: Map<Int, DescriptorSet> = emptyMap(), pushConstants: List<PreparedPushConstantWrite> = emptyList(), arguments: DrawRange, viewport: Viewport = Viewport(0f, 0f, 1f, 1f), scissor: ScissorRectangle = ScissorRectangle(0, 0, 1, 1), stencilReference: UByte = 0u) = PreparedDrawCommand(
        pipeline,
        vertexBuffers,
        indexInput,
        descriptorSets,
        pushConstants,
        arguments,
        viewport,
        scissor,
        stencilReference
    )

    private fun positionState() = VertexState(listOf(VertexBufferLayout(
        stride = 12,
        attributes = listOf(VertexAttribute(0, VertexFormat.Float32x3, 0)),
    )))

    private fun pipeline(label: String, vertex: VertexState = VertexState.Empty, layout: PipelineLayout? = null) = RenderPipelineDescription(
        label,
        proxy<ShaderStages> { name, _ -> error("Unexpected shader access: $name") },
        layout = layout,
        vertex = vertex,
        colorTargets = emptyList(),
    )

    private fun bufferView(size: Long, usage: BufferUsage) = GpuBufferView(buffer(size, setOf(usage)), 0, size)

    private fun buffer(size: Long, usage: Set<BufferUsage>): GpuBuffer = proxy { name, _ ->
        when (name) {
            "getSizeBytes" -> size
            "getUsage" -> usage
            else -> error("Unexpected buffer access: $name")
        }
    }

    private fun descriptor(view: GpuBufferView) = DescriptorSet(
        DescriptorSetLayout(listOf(DescriptorBindingLayout(0, DescriptorType.UniformBuffer(), setOf(ShaderStage.Vertex)))),
        listOf(DescriptorBinding(0, DescriptorResource.Buffer(view))),
    )

    private class Recording(private val resources: RenderPassResources, private val layouts: Map<PipelineLayout, PipelineLayoutDescription> = emptyMap()) {
        val events = mutableListOf<String>()
        val area = RenderArea(0, 0, 64, 64)
        val regions = RenderPassRegions(area) {}
        val rasterStates = mutableListOf<Triple<Viewport, ScissorRectangle, UByte>>()
        private var stencilReference: UByte = 0u
        private var selectedLayout: PipelineLayoutDescription? = null
        var memoryStackRequests = 0
        var failPush = false
        val pass: RenderPass = proxy { name, arguments ->
            if (name == "withViewport" || name == "withScissor") {
                @Suppress("UNCHECKED_CAST")
                val commands = arguments[1] as RenderPass.() -> Unit
                return@proxy if (name == "withViewport") regions.withViewport(arguments[0] as Viewport) { commands(pass) }
                else regions.withScissor(arguments[0] as ScissorRectangle) { commands(pass) }
            }
            if (name == "getMemoryStack") {
                memoryStackRequests++
                error("Prepared push writes must not access the encoder memory stack")
            }
            when (name) {
                "setStencilReference" -> { stencilReference = (arguments[0] as Byte).toUByte() }
                "bindPipeline" -> {
                    val pipeline = arguments[0] as RenderPipelineDescription
                    selectedLayout = pipeline.layout?.let { layouts[it] }
                    events.add("pipeline:${pipeline.label}")
                }
                "bindVertexBuffer" -> {
                    resources.validateVertexBuffer(arguments[1] as GpuBufferView)
                    events.add("vertex:${arguments[0]}")
                }
                "bindIndexBuffer" -> {
                    resources.validateIndexBuffer(arguments[0] as GpuBufferView)
                    events.add("index:${arguments[1]}")
                }
                "bindDescriptorSet" -> {
                    selectedLayout?.validateDescriptorSet(arguments[0] as Int, arguments[1] as DescriptorSet)
                    resources.validateDescriptorSet(arguments[1] as DescriptorSet)
                    events.add("descriptor:${arguments[0]}")
                }
                "pushConstants" -> {
                    check(!failPush) { "Injected recording failure" }
                    val source = arguments[1] as ByteBuffer
                    @Suppress("UNCHECKED_CAST")
                    selectedLayout?.validatePushConstantWrite(arguments[0] as Set<ShaderStage>, arguments[2] as Int, source.remaining())
                    assertTrue(source.isReadOnly)
                    val bytes = ByteArray(source.remaining())
                    source.duplicate().get(bytes)
                    val stages = (arguments[0] as Set<*>).joinToString(",")
                    events.add("push:$stages:${arguments[2]}:${bytes.joinToString(",")}")
                }
                "draw", "drawIndexed" -> {
                    rasterStates += Triple(regions.viewport, regions.scissor, stencilReference)
                    val command = if (name == "draw") "draw" else "indexed"
                    events.add("$command:${arguments.joinToString(",")}")
                }
                else -> error("Unexpected pass command: $name")
            }
            null
        }
    }
}

private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, arguments ->
        when (method.name) {
            "equals" -> instance === arguments?.get(0)
            "hashCode" -> System.identityHashCode(instance)
            "toString" -> T::class.java.simpleName
            else -> invoke(method.name.substringBefore('-'), arguments ?: emptyArray())
        }
    } as T
