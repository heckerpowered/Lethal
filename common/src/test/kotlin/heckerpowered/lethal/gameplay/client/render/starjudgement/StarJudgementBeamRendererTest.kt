/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render.starjudgement

import heckerpowered.math.*
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.command.pass.*
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.geometry.*
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.engine.material.*
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.scene.drawing.WorldDrawing
import heckerpowered.render.engine.shader.program.FragmentOutput
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.pipeline.color.ColorWriteMask
import heckerpowered.render.pipeline.color.BlendState
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.resource.buffer.*
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.shader.binding.DescriptorSet
import heckerpowered.render.shader.binding.DescriptorResource
import heckerpowered.render.shader.reflection.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.test.*

/** Tests record actual engine parameter encoding using a fixed shader-interface fixture, without a GPU. */
class StarJudgementBeamRendererTest {
    private val texture = TextureParameterValue(referenceTexture(), referenceProxy<GpuSampler> { operation, _ -> error(operation) })

    private fun collectBeam(origin: VectorView, animationTimeTicks: Double): RenderSubmissionList =
        WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            submit(StarJudgementBeamRenderer(texture), StarJudgementBeamDraw(origin, animationTimeTicks))
        }

    @Test
    fun encodedEndpointsExactlyMatchLegacyAnimationAtNearAndDistantOrigins() = withRecordingEngine {
        val origins = listOf(Vector(0.0, 0.0, 0.0), Vector(18.125, 63.75, -49.0625), Vector(1_000_000_000.125, -48.0, -1_000_000_000.5))
        for (origin in origins) for (time in listOf(-19.5, 0.0, 15.25, 80.75, 1_048_576.375)) {
            record(collectBeam(origin, time))
            assertLegacyEndpoints(pushes.takeLast(2), origin, time)
        }
        assertEquals(1, createdBuffers)
        assertEquals(1, uploads)
    }

    @Test
    fun layersAndRendererInstancesShareFixedTopologyAndKeepCoreThenShellOrder() {
        val list = collectBeam(Vector(17.0, 61.0, -38.0), 12.5)
        assertEquals(2, list.submissions.size)
        val geometries = list.submissions.map { (it.element as GeometryElement).geometry as VertexGeometry }
        val stream = geometries.first().streams.single() as VertexStreamSource.Static
        assertEquals(96, stream.bytes.sizeBytes)
        val packed = ByteBuffer.allocate(96).order(ByteOrder.nativeOrder()).also(stream.bytes::copyTo)
        assertContentEquals(FloatArray(24) { it.toFloat() }, (packed.flip() as ByteBuffer).asFloatBuffer().let { floats -> FloatArray(24).also(floats::get) })
        geometries.forEachIndexed { layer, geometry ->
            assertSame(geometries.first(), geometry)
            assertSame(stream, geometry.streams.single())
            assertEquals(VertexRange(24), geometry.selection.range)
            assertEquals(PrimitiveTopology.TriangleList, geometry.primitive.topology)
            val element = list.submissions[layer].element as GeometryElement
            assertEquals(layer == 0, element.depthWrite)
            assertSame(if (layer == 0) CompositingMode.Replace else BeamShellComposition, element.composition)
            assertEquals(Vectors.Zero, list.submissions[layer].objectState.localToWorld.translation)
        }
        val another = (collectBeam(Vectors.Zero, 90.0).submissions.first().element as GeometryElement).geometry
        assertSame(geometries.first(), another)
    }

    @Test
    fun rendererRetainsCollectorPlacementAndLeavesEndpointsInLocalCoordinates() = withRecordingEngine {
        val origin = Vector(17.0, 61.0, -38.0)
        val placement = AffineTransform(Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ, Vector(1_000.0, 80.0, -700.0))
        val list = WorldDrawing.collect(ObjectSubmitContext(placement)) {
            submit(StarJudgementBeamRenderer(texture), StarJudgementBeamDraw(origin, 12.5))
        }
        assertEquals(placement.translation, list.submissions.first().objectState.localToWorld.translation)
        record(list)
        assertLegacyEndpoints(pushes.takeLast(2), origin, 12.5)
        assertEquals(listOf(1_000F, 80F, -700F), pushes.last().slice(12..14))
    }

    @Test
    fun laterCapturesAndStagesDoNotOverwritePreviouslyCollectedEndpoints() = withRecordingEngine {
        val originA = Vector(18.125, 63.75, -49.0625)
        val originB = Vector(-44.0, 80.0, 50.0)
        val capturedA = collectBeam(originA, 15.25)
        val capturedB = collectBeam(originB, 80.75)
        record(capturedA, capturedB)
        assertLegacyEndpoints(pushes.takeLast(4).take(2), originA, 15.25)
        assertLegacyEndpoints(pushes.takeLast(2), originB, 80.75)
        record(capturedA)
        assertLegacyEndpoints(pushes.takeLast(2), originA, 15.25)
        assertEquals(1, createdBuffers)
        assertEquals(1, uploads)
    }

    @Test
    fun enginePipelinesPreserveEachAnaglyphEyeAndTheOriginalLayerBlends() = withRecordingEngine {
        for (mask in listOf(ColorWriteMask(false, true, true, false), ColorWriteMask(true, false, false, false), ColorWriteMask.All, ColorWriteMask.None)) {
            val collection = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
                submit(StarJudgementBeamRenderer(texture, mask), StarJudgementBeamDraw(Vectors.Zero, 12.5))
            }
            record(collection)
            val [core, shell] = pipelines.takeLast(2)
            assertEquals(mask, core.colorTargets.single().writeMask)
            assertEquals(mask, shell.colorTargets.single().writeMask)
            assertNull(core.colorTargets.single().blend)
            assertEquals(BlendState.StraightAlpha, shell.colorTargets.single().blend)
            assertTrue(requireNotNull(requireNotNull(core.depthStencil).depth).writeEnabled)
            assertFalse(requireNotNull(requireNotNull(shell.depthStencil).depth).writeEnabled)
        }
        assertEquals(1, createdBuffers)
        assertEquals(1, uploads)
    }

    @Test
    fun mappingUsesOriginalNamesAndReflectedAddressesRegardlessOfMemberOrder() = withRecordingEngine(reflection = { stage ->
        val original = beamInterface(stage)
        val inputs = if (stage == ShaderStage.Vertex) original.inputs.map { it.copy(location = 5) } else original.inputs
        val resources = original.resources.map { resource ->
            ShaderInterfaceResource(resource.kind, resource.name,
                if (resource.kind == ShaderInterfaceResourceKind.CombinedTextureSampler) 2 else resource.set,
                if (resource.kind == ShaderInterfaceResourceKind.CombinedTextureSampler) 7 else resource.binding,
                resource.arrayDimensions, resource.sizeBytes, resource.blockName, resource.members.reversed(), resource.image)
        }
        ShaderInterfaceDescription(inputs, original.outputs, resources)
    }) {
        record(collectBeam(Vectors.Zero, 12.5))
        assertTrue(pipelines.all { it.vertex.buffers.single().attributes.single().location == 5 })
        assertEquals(listOf(0, 1, 2, 0, 1, 2), descriptors.map { it.first })
        for ((index, set) in descriptors) {
            if (index != 2) {
                assertTrue(set.layout.bindings.isEmpty())
                continue
            }
            assertEquals(7, set.layout.bindings.single().binding)
            val sampled = set.bindings.single().resources.single() as DescriptorResource.CombinedTextureSampler
            assertSame(texture.view, sampled.view)
            assertSame(texture.sampler, sampled.sampler)
        }
        assertLegacyEndpoints(pushes, Vectors.Zero, 12.5)
    }

    @Test
    fun mappingRejectsSameSizedPushBytesWithDifferentMeaningsOrShapesInEitherStage() {
        val members = beamInterface(ShaderStage.Vertex).resources.single().members
        val invalid = listOf(
            members.map { if (it.name == "cornersA") it.copy(type = ShaderValueDescription(ShaderScalarKind.SignedInteger, 32, 4, 1)) else it },
            members.map { if (it.name == "cornersA") it.copy(offsetBytes = 112) else if (it.name == "color") it.copy(offsetBytes = 64) else it },
            members.map { if (it.name == "clipFromLocal") it.copy(rowMajor = true) else it },
            members.map { if (it.name == "clipFromLocal") it.copy(matrixStrideBytes = 32) else it },
            members.map { if (it.name == "heightAndV") it.copy(type = ShaderValueDescription(ShaderScalarKind.Float, 32, 4, 1, listOf(1)), arrayStrideBytes = 16) else it },
            members.filterNot { it.name == "cornersB" },
        )
        for (stage in listOf(ShaderStage.Vertex, ShaderStage.Fragment)) for (changed in invalid) {
            withRecordingEngine(reflection = { selected ->
                val original = beamInterface(selected)
                if (selected != stage) original else original.withPushMembers(changed)
            }) {
                assertFailsWith<IllegalArgumentException> { StarJudgementBeamRenderer(texture).prepare(engine) }
            }
        }
    }

    @Test
    fun mappingRequiresNamedVertexAndImageInputsAndTheirActualFloatShapes() {
        val vertex = beamInterface(ShaderStage.Vertex)
        for (input in listOf(
            vertex.inputs.single().copy(name = "unrelated"),
            vertex.inputs.single().copy(type = ShaderValueDescription(ShaderScalarKind.Float, 32, 2, 1)),
            vertex.inputs.single().copy(type = ShaderValueDescription(ShaderScalarKind.SignedInteger, 32, 1, 1)),
        )) {
            withRecordingEngine(reflection = { stage ->
                if (stage == ShaderStage.Vertex) ShaderInterfaceDescription(listOf(input), vertex.outputs, vertex.resources) else beamInterface(stage)
            }) {
                assertFailsWith<IllegalArgumentException> { StarJudgementBeamRenderer(texture).prepare(engine) }
            }
        }
        val fragment = beamInterface(ShaderStage.Fragment)
        val image = fragment.resources.single { it.kind == ShaderInterfaceResourceKind.CombinedTextureSampler }
        val shape = requireNotNull(image.image)
        val invalidImages = listOf(
            ShaderImageDescription(ShaderImageDimension.Cube, false, false, false, shape.sampledType),
            shape.copy(depth = true), shape.copy(arrayed = true), shape.copy(multisampled = true),
            shape.copy(sampledType = ShaderValueDescription(ShaderScalarKind.UnsignedInteger, 32, 1, 1)),
        )
        for ((name, dimensions, changed) in listOf(Triple("unrelated", emptyList<Int?>(), shape), Triple("image", listOf<Int?>(2), shape)) +
                invalidImages.map { Triple("image", emptyList<Int?>(), it) }) {
            val resource = ShaderInterfaceResource(image.kind, name, image.set, image.binding, dimensions, image.sizeBytes, image.blockName, image.members, changed)
            withRecordingEngine(reflection = { stage ->
                if (stage == ShaderStage.Vertex) vertex else ShaderInterfaceDescription(fragment.inputs, fragment.outputs, fragment.resources.map { if (it === image) resource else it })
            }) {
                assertFailsWith<IllegalArgumentException> { StarJudgementBeamRenderer(texture).prepare(engine) }
            }
        }
    }

    @Test
    fun coverageOutputRequiresFourFloatComponentsAtLocationZero() {
        val fragment = beamInterface(ShaderStage.Fragment)
        for (output in listOf(fragment.outputs.single().copy(location = 1), fragment.outputs.single().copy(type = ShaderValueDescription(ShaderScalarKind.Float, 32, 3, 1)),
                fragment.outputs.single().copy(type = ShaderValueDescription(ShaderScalarKind.SignedInteger, 32, 4, 1)))) {
            withRecordingEngine(reflection = { stage ->
                if (stage == ShaderStage.Vertex) beamInterface(stage) else ShaderInterfaceDescription(fragment.inputs, listOf(output), fragment.resources)
            }) {
                assertFailsWith<IllegalArgumentException> { StarJudgementBeamRenderer(texture).prepare(engine) }
            }
        }
    }

    @Test
    fun rendererRejectsRelabelingSignalOrPremultipliedContentsAsItsStraightTexture() {
        assertFailsWith<IllegalArgumentException> { StarJudgementBeamRenderer(texture.copy(representation = AlphaRepresentation.Premultiplied)) }
        assertFailsWith<IllegalArgumentException> { StarJudgementBeamRenderer(texture.copy(alphaQuantity = AlphaQuantity.Signal)) }
    }

    @Test
    fun hostShellBlendRetainsLegacyEquationWithoutInventingDestinationCoverage() {
        val output = FragmentOutput(AlphaRepresentation.Straight, AlphaQuantity.Coverage)
        for (destination in listOf(null, AlphaQuantity.Signal, AlphaQuantity.Coverage)) {
            val state = BeamShellComposition.target(TextureFormat.Rgba8UnsignedNormalized, output, destination)
            assertEquals(BlendState.StraightAlpha, state.blend)
            assertFalse(BeamShellComposition.preservesCoverage(output, state))
        }
    }

    @Test
    fun shellRejectsAnIncompatibleSampledAlphaContract() {
        for (output in listOf(null, FragmentOutput(AlphaRepresentation.Premultiplied, AlphaQuantity.Coverage), FragmentOutput(AlphaRepresentation.Straight, AlphaQuantity.Signal))) {
            assertFailsWith<IllegalArgumentException> { BeamShellComposition.target(TextureFormat.Rgba8UnsignedNormalized, output, null) }
        }
    }
}

private fun assertLegacyEndpoints(pushes: List<FloatArray>, origin: VectorView, time: Double) {
    val scroll = -time * 0.2 - floor(-time * 0.1)
    val expected = legacyVertices(origin, time, -1.0 + (scroll - floor(scroll)))
    assertEquals(2, pushes.size)
    val cornersBySide = listOf(0 to 1, 3 to 2, 1 to 3, 2 to 0)
    for ((layer, push) in pushes.withIndex()) {
        assertEquals(32, push.size)
        assertEquals(listOf(1F, 1F, 1F, if (layer == 0) 1F else 0.125F), push.takeLast(4))
        for ((side, corners) in cornersBySide.withIndex()) for (stripVertex in 0..3) {
            val first = stripVertex % 2 == 0
            val upper = stripVertex >= 2
            val corner = if (first) corners.first else corners.second
            val actual = floatArrayOf(push[16 + corner * 2], push[24 + if (upper) 1 else 0], push[17 + corner * 2], if (first) 1F else 0F, push[26 + if (upper) 1 else 0])
            val offset = (layer * 16 + side * 4 + stripVertex) * 5
            assertContentEquals(expected.sliceArray(offset until offset + 5).map(Float::toRawBits), actual.map(Float::toRawBits), "origin=$origin time=$time layer=$layer side=$side vertex=$stripVertex")
        }
    }
}

private class RecordingEngine(private val reflection: (ShaderStage) -> ShaderInterfaceDescription = ::beamInterface) {
    val pushes = mutableListOf<FloatArray>()
    val pipelines = mutableListOf<RenderPipelineDescription>()
    val descriptors = mutableListOf<Pair<Int, DescriptorSet>>()
    var createdBuffers = 0
    var uploads = 0
    private val stack = MemoryStack(256)
    private val pass: RenderPass by lazy { referenceProxy<RenderPass> { operation, arguments ->
        when (operation) {
            "withViewport", "withScissor" -> {
                @Suppress("UNCHECKED_CAST")
                val body = arguments[1] as RenderPass.() -> Any?
                body(pass)
            }
            "getMemoryStack" -> stack
            "bindPipeline" -> { pipelines += arguments[0] as RenderPipelineDescription; Unit }
            "bindDescriptorSet" -> { descriptors += (arguments[0] as Int) to (arguments[1] as DescriptorSet); Unit }
            "bindVertexBuffer", "draw", "setStencilReference" -> Unit
            "pushConstants" -> {
                val values = (arguments[1] as ByteBuffer).duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
                pushes += FloatArray(values.remaining()).also(values::get)
                Unit
            }
            else -> error(operation)
        }
    }
    }
    private val encoder = referenceProxy<CommandEncoder> { operation, arguments ->
        when (operation) {
            "getMemoryStack" -> stack
            "writeBuffer" -> { uploads++; Unit }
            "renderPass" -> {
                @Suppress("UNCHECKED_CAST")
                val body = arguments.last() as RenderPass.() -> Unit
                body(pass)
            }
            else -> error(operation)
        }
    }
    private val device = referenceProxy<GraphicsDevice> { operation, arguments ->
        when (operation) {
            "compileCanonicalShader" -> {
                val description = arguments[0] as ShaderModuleDescription
                ShaderCompilation(description, reflection(description.stage))
            }
            "createSampler" -> referenceProxy<GpuSampler> { call, _ -> check(call == "close"); terminateOnFailure {} }
            "createShaderModule" -> {
                val description = arguments[0] as ShaderModuleDescription
                referenceProxy<ShaderModule> { call, _ -> when (call) {
                    "getStage" -> description.stage
                    "getEntryPoint" -> description.entryPoint
                    "close" -> terminateOnFailure {}
                    else -> error(call)
                } }
            }
            "createShaderStages" -> referenceProxy<ShaderStages> { call, _ -> check(call == "close"); terminateOnFailure {} }
            "createPipelineLayout" -> referenceProxy<PipelineLayout> { call, _ -> check(call == "close"); terminateOnFailure {} }
            "createBuffer" -> {
                val description = arguments[0] as BufferDescription
                createdBuffers++
                referenceProxy<GpuBuffer> { call, _ -> when (call) {
                    "getSizeBytes" -> description.sizeBytes
                    "getUsage" -> description.usage
                    "close" -> terminateOnFailure {}
                    else -> error(call)
                } }
            }
            "encode" -> {
                @Suppress("UNCHECKED_CAST")
                val body = arguments.last() as CommandEncoder.() -> Unit
                body(encoder)
            }
            "awaitIdle" -> Unit
            else -> error(operation)
        }
    }
    val engine = RenderEngine.create(device)
    private val attachment = referenceProxy<RenderAttachment> { operation, _ -> when (operation) {
        "getFormat" -> TextureFormat.Rgba8UnsignedNormalized
        "getAspects" -> setOf(TextureAspect.Color)
        "getSampleCount" -> SampleCount.One
        "getWidth", "getHeight", "getArrayLayerCount" -> 1
        else -> error(operation)
    } }

    private val depthAttachment = referenceProxy<RenderAttachment> { operation, _ -> when (operation) {
        "getFormat" -> TextureFormat.Depth32Float
        "getAspects" -> setOf(TextureAspect.Depth)
        "getSampleCount" -> SampleCount.One
        "getWidth", "getHeight", "getArrayLayerCount" -> 1
        else -> error(operation)
    } }

    fun record(vararg collections: RenderSubmissionList) = engine.stage {
        collections.forEach { collection ->
            rasterPass(RasterPass(RenderPassDescription("beam reference", renderArea = RenderArea(0, 0, 1, 1), colorAttachments = listOf(RenderPassAttachment<heckerpowered.render.color.Color>(attachment)), depthAttachment = RenderPassAttachment<Float>(depthAttachment)), collection, ViewParameters(Matrices.Identity, 1, 1, CompareFunction.LessOrEqual), requireReplaySafe = true))
        }
    }
}

private fun withRecordingEngine(reflection: (ShaderStage) -> ShaderInterfaceDescription = ::beamInterface, block: RecordingEngine.() -> Unit) {
    val recording = RecordingEngine(reflection)
    try { recording.block() } finally { recording.engine.close() }
}

private fun ShaderInterfaceDescription.withPushMembers(members: List<ShaderInterfaceBlockMember>): ShaderInterfaceDescription =
    ShaderInterfaceDescription(inputs, outputs, resources.map { resource ->
        if (resource.kind != ShaderInterfaceResourceKind.PushConstant) resource else
            ShaderInterfaceResource(resource.kind, resource.name, resource.set, resource.binding, resource.arrayDimensions, resource.sizeBytes, resource.blockName, members, resource.image)
    })

private fun beamInterface(stage: ShaderStage): ShaderInterfaceDescription {
    fun vector(components: Int) = ShaderValueDescription(ShaderScalarKind.Float, 32, components, 1)
    val members = listOf(
        ShaderInterfaceBlockMember("clipFromLocal", 0, 64, ShaderValueDescription(ShaderScalarKind.Float, 32, 4, 4), 16, 0, false),
        ShaderInterfaceBlockMember("cornersA", 64, 16, vector(4), 0, 0, false),
        ShaderInterfaceBlockMember("cornersB", 80, 16, vector(4), 0, 0, false),
        ShaderInterfaceBlockMember("heightAndV", 96, 16, vector(4), 0, 0, false),
        ShaderInterfaceBlockMember("color", 112, 16, vector(4), 0, 0, false),
    )
    val block = ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "beam", null, null, emptyList(), 128, "Beam", members, null)
    val coordinates = ShaderInterfaceVariable("coordinates", 0, vector(2))
    if (stage == ShaderStage.Vertex) return ShaderInterfaceDescription(listOf(ShaderInterfaceVariable("vertexNumber", 0, vector(1))), listOf(coordinates), listOf(block))
    val sampler = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 0, emptyList(), null, null, emptyList(), ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, vector(1)))
    return ShaderInterfaceDescription(listOf(coordinates), listOf(ShaderInterfaceVariable("result", 0, vector(4))), listOf(block, sampler))
}

private fun referenceTexture(): GpuTextureView {
    val storage = referenceProxy<GpuTexture> { operation, _ -> when (operation) {
        "getFormat" -> TextureFormat.Rgba8UnsignedNormalized
        "getDimension" -> TextureDimension.TwoDimensional
        "getSampleCount" -> SampleCount.One
        "getStorage" -> TextureStorage.Backed
        "getUsage" -> setOf(TextureUsage.Sampled)
        "getWidth", "getHeight", "getDepth", "getMipLevelCount", "getArrayLayerCount" -> 1
        "getCubeCompatible" -> false
        else -> error(operation)
    } }
    return referenceProxy { operation, _ -> when (operation) {
        "getTexture" -> storage
        "getFormat" -> TextureFormat.Rgba8UnsignedNormalized
        "getDimension" -> TextureViewDimension.TwoDimensional
        "getBaseMipLevel", "getBaseArrayLayer" -> 0
        "getMipLevelCount", "getArrayLayerCount", "getWidth", "getHeight", "getDepth" -> 1
        "getAspects" -> setOf(TextureAspect.Color)
        else -> error(operation)
    } }
}

private inline fun <reified T> referenceProxy(crossinline body: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, arguments ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === arguments?.get(0)
            else -> body(method.name.substringBefore('-'), arguments ?: emptyArray())
        }
    } as T

// Frozen pre-migration geometry is an independent regression oracle, including Float rounding.
private fun legacyVertices(origin: VectorView, animationTimeTicks: Double, startV: Double): FloatArray {
    val vertices = FloatArray(8 * 4 * 5)
    var offset = 0
    fun vertex(corner: BeamCorner, height: Double, u: Float, v: Double) {
        vertices[offset++] = (origin.x + corner.x).toFloat()
        vertices[offset++] = (origin.y + height).toFloat()
        vertices[offset++] = (origin.z + corner.z).toFloat()
        vertices[offset++] = u
        vertices[offset++] = v.toFloat()
    }
    fun sides(corners: List<BeamCorner>, endV: Double) {
        fun face(first: BeamCorner, second: BeamCorner) {
            vertex(first, BEAM_START_OFFSET, 1F, startV)
            vertex(second, BEAM_START_OFFSET, 0F, startV)
            vertex(first, BEAM_START_OFFSET + BEAM_HEIGHT, 1F, endV)
            vertex(second, BEAM_START_OFFSET + BEAM_HEIGHT, 0F, endV)
        }
        val [first, second, third, fourth] = corners
        face(first, second)
        face(fourth, third)
        face(second, fourth)
        face(third, first)
    }
    sides(rotatingCorners(animationTimeTicks), startV + BEAM_HEIGHT * 0.5 / INNER_RADIUS)
    sides(GlowCorners, startV + BEAM_HEIGHT)
    return vertices
}

private const val BEAM_START_OFFSET = -512.0
private const val BEAM_HEIGHT = 1024.0
private const val INNER_RADIUS = 0.2
private const val GLOW_RADIUS = 0.25
private const val GLOW_ALPHA = 0.125F

private fun rotatingCorners(animationTimeTicks: Double): List<BeamCorner> {
    val rotation = animationTimeTicks * -0.0375
    fun corner(angleOffset: Double): BeamCorner {
        val angle = rotation + angleOffset
        return BeamCorner(0.5 + cos(angle) * INNER_RADIUS, 0.5 + sin(angle) * INNER_RADIUS)
    }
    return listOf(corner(Math.PI * 0.75), corner(Math.PI * 0.25), corner(Math.PI * 1.25), corner(Math.PI * 1.75))
}

private val GlowCorners = listOf(BeamCorner(0.5 - GLOW_RADIUS, 0.5 - GLOW_RADIUS), BeamCorner(0.5 + GLOW_RADIUS, 0.5 - GLOW_RADIUS), BeamCorner(0.5 - GLOW_RADIUS, 0.5 + GLOW_RADIUS), BeamCorner(0.5 + GLOW_RADIUS, 0.5 + GLOW_RADIUS))

private data class BeamCorner(val x: Double, val z: Double)
