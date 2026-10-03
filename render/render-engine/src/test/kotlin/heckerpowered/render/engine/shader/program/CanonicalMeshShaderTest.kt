/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.shader.program

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.image.ImageSize
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.pass.replacementPass
import heckerpowered.render.engine.pass.screenPass
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.ShaderInterfaceDescription
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

class CanonicalMeshShaderTest {
    @Test
    fun directBuiltinDefinitionsReadNoSourceUntilPreparationAndShareEagerFirstUseCache() {
        val fixture = Fixture()
        val shader = tentShader()
        val engine = fixture.engine()
        try {
            assertEquals(0, fixture.compilationCalls)
            engine.prepare(shader)
            assertEquals(2, fixture.compilationCalls)
            assertEquals(listOf(2, 2, 1, 1), fixture.counts())
            val source = engine.images.image("source", ImageSize(4, 4), TextureFormat.Rgba16Float)
            val target = engine.images.image("target", ImageSize(4, 4), TextureFormat.Rgba16Float)
            engine.stage { rasterPass(screenPass(replacementPass("tent", target), shader.bind(source))) }
            engine.prepare(shader)
            assertEquals(2, fixture.compilationCalls)
            assertEquals(listOf(2, 2, 1, 1), fixture.counts())
            assertEquals(1, fixture.awaitCalls)
        } finally { engine.close() }
        assertEquals(fixture.created, fixture.closed)
        assertEquals(1, fixture.samplerCreated)
        assertEquals(1, fixture.samplerClosed)
    }

    @Test
    fun resourceDefinitionsSelectSourceGenerationsPerEngineAndFailedLoadsRetry() {
        var failLoad = true
        var reads = 0
        fun sources(generation: String) = ShaderSources { resources ->
            reads++
            val currentGeneration = generation
            check(!failLoad) { "Source generation temporarily unavailable" }
            resources.map { [stage, path] ->
                CanonicalShaderModule(ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, currentGeneration, path)), path.substringAfterLast('/'))
            }
        }
        val definition = shaderDefinition("copy") {
            vertex("/assets/render-engine/shaders/fullscreen.vert")
            fragment("/assets/render-engine/shaders/copy.frag")
            inputs { screenShaderInputs(this) }
            sourceRepresentation(AlphaRepresentation.Premultiplied)
            output(0, FragmentOutput(AlphaRepresentation.Premultiplied, alphaFromTexture = ParameterName("source")))
            replaySafe()
        }
        val shader = MeshShader<Unit>(definition) { error("This test prepares without drawing") }
        val first = Fixture()
        val second = Fixture()
        val firstEngine = first.engine(sources("A"))
        val secondEngine = second.engine(sources("B"))
        try {
            assertFailsWith<IllegalStateException> { firstEngine.prepare(shader) }
            assertEquals(0, first.compilationCalls)
            failLoad = false
            firstEngine.prepare(shader)
            secondEngine.prepare(shader)
            assertEquals(3, reads)
            assertEquals(listOf("A", "A"), first.sourceTexts)
            assertEquals(listOf("B", "B"), second.sourceTexts)
            firstEngine.prepare(shader)
            assertEquals(listOf("A", "A"), first.sourceTexts)
            assertEquals(listOf(2, 2, 1, 1), first.counts())
            assertEquals(first.counts(), second.counts())
        } finally {
            secondEngine.close()
            firstEngine.close()
        }
        assertEquals(first.created, first.closed)
        assertEquals(second.created, second.closed)
        assertEquals(first.samplerCreated, first.samplerClosed)
        assertEquals(second.samplerCreated, second.samplerClosed)
    }

    @Test
    fun reflectedBindingOffsetsAndExtentsDriveTheTypedScreenMapping() {
        val fixture = Fixture()
        fixture.binding = 3
        fixture.pushOffset = 16
        val root = ResourceLifetime.build { this }
        try {
            val programs = ShaderRealizations(fixture.device, root)
            val definition = programs.definition(tentShader())
            assertEquals(listOf(2, 0, 0, 0), fixture.counts())
            val descriptor = definition.inputs.descriptors.single()
            assertEquals(3, descriptor.parameters.single().binding)
            assertEquals(ParameterName("source"), descriptor.parameters.single().name)
            val push = definition.inputs.pushes.single()
            assertEquals(24, push.sizeBytes)
            assertEquals(16, push.fields.single().offsetBytes)
            assertEquals(8, push.fields.single().sizeBytes)
        } finally { root.close() }
        assertEquals(0, fixture.created)
    }

    @Test
    fun unsupportedCanonicalCompilationAndMalformedFactsNeverCreateGpuObjects() {
        val fixture = Fixture()
        val engine = fixture.engine()
        val shader = copyImageShader()
        try {
            fixture.unsupported = true
            assertFailsWith<UnsupportedOperationException> { engine.prepare(shader) }
            assertEquals(0, fixture.created)
            fixture.unsupported = false
            fixture.malformed = true
            assertFailsWith<IllegalArgumentException> { engine.prepare(shader) }
            assertEquals(0, fixture.created)
            fixture.malformed = false
            engine.prepare(shader)
            assertEquals(2, fixture.moduleCalls)
            assertEquals(1, fixture.stageCalls)
            assertEquals(1, fixture.layoutCalls)
        } finally { engine.close() }
        assertEquals(fixture.created, fixture.closed)
        assertEquals(1, fixture.samplerCreated)
        assertEquals(1, fixture.samplerClosed)
    }

    @Test
    fun surfaceFactoriesKeepSourceLoadingOutOfConstruction() {
        assertEquals("unlit", unlitShader().label)
        assertEquals("textured", texturedStraightShader().label)
        assertEquals("texturedPremultiplied", texturedPremultipliedShader().label)
    }

    private class Fixture {
        val sourceTexts = mutableListOf<String>()
        var samplerCreated = 0
        var samplerClosed = 0
        var awaitCalls = 0
        var compilationCalls = 0
        var moduleCalls = 0
        var stageCalls = 0
        var layoutCalls = 0
        var created = 0
        var closed = 0
        var binding = 0
        var pushOffset = 0
        var unsupported = false
        var malformed = false
        private fun <T> owned(type: Class<T>): T {
            created++
            return proxy(type) { operation, _ ->
                check(operation == "close")
                terminateOnFailure { closed++; Unit }
            }
        }
        val device = proxy(GraphicsDevice::class.java) { operation, arguments ->
            when (operation) {
                "compileCanonicalShader" -> {
                    compilationCalls++
                    if (unsupported) throw UnsupportedOperationException("fixture device has no canonical capability")
                    val description = arguments[0] as ShaderModuleDescription
                    sourceTexts += (description.code as ShaderSource).text
                    val ready = fixtureScreenPreparation(description, arguments[1] as String, binding, pushOffset)
                    if (malformed && description.stage == ShaderStage.Fragment) ShaderCompilation(ready.module, ShaderInterfaceDescription(emptyList(), emptyList(), emptyList())) else ready
                }
                "createShaderModule" -> { moduleCalls++; owned(ShaderModule::class.java) }
                "createShaderStages" -> { stageCalls++; owned(ShaderStages::class.java) }
                "createPipelineLayout" -> { layoutCalls++; owned(PipelineLayout::class.java) }
                "createTexture" -> {
                    val description = arguments[0] as TextureDescription
                    created++
                    proxy(GpuTexture::class.java) { property, _ -> when (property) {
                        "getWidth" -> description.width; "getHeight" -> description.height; "getDepth", "getArrayLayerCount", "getMipLevelCount" -> 1
                        "getFormat" -> description.format; "getDimension" -> description.dimension; "getSampleCount" -> description.sampleCount; "getUsage" -> description.usage
                        "close" -> terminateOnFailure { closed++; Unit }; else -> error(property)
                    } }
                }
                "createTextureView" -> {
                    val texture = arguments[0] as GpuTexture
                    proxy(GpuTextureView::class.java) { property, _ -> when (property) {
                        "getTexture" -> texture; "getFormat" -> texture.format; "getWidth" -> texture.width; "getHeight" -> texture.height
                        "getDimension" -> TextureViewDimension.TwoDimensional; "getBaseMipLevel", "getBaseArrayLayer" -> 0
                        "getDepth", "getMipLevelCount", "getArrayLayerCount" -> 1; "getAspects" -> setOf(TextureAspect.Color); else -> error(property)
                    } }
                }
                "createAttachmentView" -> {
                    val view = arguments[0] as GpuTextureView
                    proxy(RenderAttachment::class.java) { property, _ -> when (property) {
                        "getWidth" -> view.width; "getHeight" -> view.height; "getArrayLayerCount" -> 1
                        "getFormat" -> view.format; "getSampleCount" -> SampleCount.One; "getAspects" -> view.aspects; else -> error(property)
                    } }
                }
                "createSampler" -> {
                    samplerCreated++
                    proxy(GpuSampler::class.java) { property, _ ->
                        check(property == "close")
                        terminateOnFailure { samplerClosed++; Unit }
                    }
                }
                "awaitIdle" -> { awaitCalls++; Unit }
                "encode" -> Unit
                else -> error(operation)
            }
        }
        fun counts() = listOf(compilationCalls, moduleCalls, stageCalls, layoutCalls)
        fun engine(sources: ShaderSources = ClasspathShaderSources) = RenderEngine.create(device, sources)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> invoke(method.name.substringBefore('-'), args ?: emptyArray()) } as T
    }
}
