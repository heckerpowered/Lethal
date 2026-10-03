/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render.postprocess.bloom

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.command.pass.*
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.image.ImageSize
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.engine.pass.screenPass
import heckerpowered.render.engine.shader.program.copyImageShader
import heckerpowered.lethal.gameplay.client.render.postprocess.withWriteMask
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.pipeline.color.BlendState
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.color.ColorWriteMask
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

/** Records the real engine's prepared pipelines; only device calls and compiler reflection are fixtures. */
class BloomOutputMaskTest {
    @Test
    fun maskedCompositionCannotRelabelSignalAsOwnedCoverage() {
        val recorder = BloomRecordingDevice()
        val engine = RenderEngine.create(recorder.device)
        try {
            val source = engine.images.image("signal", ImageSize(8, 8), TextureFormat.Rgba8UnsignedNormalized, AlphaQuantity.Signal)
            val coverage = engine.images.image("coverage", ImageSize(8, 8), TextureFormat.Rgba8UnsignedNormalized, AlphaQuantity.Coverage)
            val output = recorder.output.derive(colorAttachments = listOf(RenderPassAttachment<heckerpowered.render.color.Color>(coverage.attachment, AttachmentOperations(AttachmentLoadOperation.Clear(heckerpowered.render.color.Color.TransparentBlack), AttachmentStoreOperation.Store))))
            for (mask in eyeMasks) {
                assertFailsWith<IllegalArgumentException> {
                    engine.stage {
                        val pass = screenPass(output, copyImageShader().bind(source), CompositingMode.SourceOver.withWriteMask(mask))
                        rasterPass(RasterPass(pass.description, pass.collection, pass.inputs, alphaQuantities = mapOf(0 to AlphaQuantity.Coverage)))
                    }
                }
                assertTrue(recorder.states.isEmpty())
            }
        } finally { engine.close() }
    }

    @Test
    fun sceneBloomKeepsIntermediateWritesCompleteAndPreservesSceneAlphaForBothEyes() {
        val recorder = BloomRecordingDevice()
        val engine = RenderEngine.create(recorder.device)
        try {
            val source = engine.images.image("source", ImageSize(8, 8), TextureFormat.Rgba8UnsignedNormalized, AlphaQuantity.Signal)
            val renderer = BloomRenderer(engine.images, TextureFormat.Rgba8UnsignedNormalized)
            for (mask in eyeMasks) {
                recorder.states.clear()
                engine.stage { renderer.apply(this, source, recorder.output, 0.5F, mask) }
                assertTrue(recorder.states.size > 2)
                assertTrue(recorder.states.dropLast(1).all { it.second.writeMask == ColorWriteMask.All })
                val final = recorder.states.last().second
                assertEquals(mask, final.writeMask)
                assertEquals(CompositingMode.AddColorPreserveAlpha.target(final.format, null, null).blend, final.blend)
                assertEquals("Scene Bloom reference", recorder.states.last().first)
            }
        } finally { engine.close() }
    }

    @Test
    fun electricContentAndGlowRestrictOnlyTheirSceneCompositionsWithoutChangingNumericAlpha() {
        val recorder = BloomRecordingDevice()
        val engine = RenderEngine.create(recorder.device)
        try {
            val source = engine.images.image("electric", ImageSize(8, 8), TextureFormat.Rgba8UnsignedNormalized, AlphaQuantity.Signal)
            val renderer = BloomRenderer(engine.images, TextureFormat.Rgba8UnsignedNormalized)
            for (mask in eyeMasks) {
                recorder.states.clear()
                engine.stage { renderer.compositeContent(this, source, recorder.output, 0.5F, mask) }
                assertTrue(recorder.states.size > 3)
                assertTrue(recorder.states.dropLast(2).all { it.second.writeMask == ColorWriteMask.All })
                val [content, glow] = recorder.states.takeLast(2)
                assertEquals("Electric content", content.first)
                assertEquals("Electric glow", glow.first)
                assertEquals(mask, content.second.writeMask)
                assertEquals(mask, glow.second.writeMask)
                assertEquals(BlendState.PremultipliedAlpha, content.second.blend)
                assertEquals(BlendState.Additive, glow.second.blend)
            }
        } finally { engine.close() }
    }

    @Test
    fun electricHdrStorageSurvivesEveryIntermediatePassUntilSceneComposition() {
        for (format in listOf(TextureFormat.Rgba16Float, TextureFormat.Rgba32Float)) {
            val recorder = BloomRecordingDevice()
            val engine = RenderEngine.create(recorder.device)
            try {
                val source = engine.images.image("electric", ImageSize(8, 8), format, AlphaQuantity.Signal)
                val renderer = BloomRenderer(engine.images, format)
                for (mask in eyeMasks) {
                    recorder.states.clear()
                    engine.stage { renderer.compositeContent(this, source, recorder.output, 0.7F, mask) }
                    assertTrue(recorder.states.size > 3)
                    for ((_, target) in recorder.states.dropLast(2)) {
                        assertEquals(format, target.format)
                        assertEquals(ColorWriteMask.All, target.writeMask)
                    }
                    val [content, glow] = recorder.states.takeLast(2)
                    assertEquals(TextureFormat.Rgba8UnsignedNormalized, content.second.format)
                    assertEquals(TextureFormat.Rgba8UnsignedNormalized, glow.second.format)
                    assertEquals(mask, content.second.writeMask)
                    assertEquals(mask, glow.second.writeMask)
                    assertEquals(BlendState.PremultipliedAlpha, content.second.blend)
                    assertEquals(BlendState.Additive, glow.second.blend)
                }
                assertTrue(recorder.textures.size > 2)
                for (texture in recorder.textures) {
                    assertEquals(format, texture.format)
                    assertEquals(setOf(TextureUsage.Sampled, TextureUsage.ColorAttachment), texture.usage)
                }
            } finally { engine.close() }
        }
    }

    @Test
    fun sceneBloomUsesHdrIntermediatesWhileSceneSnapshotAndDestinationRemainRgba8() {
        for (format in listOf(TextureFormat.Rgba16Float, TextureFormat.Rgba32Float)) {
            val recorder = BloomRecordingDevice()
            val engine = RenderEngine.create(recorder.device)
            try {
                val snapshot = engine.images.image("snapshot", ImageSize(8, 8), TextureFormat.Rgba8UnsignedNormalized, AlphaQuantity.Signal)
                val renderer = BloomRenderer(engine.images, format)
                engine.stage { renderer.apply(this, snapshot, recorder.output, 0.7F, ColorWriteMask.All) }
                assertTrue(recorder.states.size > 2)
                assertTrue(recorder.states.dropLast(1).all { it.second.format == format })
                val final = recorder.states.last().second
                assertEquals(TextureFormat.Rgba8UnsignedNormalized, final.format)
                assertEquals(CompositingMode.AddColorPreserveAlpha.target(final.format, null, null).blend, final.blend)
                assertEquals(TextureFormat.Rgba8UnsignedNormalized, recorder.textures.first().format)
                assertTrue(recorder.textures.drop(1).all { it.format == format })
                assertTrue(recorder.textures.all { it.usage == setOf(TextureUsage.Sampled, TextureUsage.ColorAttachment) })
            } finally { engine.close() }
        }
    }

    private val eyeMasks = listOf(
        ColorWriteMask(false, true, true, false), ColorWriteMask(true, false, false, false), ColorWriteMask.All,
    )
}

private class BloomRecordingDevice {
    val states = mutableListOf<Pair<String, ColorTargetState>>()
    val textures = mutableListOf<TextureDescription>()
    private val stack = MemoryStack(256)
    private var label = ""
    private val pass: RenderPass by lazy { fixtureProxy<RenderPass> { call, arguments ->
        when (call) {
            "getMemoryStack" -> stack
            "withViewport", "withScissor" -> {
                @Suppress("UNCHECKED_CAST")
                val body = arguments[1] as RenderPass.() -> Any?
                body(pass)
            }
            "bindPipeline" -> { states += label to (arguments[0] as RenderPipelineDescription).colorTargets.single(); Unit }
            "bindDescriptorSet", "pushConstants", "draw", "setStencilReference" -> Unit
            else -> error(call)
        }
    } }
    private val encoder = fixtureProxy<CommandEncoder> { call, arguments ->
        when (call) {
            "getMemoryStack" -> stack
            "renderPass" -> {
                label = (arguments[0] as RenderPassDescription).label
                @Suppress("UNCHECKED_CAST")
                val body = arguments.last() as RenderPass.() -> Unit
                body(pass)
            }
            else -> error(call)
        }
    }
    val device = fixtureProxy<GraphicsDevice> { call, arguments ->
        when (call) {
            "compileCanonicalShader" -> screenInterface(arguments[0] as ShaderModuleDescription, arguments[1] as String)
            "createShaderModule" -> {
                val description = arguments[0] as ShaderModuleDescription
                fixtureProxy<ShaderModule> { operation, _ -> when (operation) {
                    "getStage" -> description.stage
                    "getEntryPoint" -> description.entryPoint
                    "close" -> terminateOnFailure {}
                    else -> error(operation)
                } }
            }
            "createShaderStages" -> fixtureProxy<ShaderStages> { operation, _ -> check(operation == "close"); terminateOnFailure {} }
            "createPipelineLayout" -> fixtureProxy<PipelineLayout> { operation, _ -> check(operation == "close"); terminateOnFailure {} }
            "createSampler" -> fixtureProxy<GpuSampler> { operation, _ -> check(operation == "close"); terminateOnFailure {} }
            "createTexture" -> {
                val description = arguments[0] as TextureDescription
                textures += description
                fixtureProxy<GpuTexture> { operation, _ -> when (operation) {
                    "getWidth" -> description.width
                    "getHeight" -> description.height
                    "getFormat" -> description.format
                    "getDimension" -> description.dimension
                    "getStorage" -> description.storage
                    "getUsage" -> description.usage
                    "getSampleCount" -> description.sampleCount
                    "getDepth", "getMipLevelCount", "getArrayLayerCount" -> 1
                    "getCubeCompatible" -> false
                    "close" -> terminateOnFailure {}
                    else -> error(operation)
                } }
            }
            "createTextureView" -> {
                val texture = arguments[0] as GpuTexture
                fixtureProxy<GpuTextureView> { operation, _ -> when (operation) {
                    "getTexture" -> texture
                    "getWidth" -> texture.width
                    "getHeight" -> texture.height
                    "getFormat" -> texture.format
                    "getDimension" -> TextureViewDimension.TwoDimensional
                    "getAspects" -> setOf(TextureAspect.Color)
                    "getBaseMipLevel", "getBaseArrayLayer" -> 0
                    "getDepth", "getMipLevelCount", "getArrayLayerCount" -> 1
                    else -> error(operation)
                } }
            }
            "createAttachmentView" -> attachment(arguments[0] as GpuTextureView)
            "encode" -> {
                @Suppress("UNCHECKED_CAST")
                val body = arguments.last() as CommandEncoder.() -> Unit
                body(encoder)
            }
            "awaitIdle" -> Unit
            else -> error(call)
        }
    }
    val output = RenderPassDescription("Scene Bloom reference", colorAttachments = listOf(RenderPassAttachment<heckerpowered.render.color.Color>(fixtureProxy<RenderAttachment> { call, _ -> when (call) {
        "getFormat" -> TextureFormat.Rgba8UnsignedNormalized
        "getWidth", "getHeight" -> 8
        "getArrayLayerCount" -> 1
        "getAspects" -> setOf(TextureAspect.Color)
        "getSampleCount" -> SampleCount.One
        else -> error(call)
    } })))
}

private fun attachment(view: GpuTextureView): RenderAttachment = fixtureProxy { call, _ -> when (call) {
    "getWidth" -> view.width
    "getHeight" -> view.height
    "getFormat" -> view.format
    "getArrayLayerCount" -> 1
    "getAspects" -> setOf(TextureAspect.Color)
    "getSampleCount" -> SampleCount.One
    else -> error(call)
} }

private fun screenInterface(description: ShaderModuleDescription, origin: String): ShaderCompilation {
    fun vector(size: Int) = ShaderValueDescription(ShaderScalarKind.Float, 32, size, 1)
    val coordinates = ShaderInterfaceVariable("coordinates", 0, vector(2))
    val facts = if (description.stage == ShaderStage.Vertex) ShaderInterfaceDescription(emptyList(), listOf(coordinates), emptyList()) else {
        val image = ShaderInterfaceResource(ShaderInterfaceResourceKind.CombinedTextureSampler, "image", 0, 0, emptyList(), null, null, emptyList(), ShaderImageDescription(ShaderImageDimension.TwoDimensional, false, false, false, vector(1)))
        val field = when (origin.substringAfterLast('/')) {
            "copy.frag" -> null
            "tent.frag" -> "texelSize" to 2
            "brightness.frag" -> "threshold" to 1
            else -> error(origin)
        }
        val resources = if (field == null) listOf(image) else {
            val size = field.second.toLong() * Float.SIZE_BYTES
            listOf(image, ShaderInterfaceResource(ShaderInterfaceResourceKind.PushConstant, "params", null, null, emptyList(), size, "Params", listOf(ShaderInterfaceBlockMember(field.first, 0, size, vector(field.second), 0, 0, false)), null))
        }
        ShaderInterfaceDescription(listOf(coordinates), listOf(ShaderInterfaceVariable("result", 0, vector(4))), resources)
    }
    return ShaderCompilation(description, facts)
}

private inline fun <reified T> fixtureProxy(crossinline body: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, arguments ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === arguments?.get(0)
            else -> body(method.name.substringBefore('-'), arguments ?: emptyArray())
        }
    } as T
