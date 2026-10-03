/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.postprocessing

import heckerpowered.render.engine.shader.program.fixtureScreenPreparation
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.color.Color
import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.pass.*
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.image.ImageSize
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.color.*
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.ColorContent
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.shader.binding.DescriptorResource
import heckerpowered.render.shader.binding.DescriptorSet
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

/** Exercises the real stage processor and encoding callbacks; the recorder performs no GPU work. */
class BloomRenderingTest {
    @Test
    fun imageSizesRetainDistinctAllocationsAndEncodedReconstructionOrder() {
        for ([width, height, count] in listOf(
            Triple(1, 1, 1),
            Triple(2, 2, 1),
            Triple(4, 4, 2),
            Triple(1, 7, 3),
            Triple(7, 1, 3),
            Triple(3, 5, 3),
            Triple(8, 8, 3),
            Triple(16, 9, 4)
        )) {
            val fixture = Fixture()
            try {
                val source = fixture.engine.images.image("source", ImageSize(width, height), TextureFormat.Rgba16Float)
                fixture.engine.stage {
                    val result = fixture.bloom.render(this, source, .625f, "test")
                    assertEquals(source.size, result.size)
                }
                val sizes = generateSequence(width to height) { [w, h] -> maxOf(1, w / 2) to maxOf(1, h / 2) }.take(count).toList()
                val allocationNames = listOf("down", "up").flatMap { direction -> (0 until count).map { "test.node.0.$direction.$it" } }
                assertEquals(listOf("source") + allocationNames, fixture.allocations.map { it.label })
                assertEquals(sizes + sizes, fixture.allocations.drop(1).map { it.width to it.height })
                assertTrue(fixture.allocations.all { it.format == TextureFormat.Rgba16Float })
                val allocatedTextures = fixture.allocations.map { fixture.textures.getValue(it.label) }
                for (index in allocatedTextures.indices) {
                    for (previous in 0 until index) assertNotSame(allocatedTextures[previous], allocatedTextures[index])
                }
                val expected = mutableListOf(Triple("brightness", "source", "test.node.0.down.0"))
                for (index in 1 until count) expected += Triple("tent", "test.node.0.down.${index - 1}", "test.node.0.down.$index")
                expected += Triple("copy", "test.node.0.down.${count - 1}", "test.node.0.up.${count - 1}")
                for (index in count - 2 downTo 0) {
                    expected += Triple("copy", "test.node.0.up.${index + 1}", "test.node.0.up.$index")
                    expected += Triple("tent", "test.node.0.down.$index", "test.node.0.up.$index")
                }
                assertEquals(3 * count - 1, fixture.passes.size)
                for ([pass, command] in fixture.passes.zip(expected)) {
                    val pipeline = assertNotNull(pass.pipeline)
                    val sampled = assertNotNull(pass.source)
                    assertEquals(command.first, pipeline.label)
                    assertSame(fixture.textures[command.second], sampled)
                    val dimensions = sizes[command.third.substringAfterLast('.').toInt()]
                    val target = fixture.image(command.third, dimensions, if (command.third.contains(".up.")) AlphaQuantity.Signal else AlphaQuantity.Coverage)
                    assertSame(target.attachment, pass.description.colorAttachments.single()!!.attachment)
                    assertNotSame(sampled, target.view.texture)
                    assertEquals(dimensions, pass.description.renderArea.width to pass.description.renderArea.height)
                    val additive = command.first == "tent" && command.third.contains(".up.")
                    assertEquals(if (additive) BlendState.Additive else null, pipeline.colorTargets.single().blend)
                    val operation = pass.description.colorAttachments.single()!!.operation
                    assertEquals(AttachmentStoreOperation.Store, operation.store)
                    assertEquals(if (additive) AttachmentLoadOperation.Load else AttachmentLoadOperation.Clear(Color(0f, 0f, 0f, 0f)), operation.load)
                    assertTrue(pass.description.colorResolves.isEmpty())
                    val values = when (command.first) {
                        "brightness" -> floatArrayOf(.625f)
                        "tent" -> floatArrayOf(1f / sampled.width, 1f / sampled.height)
                        else -> floatArrayOf()
                    }
                    assertEquals(if (values.isEmpty()) listOf("stencil", "pipeline", "descriptor", "draw")
                        else listOf("stencil", "pipeline", "descriptor", "push", "draw"), pass.events)
                    if (values.isEmpty()) assertTrue(pass.pushes.isEmpty())
                    else assertContentEquals(floatBytes(values), pass.pushes.single())
                }
            } finally { fixture.engine.close() }
            assertEquals(fixture.created, fixture.closed)
        }
    }

    @Test
    fun sameNamesUseSeparateOrdinalsAndReuseImagesAcrossStages() {
        val fixture = Fixture()
        try {
            val source = fixture.image("source", 8 to 8)
            val outputs = mutableListOf<GpuTexture>()
            repeat(2) { frame ->
                val createdBefore = fixture.created
                fixture.engine.stage {
                    repeat(2) { outputs += fixture.bloom.render(this, source, .5f, "repeat").view.texture }
                }
                if (frame == 1) assertEquals(createdBefore, fixture.created)
            }
            assertNotSame(outputs[0], outputs[1])
            assertSame(outputs[0], outputs[2])
            assertSame(outputs[1], outputs[3])
            assertEquals(13, fixture.allocations.size)
            assertEquals(32, fixture.passes.size)
            for ([before, after] in fixture.passes.take(16).zip(fixture.passes.drop(16))) {
                assertSame(before.source, after.source)
                assertSame(before.description.colorAttachments.single()!!.attachment, after.description.colorAttachments.single()!!.attachment)
                assertEquals(before.events, after.events)
            }
        } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun applyEncodesOriginalStoresAndNonemptyResolveWithDestinationAlphaPreserved() {
        val fixture = Fixture()
        try {
            val original = output()
            val source = fixture.image("source", 4 to 4)
            fixture.engine.stage { fixture.bloom.apply(this, source, original, .5f) }
            val last = fixture.passes.last()
            assertSame(original, last.description)
            assertEquals(original.colorResolves, last.description.colorResolves)
            assertEquals(AttachmentStoreOperation.Discard, last.description.colorAttachments.single()!!.operation.store)
            val blend = assertNotNull(last.pipeline).colorTargets.single().blend!!
            assertEquals(BlendComponent(BlendFactor.One, BlendFactor.One), blend.color)
            assertEquals(BlendComponent(BlendFactor.Zero, BlendFactor.One), blend.alpha)
            assertEquals(SampleCount.Four, last.pipeline!!.multisample.sampleCount)
            assertEquals(listOf("stencil", "pipeline", "descriptor", "draw"), last.events)
        } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun contentEncodesInitialLoadThenFinalStoreAndResolveWithoutChangingOutput() {
        val fixture = Fixture()
        try {
            val original = output()
            val attachment = original.colorAttachments.single()!!
            val resolve = original.colorResolves.single()
            val source = fixture.image("source", 4 to 4)
            fixture.engine.stage { fixture.bloom.compositeContent(this, source, original, .5f, ColorContent(AlphaQuantity.Coverage, AlphaRepresentation.Premultiplied)) }
            val first = fixture.passes[fixture.passes.lastIndex - 1]
            val last = fixture.passes.last()
            assertSame(source.view.texture, first.source)
            assertSame(attachment.attachment, first.description.colorAttachments.single()!!.attachment)
            assertSame(attachment.operation.load, first.description.colorAttachments.single()!!.operation.load)
            assertEquals(AttachmentStoreOperation.Store, first.description.colorAttachments.single()!!.operation.store)
            assertTrue(first.description.colorResolves.isEmpty())
            assertEquals(BlendState.PremultipliedAlpha, first.pipeline!!.colorTargets.single().blend)
            assertEquals(AttachmentLoadOperation.Load, last.description.colorAttachments.single()!!.operation.load)
            assertEquals(attachment.operation.store, last.description.colorAttachments.single()!!.operation.store)
            assertSame(resolve, last.description.colorResolves.single())
            assertEquals(BlendState.Additive, last.pipeline!!.colorTargets.single().blend)
            assertSame(attachment, original.colorAttachments.single())
            assertSame(resolve, original.colorResolves.single())
            assertEquals(AttachmentLoadOperation.Clear(Color(.1f, .2f, .3f, .4f)), attachment.operation.load)
            assertEquals(AttachmentStoreOperation.Discard, attachment.operation.store)
            assertEquals(listOf("stencil", "pipeline", "descriptor", "draw"), first.events)
            assertEquals(first.events, last.events)
        } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun invalidThresholdDoesNotAllocate() {
        val fixture = Fixture()
        try {
            val source = fixture.image("source", 1 to 1)
            fixture.engine.stage {
                assertFailsWith<IllegalArgumentException> { fixture.bloom.render(this, source, Float.NaN, "invalid") }
            }
            assertEquals(1, fixture.allocations.size)
            assertTrue(fixture.passes.isEmpty())
        } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun signalSourceIsRejectedByTheBrightnessInputContract() {
        val fixture = Fixture()
        try {
            val source = fixture.image("source", 1 to 1, AlphaQuantity.Signal)
            fixture.engine.stage {
                val failure = assertFailsWith<IllegalArgumentException> { fixture.bloom.render(this, source, .5f, "signal") }
                assertEquals("Brightness extraction requires coverage input", failure.message)
            }
            assertTrue(fixture.passes.isEmpty())
        } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun callerNamespaceCollisionIsExposedForRhiFeedbackValidation() {
        val fixture = Fixture()
        try {
            val source = fixture.image("alias.node.0.down.0", 1 to 1)
            fixture.engine.stage { fixture.bloom.render(this, source, .5f, "alias") }
            val brightness = fixture.passes.first()
            val sampled = brightness.resources.descriptors.flatMap { it.bindings }
                .flatMap { it.resources }.filterIsInstance<DescriptorResource.CombinedTextureSampler>().single()
            assertEquals("bloom brightness", brightness.description.label)
            assertSame(source.view.texture, sampled.view.texture)
            assertSame(source.attachment, brightness.description.colorAttachments.single()!!.attachment)
        } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun signalBloomPreparesAndEncodesTheSamePyramidWithSignalAllocations() {
        val fixture = Fixture()
        try {
            val source = fixture.image("source", 8 to 8, AlphaQuantity.Signal)
            val bloom = Bloom(fixture.engine.images, TextureFormat.Rgba16Float, AlphaQuantity.Signal)
            fixture.engine.stage {
                val result = bloom.render(this, source, .625f, "signal")
                assertEquals(source.size, result.size)
                assertEquals(AlphaQuantity.Signal, result.alphaQuantity)
            }
            assertEquals(listOf("brightness", "tent", "tent", "copy", "copy", "tent", "copy", "tent"), fixture.passes.map { it.pipeline!!.label })
            assertEquals(listOf(8, 4, 2, 2, 4, 4, 8, 8), fixture.passes.map { it.description.renderArea.width })
            assertEquals(7, fixture.allocations.size)
            for ((index, pass) in fixture.passes.withIndex()) {
                val target = assertNotNull(fixture.engine.images.find(pass.description.colorAttachments.single()!!.attachment))
                val sampled = assertNotNull(fixture.engine.images.find(pass.source!!))
                assertEquals(AlphaQuantity.Signal, target.alphaQuantity)
                assertEquals(AlphaQuantity.Signal, sampled.alphaQuantity)
                assertNotSame(target.view.texture, sampled.view.texture)
                val additive = index == 5 || index == 7
                assertEquals(if (additive) BlendState.Additive else null, pass.pipeline!!.colorTargets.single().blend)
                assertEquals(if (additive) AttachmentLoadOperation.Load else AttachmentLoadOperation.Clear(Color(0f, 0f, 0f, 0f)), pass.description.colorAttachments.single()!!.operation.load)
                when (pass.pipeline!!.label) {
                    "brightness" -> assertContentEquals(floatBytes(floatArrayOf(.625f)), pass.pushes.single())
                    "tent" -> assertContentEquals(floatBytes(floatArrayOf(1f / sampled.size.width, 1f / sampled.size.height)), pass.pushes.single())
                    else -> assertTrue(pass.pushes.isEmpty())
                }
            }
            val createdBefore = fixture.created
            fixture.engine.stage { bloom.render(this, source, .625f, "signal") }
            assertEquals(createdBefore, fixture.created)
            for ((first, second) in fixture.passes.take(8).zip(fixture.passes.drop(8))) {
                assertSame(first.source, second.source)
                assertSame(first.description.colorAttachments.single()!!.attachment, second.description.colorAttachments.single()!!.attachment)
            }
        } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun explicitSignalBloomRejectsCoverageAndSourceOverComposition() {
        val fixture = Fixture()
        try {
            val bloom = Bloom(fixture.engine.images, TextureFormat.Rgba16Float, AlphaQuantity.Signal)
            val coverage = fixture.image("coverage", 1 to 1)
            fixture.engine.stage {
                val failure = assertFailsWith<IllegalArgumentException> { bloom.render(this, coverage, .5f, "mismatch") }
                assertEquals("Brightness extraction requires signal input", failure.message)
            }
            val signal = fixture.image("signal", 1 to 1, AlphaQuantity.Signal)
            val allocationsBefore = fixture.allocations.size
            fixture.engine.stage {
                val failure = assertFailsWith<IllegalArgumentException> { bloom.compositeContent(this, signal, output(), .5f) }
                assertEquals("Content composition requires coverage input", failure.message)
            }
            assertEquals(allocationsBefore, fixture.allocations.size)
            assertTrue(fixture.passes.isEmpty())
        } finally { fixture.engine.close() }
        assertEquals(fixture.created, fixture.closed)
    }

    private fun floatBytes(values: FloatArray): ByteArray = ByteBuffer.allocate(values.size * 4).order(ByteOrder.nativeOrder()).apply {
        values.forEach { putFloat(it) }
    }.array()

    private fun output(): RenderPassDescription {
        val color = attachment(SampleCount.Four)
        val resolve = ColorAttachmentResolve(0, ImageRegion.Attachment(attachment(SampleCount.One), width = 4, height = 4))
        return RenderPassDescription(
            "output",
            RenderArea(0, 0, 4, 4),
            listOf(RenderPassAttachment(color,
                AttachmentOperations(AttachmentLoadOperation.Clear(Color(.1f, .2f, .3f, .4f)), AttachmentStoreOperation.Discard))),
            colorResolves = listOf(resolve),
        )
    }

    private fun attachment(samples: SampleCount): RenderAttachment = proxy(RenderAttachment::class.java) { operation, _ ->
        when (operation) {
            "getWidth", "getHeight" -> 4
            "getArrayLayerCount" -> 1
            "getFormat" -> TextureFormat.Rgba16Float
            "getSampleCount" -> samples
            "getAspects" -> setOf(TextureAspect.Color)
            else -> error("Unexpected output attachment access: $operation")
        }
    }

    private class RecordedPass(val description: RenderPassDescription, val resources: RenderPassResources) {
        val events = mutableListOf<String>()
        val pushes = mutableListOf<ByteArray>()
        var pipeline: RenderPipelineDescription? = null
        var source: GpuTexture? = null
    }

    private inner class Fixture {
        val allocations = mutableListOf<TextureDescription>()
        val textures = linkedMapOf<String, GpuTexture>()
        val passes = mutableListOf<RecordedPass>()
        private val encoder: CommandEncoder = proxy(CommandEncoder::class.java) { operation, arguments ->
            check(operation == "renderPass")
            val description = arguments[0] as RenderPassDescription
            val resources = arguments[1] as RenderPassResources
            val recording = RecordedPass(description, resources)
            passes += recording
            @Suppress("UNCHECKED_CAST")
            val commands = arguments.last() as RenderPass.() -> Unit
            commands(recordPass(recording, resources))
        }
        var created = 0
        var closed = 0
        fun <T> owned(type: Class<T>): T {
            created++
            return proxy(type) { operation, _ ->
                check(operation == "close")
                terminateOnFailure { closed++; Unit }
            }
        }
        private val device = proxy(GraphicsDevice::class.java) { operation, arguments ->
            when (operation) {
                "compileCanonicalShader" -> fixtureScreenPreparation(arguments[0] as ShaderModuleDescription, arguments[1] as String)
                "createSampler" -> owned(GpuSampler::class.java)
                "awaitIdle" -> Unit
                "createShaderModule" -> owned(ShaderModule::class.java)
                "createShaderStages" -> owned(ShaderStages::class.java)
                "createPipelineLayout" -> owned(PipelineLayout::class.java)
                "createTexture" -> {
                    val description = arguments[0] as TextureDescription
                    allocations += description
                    created++
                    proxy(GpuTexture::class.java) { property, _ ->
                        when (property) {
                            "getUsage" -> description.usage
                            "getDimension" -> description.dimension
                            "getSampleCount" -> description.sampleCount
                            "getFormat" -> description.format
                            "getWidth" -> description.width
                            "getHeight" -> description.height
                            "getDepth", "getMipLevelCount", "getArrayLayerCount" -> 1
                            "close" -> terminateOnFailure { closed++; Unit }
                            else -> error("Unexpected texture access: $property")
                        }
                    }.also { textures[description.label] = it }
                }
                "createTextureView" -> {
                    val texture = arguments[0] as GpuTexture
                    proxy(GpuTextureView::class.java) { property, _ ->
                        when (property) {
                            "getTexture" -> texture
                            "getDimension" -> TextureViewDimension.TwoDimensional
                            "getFormat" -> texture.format
                            "getWidth" -> texture.width
                            "getHeight" -> texture.height
                            "getArrayLayerCount" -> 1
                            "getBaseMipLevel", "getBaseArrayLayer" -> 0
                            "getMipLevelCount", "getDepth" -> 1
                            "getAspects" -> setOf(TextureAspect.Color)
                            else -> error("Unexpected view access: $property")
                        }
                    }
                }
                "createAttachmentView" -> {
                    val view = arguments[0] as GpuTextureView
                    proxy(RenderAttachment::class.java) { property, _ ->
                        when (property) {
                            "getWidth" -> view.width
                            "getHeight" -> view.height
                            "getArrayLayerCount" -> 1
                            "getFormat" -> view.format
                            "getSampleCount" -> SampleCount.One
                            "getAspects" -> view.aspects
                            else -> error("Unexpected attachment access: $property")
                        }
                    }
                }
                "encode" -> {
                    @Suppress("UNCHECKED_CAST")
                    val commands = arguments.last() as CommandEncoder.() -> Unit
                    commands(encoder)
                }
                else -> error("Unexpected device operation: $operation")
            }
        }
        val engine = RenderEngine.create(device)
        val bloom = Bloom(engine.images, TextureFormat.Rgba16Float)

        fun image(name: String, size: Pair<Int, Int>, quantity: AlphaQuantity = AlphaQuantity.Coverage) = engine.images.image(name, ImageSize(size.first, size.second), TextureFormat.Rgba16Float, quantity)

        private fun recordPass(recording: RecordedPass, resources: RenderPassResources): RenderPass {
            val stack = MemoryStack(32)
            val regions = RenderPassRegions(recording.description.renderArea) {}
            lateinit var pass: RenderPass
            pass = proxy(RenderPass::class.java) { operation, arguments ->
                if (operation == "withViewport" || operation == "withScissor") {
                    @Suppress("UNCHECKED_CAST")
                    val commands = arguments[1] as RenderPass.() -> Unit
                    return@proxy if (operation == "withViewport") regions.withViewport(arguments[0] as Viewport) { commands(pass) }
                    else regions.withScissor(arguments[0] as ScissorRectangle) { commands(pass) }
                }
                if (operation == "getMemoryStack") return@proxy stack
                when (operation) {
                    "setStencilReference" -> { recording.events += "stencil"; assertEquals(0.toByte(), arguments[0]) }
                    "bindPipeline" -> { recording.events += "pipeline"; recording.pipeline = arguments[0] as RenderPipelineDescription }
                    "bindDescriptorSet" -> {
                        recording.events += "descriptor"
                        assertEquals(0, arguments[0])
                        val descriptors = arguments[1] as DescriptorSet
                        resources.validateDescriptorSet(descriptors)
                        val resource = descriptors.bindings.single().resources.single() as DescriptorResource.CombinedTextureSampler
                        recording.source = resource.view.texture
                    }
                    "pushConstants" -> {
                        recording.events += "push"
                        assertEquals(setOf(ShaderStage.Fragment), arguments[0])
                        assertEquals(0, arguments[2])
                        val source = arguments[1] as java.nio.ByteBuffer
                        assertTrue(source.isReadOnly)
                        val bytes = ByteArray(source.remaining())
                        source.duplicate().get(bytes)
                        recording.pushes += bytes
                    }
                    "draw" -> {
                        recording.events += "draw"
                        assertEquals(listOf(3, 0, 1, 0), arguments.toList())
                        assertEquals(Viewport.from(recording.description.renderArea), regions.viewport)
                        assertEquals(ScissorRectangle.from(recording.description.renderArea), regions.scissor)
                    }
                    else -> error("Unexpected encoded command: $operation")
                }
                null
            }
            return pass
        }
    }

    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, arguments ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === arguments?.get(0)
                "toString" -> type.simpleName
                else -> invoke(method.name.substringBefore('-'), arguments ?: emptyArray())
            }
        },
    )
}
