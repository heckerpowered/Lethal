/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Matrices
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.geometry.*
import heckerpowered.render.engine.geometry.index.IndexSelection
import heckerpowered.render.engine.geometry.index.IndexSource
import heckerpowered.render.engine.geometry.vertex.*
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.scene.drawing.WorldDrawing
import heckerpowered.render.engine.scene.geometryElement
import heckerpowered.render.engine.shader.binding.AttributeInput
import heckerpowered.render.engine.shader.binding.PushConstantField
import heckerpowered.render.engine.shader.binding.PushConstantInterface
import heckerpowered.render.engine.shader.binding.VertexInterface
import heckerpowered.render.engine.shader.program.GeometryInput
import heckerpowered.render.engine.shader.program.MeshShader
import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

class GeometryElementPassProcessorTest {
    @Test
    fun indexedGeometryGroupPreservesStorageRangesInstancesAndSignedBaseVertex() {
        val vertices = view(36, BufferUsage.Vertex)
        val indices = view(16, BufferUsage.Index)
        val layout = GeometryLayout(listOf(VertexStreamLayout(12, listOf(GeometryAttribute(semantic = VertexSemantics.Position, format = VertexFormat.Float32x3, offsetBytes = 0)))))
        val indexSource = IndexSource.Resident(IndexSelection(indices, IndexFormat.Uint16))
        val geometry = VertexGeometry(layout, listOf(VertexStreamSource.Resident(vertices)), GeometrySelection.Indexed(indexSource, DrawRange.indices(3, 1, -7, 2, 4)), PrimitiveState(PrimitiveTopology.TriangleList))
        val shader = MeshShader<RenderGeometry>(
            modules(),
            VertexInterface(listOf(AttributeInput(semantic = VertexSemantics.Position, location = 0, format = VertexFormat.Float32x3))),
            label = "test",
            encode = { GeometryInput(it) },
        )
        val collected = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            GeometryGroup(listOf(geometry, geometry.selecting(GeometrySelection.Indexed(indexSource, DrawRange.indices(3, 4, 5))))).parts.forEach { geometry(shader.bind(it)) }
        }
        val pass = RasterPass(
            RenderPassDescription("test", renderArea = RenderArea(0, 0, 1, 1)),
            collected,
            ViewParameters(Matrices.Identity, 1, 1, CompareFunction.Always),
        )
        ResourceLifetime.build {
            try {
                val preparation = PassPreparation(proxy<GraphicsDevice> { error("Resident geometry must not allocate") }, this)
                val processor = GeometryElementPassProcessor(ShaderRealizations(shaderDevice(), this))
                val draws = collected.submissions.flatMap { processor.prepare(it.geometryElement, it, pass, preparation) }
                assertEquals(listOf(IndexedRange(3, 1, -7, 2, 4), IndexedRange(3, 4, 5)), draws.map { it.arguments })
                collected.submissions.zip(draws).forEach { [submission, draw] ->
                    assertSame(submission.geometryElement.geometry.range, draw.arguments)
                }
                draws.forEach {
                    assertSame(vertices, it.vertexBuffers[0])
                    assertSame(indices, it.indexInput?.view)
                    assertEquals(geometry.primitive, it.pipeline.primitive)
                }
                assertTrue(preparation.snapshot().isEmpty())
            } finally {
                close()
            }
        }
    }


    @Test
    fun replayAndZeroWorkAreCheckedBeforeDeviceAccess() {
        val shader = MeshShader<RenderGeometry>(modules(), VertexInterface(emptyList()), label = "test", encode = { GeometryInput(it) })
        val geometry = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))
        val element = shader.bind(geometry)
        val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) { submit(element) }
        val submission = collection.submissions.single()
        val description = RenderPassDescription("test", renderArea = RenderArea(0, 0, 1, 1))
        val inputs = ViewParameters(Matrices.Identity, 1, 1, CompareFunction.Always)
        ResourceLifetime.build {
            try {
                val device = proxy<GraphicsDevice> { error("Rejected or empty work must not access the device") }
                val preparation = PassPreparation(device, this)
                val processor = GeometryElementPassProcessor(ShaderRealizations(device, this))
                assertFailsWith<IllegalArgumentException> { processor.prepare(element, submission, RasterPass(description, collection, inputs, requireReplaySafe = true), preparation) }
                val empty = shader.bind(ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(0)), geometry.primitive))
                assertTrue(processor.prepare(empty, submission.copy(element = empty), RasterPass(description, collection, inputs), preparation).isEmpty())
                val noInstances = shader.bind(ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3, instances = 0)), geometry.primitive))
                assertTrue(processor.prepare(noInstances, submission.copy(element = noInstances), RasterPass(description, collection, inputs), preparation).isEmpty())
                val requiringAttributes = MeshShader<RenderGeometry>(
                    modules(),
                    VertexInterface(listOf(AttributeInput(VertexSemantics.Position, 0, VertexFormat.Float32x3))),
                    label = "required attributes",
                    encode = { GeometryInput(it) },
                )
                val missingAttributes = requiringAttributes.bind(geometry)
                assertFailsWith<IllegalArgumentException> { processor.prepare(missingAttributes, submission.copy(element = missingAttributes), RasterPass(description, collection, inputs), preparation) }
                val mismatched = element.copy(composition = CompositingMode.SourceOver)
                assertFailsWith<IllegalArgumentException> { processor.prepare(mismatched, submission.copy(element = mismatched), RasterPass(description, collection, inputs), preparation) }
            } finally {
                close()
            }
        }
    }

    @Test
    fun replaySafeShaderPreparesTheSameCollectionForTwoColorPasses() {
        var bound = 0
        val shader = MeshShader<RenderGeometry>(
            modules(),
            VertexInterface(emptyList()),
            replaySafe = true,
            label = "test",
            encode = { bound++; GeometryInput(it) },
        )
        val geometry = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))
        val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            geometry(shader.bind(geometry))
        }
        val inputs = ViewParameters(Matrices.Identity, 2, 2, CompareFunction.Always)
        ResourceLifetime.build {
            try {
                val device = shaderDevice()
                val processor = GeometryElementPassProcessor(ShaderRealizations(device, this))
                val commands = listOf("first color", "second color").map { label ->
                    val pass = RasterPass(RenderPassDescription(label, renderArea = RenderArea(0, 0, 2, 2)), collection, inputs, requireReplaySafe = true)
                    val submission = collection.submissions.single()
                    processor.prepare(submission.geometryElement, submission, pass, PassPreparation(device, this)).single()
                }
                assertEquals(1, bound)
                assertNotSame(commands[0], commands[1])
                assertSame(commands[0].pipeline.shaders, commands[1].pipeline.shaders)
                assertEquals(commands[0].arguments, commands[1].arguments)
                assertEquals(commands[0].viewport, commands[1].viewport)
                assertEquals(commands[0].scissor, commands[1].scissor)
            } finally {
                close()
            }
        }
    }

    @Test
    fun actualProcessorResolvesViewAndShadingBytesAndRasterScope() {
        val pushes = PushConstantInterface(
            setOf(ShaderStage.Vertex, ShaderStage.Fragment),
            0,
            80,
            listOf(
                PushConstantField(heckerpowered.render.engine.material.parameter.ParameterName("clipFromLocal"), 0, 64),
                PushConstantField(heckerpowered.render.engine.material.parameter.ParameterName("value"), 64, 16)
            ),
        )
        val geometry = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))
        val shader = MeshShader<Float>(
            modules(),
            VertexInterface(emptyList()),
            pushes = listOf(pushes),
            replaySafe = true,
            label = "test",
            encode = { GeometryInput(geometry, ParameterValues().replacing("value", heckerpowered.render.engine.material.parameter.NumericParameterValue.floats(it, 2f, 3f, 4f))) },
        )
        val viewport = heckerpowered.render.command.pass.Viewport(0f, 0f, 10f, 10f)
        val clip = heckerpowered.render.command.pass.ScissorRectangle(2, 3, 4, 5)
        val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            withViewport(viewport) { clip(clip) { withStencilReference(7u) { geometry(shader.bind(.5f)) } } }
        }
        val submission = collection.submissions.single()
        val pass = RasterPass(
            RenderPassDescription("test", renderArea = RenderArea(0, 0, 10, 10)),
            collection,
            ViewParameters(Matrices.Identity, 10, 10, CompareFunction.Always),
            requireReplaySafe = true,
        )
        ResourceLifetime.build {
            try {
                val device = shaderDevice()
                val processor = GeometryElementPassProcessor(ShaderRealizations(device, this))
                val command = processor.prepare(submission.geometryElement, submission, pass, PassPreparation(device, this)).single()
                assertEquals(viewport, command.viewport)
                assertEquals(clip, command.scissor)
                assertEquals(7u.toUByte(), command.stencilReference)
                val bytes = java.nio.ByteBuffer.allocate(80).order(java.nio.ByteOrder.nativeOrder())
                command.pushConstants.single().copyTo(bytes)
                assertEquals(1f, bytes.getFloat(0))
                assertEquals(1f, bytes.getFloat(60))
                assertEquals(.5f, bytes.getFloat(64))
                assertEquals(4f, bytes.getFloat(76))
            } finally {
                close()
            }
        }
    }

    private class BentInput(
        val geometry: VertexGeometry,
        val amplitude: Float,
    )

    @Test
    fun typedBindingRetainsReusableVertexGeometryAndShapeParametersAcrossShaders() {
        val vertices = view(36, BufferUsage.Vertex)
        val layout = GeometryLayout(listOf(VertexStreamLayout(12, listOf(GeometryAttribute(VertexSemantics.Position, VertexFormat.Float32x3, 0)))))
        val geometry = VertexGeometry(layout, listOf(VertexStreamSource.Resident(vertices)), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState())
        fun definition(): MeshShader<BentInput> = MeshShader(
            modules(),
            VertexInterface(listOf(AttributeInput(VertexSemantics.Position, 0, VertexFormat.Float32x3))),
            pushes = listOf(
                PushConstantInterface(
                    setOf(ShaderStage.Vertex),
                    0,
                    4,
                    listOf(PushConstantField(heckerpowered.render.engine.material.parameter.ParameterName("amplitude"), 0, 4)),
                )
            ),
            label = "same label",
            encode = { input ->
                require(input.amplitude.isFinite())
                GeometryInput(
                    input.geometry,
                    ParameterValues().replacing(
                        "amplitude",
                        heckerpowered.render.engine.material.parameter.NumericParameterValue.floats(input.amplitude)
                    ),
                )
            },
        )

        val firstShader = definition()
        val secondShader = definition()
        val first = firstShader.bind(BentInput(geometry, .5f))
        val second = secondShader.bind(BentInput(geometry, 1.25f))
        assertSame(geometry, first.geometry)
        assertSame(geometry, second.geometry)
        assertSame(firstShader, first.shading.shader)
        assertSame(secondShader, second.shading.shader)
        assertSame(first.shading, first.copy(composition = CompositingMode.Add).shading)
        assertFailsWith<IllegalArgumentException> { firstShader.bind(BentInput(geometry, Float.NaN)) }
        val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) { geometry(first); geometry(second) }
        val pass = RasterPass(
            RenderPassDescription("bindings", renderArea = RenderArea(0, 0, 1, 1)),
            collection,
            ViewParameters(Matrices.Identity, 1, 1, CompareFunction.Always),
        )
        ResourceLifetime.build {
            try {
                val device = shaderDevice()
                val processor = GeometryElementPassProcessor(ShaderRealizations(device, this))
                val preparation = PassPreparation(device, this)
                val commands = collection.submissions.map { processor.prepare(it.geometryElement, it, pass, preparation).single() }
                assertNotSame(commands[0].pipeline.shaders, commands[1].pipeline.shaders)
                commands.forEach { assertSame(vertices, it.vertexBuffers[0]); assertSame(geometry.range, it.arguments) }
                for ([command, amplitude] in commands.zip(listOf(.5f, 1.25f))) {
                    val bytes = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.nativeOrder())
                    command.pushConstants.single().copyTo(bytes)
                    assertEquals(amplitude, bytes.getFloat(0))
                }
                assertTrue(preparation.snapshot().isEmpty())
            } finally {
                close()
            }
        }
    }

    @Test
    fun actualVertexAndParameterContractsRejectMalformedBindingsWithoutProtocolLabels() {
        val vertices = view(36, BufferUsage.Vertex)
        val layouts = listOf(
            GeometryLayout(listOf(VertexStreamLayout(12, listOf(GeometryAttribute(VertexSemantics.UV, VertexFormat.Float32x3, 0))))),
            GeometryLayout(listOf(VertexStreamLayout(12, listOf(GeometryAttribute(VertexSemantics.Position, VertexFormat.Float32x2, 0))))),
        )
        val vertexShader = MeshShader<VertexGeometry>(
            modules(),
            VertexInterface(listOf(AttributeInput(VertexSemantics.Position, 0, VertexFormat.Float32x3))),
            label = "attributes",
            encode = { GeometryInput(it) },
        )
        val generated = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState())
        val valueName = heckerpowered.render.engine.material.parameter.ParameterName("value")
        val parameterShader = MeshShader<GeometryInput>(
            modules(),
            VertexInterface(emptyList()),
            pushes = listOf(PushConstantInterface(setOf(ShaderStage.Fragment), 0, 4, listOf(PushConstantField(valueName, 0, 4)))),
            label = "parameters",
            encode = { it },
        )
        val missing = parameterShader.bind(GeometryInput(generated))
        val wrongSize = parameterShader.bind(
            GeometryInput(
                generated,
                ParameterValues().replacing(
                    "value",
                    heckerpowered.render.engine.material.parameter.NumericParameterValue.floats(1f, 2f)
                ),
            )
        )
        val duplicates = parameterShader.bind(
            GeometryInput(
                ShaderGeometry(
                    ParameterValues().replacing(
                        "value",
                        heckerpowered.render.engine.material.parameter.NumericParameterValue.floats(1f)
                    ),
                    generated.selection,
                    generated.primitive,
                ),
                ParameterValues().replacing("value", heckerpowered.render.engine.material.parameter.NumericParameterValue.floats(2f))
            )
        )
        val inputs = ViewParameters(Matrices.Identity, 1, 1, CompareFunction.Always)
        val description = RenderPassDescription("invalid", renderArea = RenderArea(0, 0, 1, 1))
        ResourceLifetime.build {
            try {
                val forbiddenDevice = proxy<GraphicsDevice> { error("Invalid CPU input must fail before device access") }
                val processor = GeometryElementPassProcessor(ShaderRealizations(forbiddenDevice, this))
                val malformed = layouts.map { vertexShader.bind(VertexGeometry(it, listOf(VertexStreamSource.Resident(vertices)), generated.selection, generated.primitive)) } + listOf(missing, duplicates)
                for (element in malformed) {
                    val submission = RenderSubmission(element, ObjectSubmitContext(AffineTransforms.Identity))
                    val pass = RasterPass(description, RenderSubmissionList(listOf(submission)), inputs)
                    assertFailsWith<IllegalArgumentException> { processor.prepare(element, submission, pass, PassPreparation(forbiddenDevice, this)) }
                }
                val device = shaderDevice()
                val withResources = GeometryElementPassProcessor(ShaderRealizations(device, this))
                val submission = RenderSubmission(wrongSize, ObjectSubmitContext(AffineTransforms.Identity))
                val pass = RasterPass(description, RenderSubmissionList(listOf(submission)), inputs)
                assertFailsWith<IllegalArgumentException> { withResources.prepare(wrongSize, submission, pass, PassPreparation(device, this)) }
            } finally {
                close()
            }
        }
    }

    @Test
    fun screenPassUsesTheBoundGeneratedSelectionWithoutImposingAFullscreenRecipe() {
        val shader = MeshShader<ShaderGeometry>(modules(), VertexInterface(emptyList()), label = "generated", encode = { GeometryInput(it) })
        val primitive = PrimitiveState(PrimitiveTopology.TriangleList)
        val index = IndexSource.Resident(IndexSelection(view(8, BufferUsage.Index), IndexFormat.Uint16))
        val geometries = listOf(
            ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3, first = 4, instances = 2, firstInstance = 3)), primitive),
            ShaderGeometry(ParameterValues(), GeometrySelection.Indexed(index, DrawRange.indices(3, first = 1, baseVertex = -7, instances = 2, firstInstance = 3)), primitive),
            ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(0)), primitive),
        )
        ResourceLifetime.build {
            try {
                val device = shaderDevice()
                val processor = GeometryElementPassProcessor(ShaderRealizations(device, this))
                for (geometry in geometries) {
                    val element = shader.bind(geometry)
                    val pass = screenPass(RenderPassDescription("screen", renderArea = RenderArea(0, 0, 1, 1)), element)
                    val submission = pass.collection.submissions.single()
                    assertSame(geometry, submission.geometryElement.geometry)
                    val commands = processor.prepare(submission.geometryElement, submission, pass, PassPreparation(device, this))
                    if (geometry.range.isEmpty) assertTrue(commands.isEmpty())
                    else assertSame(geometry.range, commands.single().arguments)
                }
            } finally {
                close()
            }
        }
    }

    private fun modules(): List<ShaderModuleDescription> = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map {
        ShaderModuleDescription(it, ShaderSource(ShaderLanguage.Glsl, "CPU preparation fixture", "test"))
    }

    private fun shaderDevice(): GraphicsDevice = proxy { operation ->
        when (operation) {
            "createShaderModule" -> owned<ShaderModule>()
            "createShaderStages" -> owned<ShaderStages>()
            "createPipelineLayout" -> owned<PipelineLayout>()
            else -> error("Unexpected shader operation: $operation")
        }
    }

    private inline fun <reified T> owned(): T = proxy { operation ->
        when (operation) {
            "close" -> terminateOnFailure { Unit }
            else -> error("Unexpected resource operation: $operation")
        }
    }

    private fun view(size: Long, usage: BufferUsage) = GpuBufferView(
        proxy<GpuBuffer> { name ->
            when (name) {
                "getSizeBytes" -> size
                "getUsage" -> setOf(usage)
                else -> error("Unexpected buffer access: $name")
            }
        },
        0,
        size,
    )

    private inline fun <reified T> proxy(crossinline invoke: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> invoke(method.name.substringBefore('-')) } as T
}
