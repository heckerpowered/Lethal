/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.pass.*
import heckerpowered.render.opengl.function.*
import heckerpowered.render.opengl.shader.*
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.depthstencil.DepthState
import heckerpowered.render.pipeline.depthstencil.DepthStencilState
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure
import java.io.File
import java.nio.DoubleBuffer
import java.util.concurrent.TimeUnit
import kotlin.test.*

class OpenGLRenderbufferImportTest {
    @Test
    fun normalized32NativeStorageRequiresMatchingFormatAndPrecision() {
        RenderbufferFixture().use { fixture ->
            fixture.native.format = TextureFormat.Depth32UnsignedNormalized
            val before = fixture.snapshot()
            val mismatch = assertFailsWith<IllegalArgumentException> {
                fixture.device.importRenderbufferAttachment(fixture.native.name,
                    fixture.description(format = TextureFormat.Depth24UnsignedNormalized))
            }
            assertContains(checkNotNull(mismatch.message), "format=0x81a6")
            assertContains(checkNotNull(mismatch.message), "format=0x81a7")
            val depth = fixture.imported()
            fixture.pass(depth, AttachmentLoadOperation.Clear(0.5f)) {}
            assertEquals(TextureFormat.Depth32UnsignedNormalized, depth.format)
            assertEquals(before, fixture.snapshot())
            fixture.native.substitute = 0x8D54 to 24
            assertFailsWith<IllegalArgumentException> { fixture.imported() }
            fixture.native.substitute = null
            fixture.native.substitute = 0x8D44 to 0x8CAC
            assertFailsWith<IllegalArgumentException> { fixture.imported() }
            fixture.native.substitute = null
            assertEquals(before, fixture.snapshot())
            assertTrue(fixture.native.deleted.isEmpty())
        }
    }

    @Test
    fun borrowsAllSupportedDepthFormatsWithoutAllocatingOrDeletingStorage() {
        RenderbufferFixture().use { fixture ->
            for (format in listOf(TextureFormat.Depth24UnsignedNormalized, TextureFormat.Depth32UnsignedNormalized, TextureFormat.Depth24UnsignedNormalizedStencil8)) {
                fixture.native.format = format
                val depth = fixture.imported()
                val alias = fixture.imported()
                assertEquals(setOf(TextureAspect.Depth), depth.aspects)
                assertEquals(fixture.description().format, depth.format)
                val before = fixture.snapshot()
                fixture.pass(depth) { assertTrue(fixture.device.isActiveAttachment(alias.selection)) }
                assertEquals(before, fixture.snapshot())
                assertEquals(listOf(0x8D00 to fixture.native.name), fixture.native.attached.takeLast(1))
                assertEquals(0, fixture.native.allocations)
                assertTrue(fixture.native.deleted.isEmpty())
                alias.close()
                depth.close()
            }
        }
    }

    @Test
    fun closingLifetimeInvalidatesSelectionAndPreservesHostOwnership() {
        RenderbufferFixture().use { fixture ->
            val depth = fixture.device.importRenderbufferAttachment(fixture.native.name, fixture.description())
            val lifetime = ResourceLifetime.build { register(depth); this }
            lifetime.close()
            lifetime.close()
            assertTrue(fixture.native.isRenderbuffer(fixture.native.name))
            assertTrue(fixture.native.deleted.isEmpty())
            assertFailsWith<IllegalStateException> { fixture.pass(depth) {} }
            fixture.native.deleteRenderbuffer(fixture.native.name)
            assertEquals(listOf(fixture.native.name), fixture.native.deleted)
        }
    }

    @Test
    fun rejectsNativeMetadataMismatchesAndUnsupportedDescriptions() {
        RenderbufferFixture().use { fixture ->
            val before = fixture.snapshot()
            assertFailsWith<IllegalArgumentException> { fixture.device.importRenderbufferAttachment(RenderbufferName.None, fixture.description()) }
            assertFailsWith<IllegalArgumentException> { fixture.device.importRenderbufferAttachment(RenderbufferName(999), fixture.description()) }
            for (parameter in listOf(0x8D42, 0x8D43, 0x8D44, 0x8D54, 0x8D55)) {
                fixture.native.substitute = parameter to 9
                assertFailsWith<IllegalArgumentException> { fixture.imported() }
                fixture.native.substitute = null
                assertEquals(before, fixture.snapshot())
            }
            fixture.native.samples = 4
            assertFailsWith<IllegalArgumentException> { fixture.imported() }
            fixture.native.samples = 1
            assertFailsWith<IllegalArgumentException> { fixture.imported() }
            fixture.native.samples = 0
            for (description in listOf(
                fixture.description(sampleCount = SampleCount.Four),
                fixture.description(format = TextureFormat.Depth32Float),
                fixture.description(usage = setOf(TextureUsage.DepthStencilAttachment, TextureUsage.TransferSource)),
                fixture.description(usage = setOf(TextureUsage.Sampled)),
                fixture.description(mipLevelCount = 2),
                fixture.description(arrayLayerCount = 2),
            )) assertFailsWith<UnsupportedOperationException> { fixture.device.importRenderbufferAttachment(fixture.native.name, description) }
            assertEquals(before, fixture.snapshot())
            assertTrue(fixture.native.deleted.isEmpty())
        }
    }

    @Test
    fun restoresQueryBindingAndFramebufferBindingsAfterFailures() {
        RenderbufferFixture().use { fixture ->
            val before = fixture.snapshot()
            fixture.native.queryFailure = AssertionError("query failed")
            assertFailsWith<AssertionError> { fixture.imported() }
            assertEquals(before, fixture.snapshot())
            fixture.native.queryError = true
            assertFailsWith<OpenGLOperationException> { fixture.imported() }
            assertEquals(before, fixture.snapshot())
            val depth = fixture.imported()
            fixture.native.incomplete = true
            assertFailsWith<UnsupportedOperationException> { fixture.pass(depth) {} }
            fixture.native.incomplete = false
            assertEquals(before, fixture.snapshot())
            assertFailsWith<IllegalStateException> { fixture.pass(depth) { error("callback failed") } }
            assertEquals(before, fixture.snapshot())
        }
    }

    @Test
    fun packedDepthClearAndPipelineDoNotSelectStencil() {
        RenderbufferFixture().use { fixture ->
            fixture.native.format = TextureFormat.Depth24UnsignedNormalizedStencil8
            val depth = fixture.imported()
            val before = fixture.snapshot()
            val modules = ShaderStage.entries.map { stage ->
                OpenGLShaderModule(
                    fixture.device,
                    ShaderName(stage.ordinal + 1),
                    ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "test", "renderbuffer test")),
                    OpenGLShaderArtifact(stage, 120, "", "", emptyList(),
                        if (stage == ShaderStage.Fragment) listOf(OpenGLShaderInput(0, 4, "color")) else emptyList(),
                        emptyList(), emptyList()),
                )
            }
            try {
                OpenGLShaderStages(fixture.device, ProgramName(1), modules, "packed depth test",
                    resourceInterface = OpenGLShaderInterface(emptyList(), emptyList(), emptyList())).use { stages ->
                    fixture.device.createRenderPipeline(RenderPipelineDescription(
                        label = "packed depth pipeline",
                        shaders = stages,
                        colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)),
                        depthStencil = DepthStencilState(depth.format, DepthState()),
                    )).use { pipeline ->
                        fixture.pass(depth, AttachmentLoadOperation.Clear(0.75f)) { bindPipeline(pipeline) }
                    }
                }
            } finally {
                modules.forEach { it.close() }
            }
            assertEquals(listOf(0x100), fixture.clearMasks)
            assertEquals(before, fixture.snapshot())
        }
    }

    @Test
    fun rejectsWrongDeviceContextEncodingAndRenderbufferTransfers() {
        RenderbufferFixture().use { fixture ->
            val depth = fixture.imported()
            fixture.accessAllowed = false
            assertFailsWith<IllegalStateException> { fixture.imported() }
            assertFailsWith<IllegalStateException> { fixture.device.requireAttachment(depth) }
            fixture.accessAllowed = true
            RenderbufferFixture().use { other -> assertFailsWith<IllegalArgumentException> { other.device.requireAttachment(depth) } }
            fixture.device.encode("renderbuffer rejection") {
                assertFailsWith<IllegalStateException> { fixture.imported() }
                assertFailsWith<UnsupportedOperationException> { discardContents(ImageRegion.Attachment(depth, aspect = TextureAspect.Depth)) }
            }
            assertTrue(fixture.native.deleted.isEmpty())
        }
    }

    @Test
    fun closingActiveSelectionOrPhysicalAliasTerminates() {
        for (scenario in listOf("active", "alias", "restore")) {
            val marker = File.createTempFile("renderbuffer-fatal-", ".marker").also { it.delete() }
            val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
                "-cp", System.getProperty("java.class.path"), OpenGLRenderbufferFatalProbe::class.java.name, scenario, marker.path).redirectErrorStream(true).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.exitValue() != 0, output)
            assertTrue(if (scenario == "restore") "restore failed" in output else "active render pass" in output, output)
            assertFalse(marker.exists(), output)
        }
    }
}

private class BorrowedRenderbufferFunctions(private val driver: DrawStateDriver) : OpenGLFramebufferFunctions by checkNotNull(driver.functions.framebuffers) {
    val name = RenderbufferName(301)
    var bound = RenderbufferName(94)
    var format = TextureFormat.Depth24UnsignedNormalized
    var samples = 0
    var substitute: Pair<Int, Int>? = null
    var queryFailure: AssertionError? = null
    var queryError = false
    var incomplete = false
    var fatalRestore = false
    var allocations = 0
    val attached = mutableListOf<Pair<Int, RenderbufferName>>()
    val deleted = mutableListOf<RenderbufferName>()
    override fun isRenderbuffer(renderbuffer: RenderbufferName) = renderbuffer == name && renderbuffer !in deleted
    override fun createRenderbuffer() = RenderbufferName(500 + allocations++)
    override fun getBoundRenderbuffer() = bound
    override fun bindRenderbuffer(renderbuffer: RenderbufferName) {
        if (fatalRestore && renderbuffer == RenderbufferName(94)) throw AssertionError("renderbuffer restore failed")
        bound = renderbuffer
    }
    override fun getRenderbufferParameter(parameter: Int): Int {
        queryFailure?.let { queryFailure = null; throw it }
        if (queryError) { queryError = false; driver.nativeError = 0x0502 }
        substitute?.let { if (it.first == parameter) return it.second }
        return when (parameter) {
            0x8D42, 0x8D43 -> 64
            0x8D44 -> when (format) {
                TextureFormat.Depth24UnsignedNormalized -> 0x81A6
                TextureFormat.Depth32UnsignedNormalized -> 0x81A7
                TextureFormat.Depth24UnsignedNormalizedStencil8 -> 0x88F0
                else -> error("Unsupported fixture format")
            }
            0x8D54 -> if (format == TextureFormat.Depth32UnsignedNormalized) 32 else 24
            0x8D55 -> if (format == TextureFormat.Depth24UnsignedNormalizedStencil8) 8 else 0
            else -> error("Unexpected renderbuffer query $parameter")
        }
    }
    override fun getRenderbufferSamples() = samples
    override fun framebufferRenderbuffer(attachment: Int, renderbuffer: RenderbufferName) { attached += attachment to renderbuffer }
    override fun checkFramebufferStatus() = if (incomplete) 0x8CD6 else 0x8CD5
    override fun deleteRenderbuffer(renderbuffer: RenderbufferName) { deleted += renderbuffer }
}

private class RenderbufferFixture : AutoCloseable {
    val driver = DrawStateDriver(true)
    val native = BorrowedRenderbufferFunctions(driver)
    var accessAllowed = true
    var clearDepth = 0.375
    val clearMasks = mutableListOf<Int>()
    private val functions = object : OpenGLFunctions by driver.functions {
        override val framebuffers = native
        override fun checkCurrentContext() { check(accessAllowed) { "The original OpenGL context is not current" } }
        override fun getDoubles(parameter: Int, destination: DoubleBuffer) {
            if (parameter == 0x0B73) destination.put(0, clearDepth) else driver.functions.getDoubles(parameter, destination)
        }
        override fun clearDepth(depth: Double) { clearDepth = depth }
        override fun clear(mask: Int) { clearMasks += mask }
    }
    val device = OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler())
    private val lifetime = ResourceLifetime.build { this }
    private val color = device.createTexture(TextureDescription(label = "renderbuffer color", width = 64, height = 64, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.ColorAttachment)))
    fun description(sampleCount: SampleCount = SampleCount.One, format: TextureFormat = native.format, usage: Set<TextureUsage> = setOf(TextureUsage.DepthStencilAttachment), mipLevelCount: Int = 1, arrayLayerCount: Int = 1) = TextureDescription(label = "borrowed renderbuffer", width = 64, height = 64, format = format, usage = usage, sampleCount = sampleCount, mipLevelCount = mipLevelCount, arrayLayerCount = arrayLayerCount)
    fun imported() = device.importRenderbufferAttachment(native.name, description()).also { lifetime.register(it) }
    fun snapshot() = listOf(driver.snapshot(), native.bound, clearDepth)
    fun pass(depth: RenderAttachment, load: AttachmentLoadOperation<Float> = AttachmentLoadOperation.Load, commands: RenderPass.() -> Unit) {
        val attachment = device.createAttachmentView(device.createTextureView(color, TextureViewDescription()))
        device.encode("renderbuffer test") {
            renderPass(RenderPassDescription(label = "renderbuffer pass", colorAttachments = listOf(RenderPassAttachment(attachment)),
                depthAttachment = RenderPassAttachment(depth, AttachmentOperations(load, AttachmentStoreOperation.Store))), commands = commands)
        }
    }
    override fun close() = terminateOnFailure {
        lifetime.close()
        color.close()
        device.close()
        driver.device.close()
    }
}

object OpenGLRenderbufferFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { File(arguments[1]).writeText("hook ran") })
        RenderbufferFixture().use { fixture ->
            if (arguments[0] == "restore") { fixture.native.fatalRestore = true; fixture.imported() }
            val depth = fixture.imported()
            val closeTarget = if (arguments[0] == "alias") fixture.imported() else depth
            fixture.pass(depth) { closeTarget.close() }
        }
        error("Fatal boundary returned")
    }
}
