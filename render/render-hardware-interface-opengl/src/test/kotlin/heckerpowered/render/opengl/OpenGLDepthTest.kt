/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.opengl

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.*
import heckerpowered.render.opengl.function.*
import heckerpowered.render.opengl.shader.*
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.depthstencil.*
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.DoubleBuffer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*

class OpenGLDepthTest {
    @Test
    fun unavailableFormatsRejectBeforeAllocationWithoutRaisingTheColorBaseline() {
        DepthFixture().use { fixture ->
            fixture.driver.depthSupported = false
            fixture.driver.floatSupported = false
            val count = fixture.driver.allocations
            for (format in listOf(TextureFormat.Depth24UnsignedNormalized, TextureFormat.Depth32UnsignedNormalized, TextureFormat.Depth32Float)) {
                assertFailsWith<UnsupportedOperationException> { fixture.depth(format) }
                assertFailsWith<UnsupportedOperationException> { fixture.pipeline(format) }
            }
            assertEquals(count, fixture.driver.allocations)
            fixture.color()
        }
    }

    @Test
    fun exactDepthStorageAndFailedEstablishmentPreserveBindingsAndReleaseTheName() {
        DepthFixture().use { fixture ->
            for (format in listOf(TextureFormat.Depth24UnsignedNormalized, TextureFormat.Depth32UnsignedNormalized, TextureFormat.Depth32Float)) {
                val texture = fixture.depth(format)
                assertEquals(format, texture.format)
            }
            fixture.driver.substitutedFormat = 0x81A7
            val before = fixture.driver.base.textureBinding
            val allocated = fixture.driver.allocations
            assertFailsWith<TextureCreationException> { fixture.depth(TextureFormat.Depth24UnsignedNormalized) }
            assertEquals(before, fixture.driver.base.textureBinding)
            assertTrue(fixture.driver.deleted.contains(allocated + 100))
            fixture.driver.substitutedFormat = null
            fixture.driver.depthBits = 32
            assertFailsWith<TextureCreationException> { fixture.depth(TextureFormat.Depth24UnsignedNormalized) }
        }
    }

    @Test
    fun depthSampleAndTransferUsageAreRejectedBeforeAllocation() {
        DepthFixture().use { fixture ->
            val count = fixture.driver.allocations
            for (usage in listOf(TextureUsage.Sampled, TextureUsage.TransferSource, TextureUsage.TransferDestination)) {
                assertFailsWith<UnsupportedOperationException> { fixture.depth(usage = setOf(TextureUsage.DepthStencilAttachment, usage)) }
            }
            assertEquals(count, fixture.driver.allocations)
        }
    }

    @Test
    fun clearLoadCompareAndWriteSelectionRestoreHostState() {
        DepthFixture().use { fixture ->
            val depth = fixture.depth()
            val before = fixture.driver.snapshot()
            for (compare in CompareFunction.entries) for (write in listOf(false, true)) {
                val pipeline = fixture.pipeline(compare = compare, write = write)
                fixture.pass(depth, AttachmentLoadOperation.Clear(0.25f)) {
                    bindPipeline(pipeline)
                    assertEquals(compare.ordinal + 0x0200, fixture.driver.base.depthFunction)
                    assertEquals(write, fixture.driver.base.depthWrite)
                    assertTrue(fixture.driver.base.enables[0x0B71] == true)
                    withViewport(Viewport(
                        0f,
                        0f,
                        64f,
                        64f,
                        0.9f,
                        0.1f,
                    )) { draw(3) }
                }
                assertEquals(before, fixture.driver.snapshot())
                assertEquals(listOf(0.9f.toDouble(), 0.1f.toDouble()), fixture.driver.base.draws.last().depth)
            }
            assertEquals(16, fixture.driver.clears.size)
            assertTrue(fixture.driver.clears.all { it == 0.25 })
            fixture.pass(depth, AttachmentLoadOperation.Load) { bindPipeline(fixture.pipeline()); draw(3) }
            fixture.pass(depth, AttachmentLoadOperation.Discard) { bindPipeline(fixture.pipeline()); draw(3) }
            assertEquals(16, fixture.driver.clears.size)
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun disabledDepthRetainsTheFormatRequirementAndDoesNotWrite() {
        DepthFixture().use { fixture ->
            val depth = fixture.depth()
            val pipeline = fixture.pipeline(processing = false)
            fixture.pass(depth) {
                bindPipeline(pipeline)
                assertFalse(fixture.driver.base.depthWrite)
                assertFalse(fixture.driver.base.enables[0x0B71] == true)
            }
            assertFailsWith<IllegalArgumentException> { fixture.pass(null) { bindPipeline(pipeline) } }
            val other = fixture.depth(TextureFormat.Depth32Float)
            assertFailsWith<IllegalArgumentException> { fixture.pass(other) { bindPipeline(pipeline) } }
        }
    }

    @Test
    fun stencilAndMismatchedDepthAreRejectedBeforeCallbacksOrClears() {
        DepthFixture().use { fixture ->
            val small = fixture.depth(width = 32)
            val before = fixture.driver.snapshot()
            assertFailsWith<IllegalArgumentException> { fixture.pass(small, AttachmentLoadOperation.Clear(1f)) { error("callback reached") } }
            assertEquals(before, fixture.driver.snapshot())
            assertTrue(fixture.driver.clears.isEmpty())
            val depth = fixture.depth()
            assertFailsWith<IllegalArgumentException> { fixture.pass(depth, AttachmentLoadOperation.Load, stencil = true) { error("callback reached") } }
            assertTrue(fixture.driver.clears.isEmpty())
        }
    }

    @Test
    fun exceptionalSetupClearBindingAndDrawingRestoreBothAttachmentsAndNativeDepthState() {
        for (operation in listOf("framebufferTexture2D", "checkFramebufferStatus", "getDoubles", "clearDepth", "depthMask", "clear", "depthFunction", "drawArrays")) {
            DepthFixture().use { fixture ->
                val depth = fixture.depth()
                val pipeline = fixture.pipeline()
                val before = fixture.driver.snapshot()
                fixture.driver.failure = operation
                assertFailsWith<OpenGLOperationException> {
                    fixture.pass(depth, AttachmentLoadOperation.Clear(1f)) { bindPipeline(pipeline); draw(3) }
                }
                assertEquals(before, fixture.driver.snapshot())
            }
        }
    }
    @Test
    fun hostDepthBoundsAreNeutralizedWithOrWithoutOrdinaryDepthProcessing() {
        for (hostEnabled in listOf(false, true)) {
            DepthFixture().use { fixture ->
                fixture.driver.boundsSupported = true
                fixture.driver.base.enables[0x8890] = hostEnabled
                val depth = fixture.depth()
                val before = fixture.driver.snapshot()
                val pipelines = listOf(
                    fixture.pipeline(),
                    fixture.pipeline(processing = false),
                    fixture.pipeline(depthDeclared = false),
                )
                for (pipeline in pipelines) {
                    fixture.driver.boundsCalls.clear()
                    fixture.pass(depth) {
                        bindPipeline(pipeline)
                        assertFalse(fixture.driver.base.enables.getValue(0x8890))
                        draw(3)
                    }
                    assertEquals(before, fixture.driver.snapshot())
                    assertEquals(listOf("isEnabled", "disable", if (hostEnabled) "enable" else "disable"), fixture.driver.boundsCalls)
                }
            }
        }
    }

    @Test
    fun absentDepthBoundsExtensionHasNoUnsupportedQueriesOrMutations() {
        DepthFixture().use { fixture ->
            val depth = fixture.depth()
            val pipeline = fixture.pipeline()
            val before = fixture.driver.snapshot()
            fixture.pass(depth) { bindPipeline(pipeline); draw(3) }
            assertEquals(before, fixture.driver.snapshot())
            assertTrue(fixture.driver.boundsCalls.isEmpty())
        }
    }

    @Test
    fun failedDepthBoundsQueryAllowsRetryButFailedDisableSealsTheEncoderAndRestoresState() {
        for (operation in listOf("isEnabled", "disable")) {
            DepthFixture().use { fixture ->
                fixture.driver.boundsSupported = true
                fixture.driver.base.enables[0x8890] = true
                val depth = fixture.depth()
                val pipeline = fixture.pipeline()
                val before = fixture.driver.snapshot()
                fixture.driver.failure = "bounds:$operation"
                val commands: RenderPass.() -> Unit = {
                    val beforeBinding = fixture.driver.snapshot()
                    assertFailsWith<OpenGLOperationException> { bindPipeline(pipeline) }
                    if (operation == "isEnabled") {
                        assertEquals(beforeBinding, fixture.driver.snapshot())
                        assertEquals(listOf("isEnabled"), fixture.driver.boundsCalls)
                        bindPipeline(pipeline)
                        draw(3)
                    } else {
                        assertFailsWith<IllegalStateException> { bindPipeline(pipeline) }
                        assertFailsWith<IllegalStateException> { draw(3) }
                    }
                }
                if (operation == "isEnabled") fixture.pass(depth, commands = commands) else {
                    assertFailsWith<IllegalStateException> { fixture.pass(depth, commands = commands) }
                }
                assertEquals(before, fixture.driver.snapshot())
                assertEquals("enable", fixture.driver.boundsCalls.last())
            }
        }
    }

    @Test
    fun activeDepthDestructionAndFailedStateRestorationAreFatal() {
        val failures = mapOf(
            "active" to "active render pass",
            "restore" to "depth mask restore failed",
            "bounds-restore" to "depth bounds restore failed",
        )
        for ((kind, expectedFailure) in failures) {
            val marker = File.createTempFile("lethal-depth-$kind", ".marker")
            marker.delete()
            val process = ProcessBuilder(
                File(
                    System.getProperty("java.home"),
                    "bin/java",
                ).absolutePath,
                "-cp",
                System.getProperty("java.class.path"),
                "heckerpowered.render.opengl.OpenGLDepthFatalProbe",
                kind,
                marker.absolutePath,
            ).redirectErrorStream(true).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.exitValue() != 0, output)
            assertTrue(expectedFailure in output, output)
            assertFalse(marker.exists(), output)
        }
    }

}

private class DepthFixture : AutoCloseable {
    val driver = DepthDriver()
    val device = OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler())
    private val textures = mutableListOf<OpenGLTexture>()
    private val pipelines = mutableListOf<OpenGLRenderPipeline>()
    val color = color()
    private val modules = ShaderStage.entries.map { stage ->
        OpenGLShaderModule(
            device,
            ShaderName(stage.ordinal + 1),
            ShaderModuleDescription(
                stage,
                ShaderSource(
                    ShaderLanguage.Glsl,
                    "void main() {}",
                    "depth test",
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
        "depth test",
        resourceInterface = OpenGLShaderInterface(
            emptyList(),
            emptyList(),
            emptyList(),
        ),
    )
    fun color(): OpenGLTexture = (device.createTexture(TextureDescription(
        label = "depth test color",
        width = 64,
        height = 64,
        format = TextureFormat.Rgba8UnsignedNormalized,
        usage = setOf(TextureUsage.ColorAttachment),
    )) as OpenGLTexture).also { textures.add(it) }
    fun depth(format: TextureFormat = TextureFormat.Depth24UnsignedNormalized, usage: Set<TextureUsage> = setOf(TextureUsage.DepthStencilAttachment), width: Int = 64): OpenGLTexture = (device.createTexture(TextureDescription(
        label = "depth test image",
        width = width,
        height = 64,
        format = format,
        usage = usage,
    )) as OpenGLTexture).also { textures.add(it) }
    fun pipeline(format: TextureFormat = TextureFormat.Depth24UnsignedNormalized, compare: CompareFunction = CompareFunction.Less, write: Boolean = true, processing: Boolean = true, depthDeclared: Boolean = true): OpenGLRenderPipeline = (device.createRenderPipeline(RenderPipelineDescription(
        label = "depth test pipeline",
        shaders = stages,
        colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)),
        depthStencil = if (depthDeclared) DepthStencilState(
            format,
            if (processing) DepthState(
                compare,
                write,
            ) else null,
        ) else null,
    )) as OpenGLRenderPipeline).also { pipelines.add(it) }
    fun pass(depth: OpenGLTexture?, load: AttachmentLoadOperation<Float> = AttachmentLoadOperation.Load, stencil: Boolean = false, commands: RenderPass.() -> Unit) {
        val depthView = depth?.let { device.createTextureView(it, TextureViewDescription(aspects = setOf(TextureAspect.Depth))) }
        val depthAttachment = depthView?.let { device.createAttachmentView(it) }
        device.encode("depth test") {
            renderPass(RenderPassDescription(
                label = "depth test pass",
                colorAttachments = listOf(RenderPassAttachment(device.createAttachmentView(device.createTextureView(color, TextureViewDescription())))),
                depthAttachment = depthAttachment?.let { RenderPassAttachment(
                    it,
                    AttachmentOperations(
                        load,
                        AttachmentStoreOperation.Store,
                    ),
                ) },
                stencilAttachment = if (stencil) depthAttachment?.let { RenderPassAttachment(it) } else null,
            ), commands = commands)
        }
    }
    override fun close() = terminateOnFailure {
        pipelines.forEach { it.close() }
        stages.close()
        modules.forEach { it.close() }
        textures.forEach { it.close() }
        device.close()
        driver.base.device.close()
    }
}

private class DepthDriver {
    val base = DrawStateDriver(true)
    var depthSupported = true
    var floatSupported = true
    var boundsSupported = false
    var substitutedFormat: Int? = null
    var depthBits: Int? = null
    var failure: String? = null
    var fatalRestore = false
    var fatalBoundsRestore = false
    val boundsCalls = mutableListOf<String>()
    var allocations = 0
    var clearDepth = 0.375
    val deleted = mutableListOf<Int>()
    val clears = mutableListOf<Double>()
    private val storage = mutableMapOf<Int, List<Int>>()
    private fun fail(name: String) { if (failure == name) { failure = null; base.nativeError = 0x0502 } }
    private fun forward(method: java.lang.reflect.Method, arguments: Array<out Any?>): Any? = try {
        method.invoke(base.functions, *arguments)
    } catch (failure: InvocationTargetException) { throw failure.targetException }
    private val framebuffers = Proxy.newProxyInstance(OpenGLFramebufferFunctions::class.java.classLoader, arrayOf(OpenGLFramebufferFunctions::class.java)) { _, method, raw ->
        val arguments = raw ?: emptyArray()
        fail(method.name.substringBefore('-'))
        try { method.invoke(checkNotNull(base.functions.framebuffers), *arguments) } catch (failure: InvocationTargetException) { throw failure.targetException }
    } as OpenGLFramebufferFunctions
    val functions = Proxy.newProxyInstance(OpenGLFunctions::class.java.classLoader, arrayOf(OpenGLFunctions::class.java)) { _, method, raw ->
        val arguments = raw ?: emptyArray()
        val name = method.name.substringBefore('-')
        val value = when (name) {
            "getSupportsDepthTextures" -> depthSupported
            "getSupportsFloatDepthTextures" -> floatSupported
            "getSupportsDepthBoundsTest" -> boundsSupported
            "getFramebuffers" -> framebuffers
            "createTexture" -> 100 + allocations++
            "textureImage2D" -> { storage[base.textureBinding] = listOf(substitutedFormat ?: arguments[1] as Int, arguments[2] as Int, arguments[3] as Int); null }
            "getTextureLevelParameter" -> when (arguments[1]) {
                0x1000 -> storage.getValue(base.textureBinding)[1]
                0x1001 -> storage.getValue(base.textureBinding)[2]
                0x1003 -> storage.getValue(base.textureBinding)[0]
                0x884A -> depthBits ?: if (storage.getValue(base.textureBinding)[0] == 0x81A6) 24 else 32
                else -> error("Unexpected storage query")
            }
            "deleteTexture" -> { deleted.add(arguments[0] as Int); null }
            "isEnabled", "enable", "disable" -> {
                if (arguments[0] == 0x8890) {
                    check(boundsSupported) { "Depth-bounds token used without its extension" }
                    boundsCalls.add(name)
                    if (fatalBoundsRestore && name == "enable") throw AssertionError("depth bounds restore failed")
                    fail("bounds:$name")
                }
                forward(method, arguments)
            }
            "depthMask" -> {
                val result = forward(method, arguments)
                if (fatalRestore && arguments[0] == false) throw AssertionError("depth mask restore failed")
                result
            }
            "clearDepth" -> { clearDepth = arguments[0] as Double; null }
            "clear" -> { assertEquals(0x100, arguments[0]); assertTrue(base.depthWrite); clears.add(clearDepth); null }
            "getDoubles" -> if (arguments[0] == 0x0B73) { (arguments[1] as DoubleBuffer).put(0, clearDepth); null } else forward(method, arguments)
            else -> forward(method, arguments)
        }
        fail(name)
        value
    } as OpenGLFunctions
    fun snapshot(): List<Any> = listOf(base.snapshot(), clearDepth)
}

object OpenGLDepthFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { File(arguments[1]).writeText("hook ran") })
        DepthFixture().use { fixture ->
            val depth = fixture.depth()
            if (arguments[0] == "restore") fixture.driver.fatalRestore = true
            val pipeline = if (arguments[0] == "bounds-restore") {
                fixture.driver.boundsSupported = true
                fixture.driver.base.enables[0x8890] = true
                fixture.driver.fatalBoundsRestore = true
                fixture.pipeline()
            } else null
            fixture.pass(depth, AttachmentLoadOperation.Clear(1f)) {
                if (arguments[0] == "active") depth.close()
                if (pipeline != null) bindPipeline(pipeline)
            }
        }
        error("Fatal boundary returned")
    }
}
