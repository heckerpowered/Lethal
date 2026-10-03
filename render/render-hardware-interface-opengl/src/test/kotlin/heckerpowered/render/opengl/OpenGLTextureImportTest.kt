/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.pass.*
import heckerpowered.render.opengl.function.*
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.lifetime
import heckerpowered.render.resource.texture.*
import heckerpowered.render.terminateOnFailure
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.test.*

class OpenGLTextureImportTest {
    @Test
    fun importedColorAndDepthUseExistingViewsAndPassesWithoutAllocatingOrDeletingHostStorage() {
        ImportFixture().use { fixture ->
            for (format in listOf(TextureFormat.Depth24UnsignedNormalized, TextureFormat.Depth32UnsignedNormalized, TextureFormat.Depth32Float)) {
                val color = fixture.imported(fixture.host(TextureFormat.Rgba8UnsignedNormalized))
                val depth = fixture.imported(fixture.host(format), format)
                val colorAlias = fixture.imported(color.name)
                val depthAlias = fixture.imported(depth.name, format)
                val before = fixture.driver.snapshot()
                fixture.pass(color, depth) {
                    assertTrue(fixture.device.isActiveAttachment(colorAlias))
                    assertTrue(fixture.device.isActiveAttachment(depthAlias))
                }
                assertEquals(before, fixture.driver.snapshot())
                assertTrue(fixture.functions.deleted.isEmpty())
                assertEquals(0, fixture.functions.allocations)
                assertEquals(0, fixture.functions.parameterWrites)
            }
        }
    }

    @Test
    fun closingImportedLifetimeInvalidatesItsViewsButLeavesNativeReleaseToTheHost() {
        ImportFixture().use { fixture ->
            val name = fixture.host(TextureFormat.Rgba8UnsignedNormalized)
            val imported = fixture.device.importTexture(name, fixture.description())
            val lifetime = ResourceLifetime.build {
                register(imported)
                this
            }
            val view = fixture.device.createTextureView(imported, TextureViewDescription())
            lifetime.close()
            lifetime.close()
            assertTrue(fixture.functions.isTexture(name))
            assertTrue(fixture.functions.deleted.isEmpty())
            assertFailsWith<IllegalStateException> { fixture.device.createAttachmentView(view) }
            fixture.functions.deleteTexture(name)
            assertEquals(listOf(name), fixture.functions.deleted)
            assertFalse(fixture.functions.isTexture(name))
        }
    }

    @Test
    fun nativeMetadataMismatchAndUnsupportedStorageRejectWithoutChangingTheHost() {
        ImportFixture().use { fixture ->
            val name = fixture.host(TextureFormat.Rgba8UnsignedNormalized)
            val before = fixture.driver.textureBinding
            assertFailsWith<IllegalArgumentException> { fixture.device.importTexture(TextureName.None, fixture.description()) }
            assertFailsWith<IllegalArgumentException> { fixture.device.importTexture(TextureName(999), fixture.description()) }
            val generated = fixture.functions.createTexture()
            assertFailsWith<IllegalArgumentException> { fixture.device.importTexture(generated, fixture.description()) }
            assertFailsWith<IllegalArgumentException> { fixture.device.importTexture(name, fixture.description(width = 32)) }
            assertFailsWith<IllegalArgumentException> { fixture.device.importTexture(name, fixture.description(format = TextureFormat.Depth24UnsignedNormalized)) }
            assertFailsWith<UnsupportedOperationException> { fixture.device.importTexture(name, fixture.description(samples = SampleCount.Four)) }
            fixture.functions.levels.getValue(name)[2] = NativeLevel(0x8058, 16, 16)
            assertFailsWith<IllegalArgumentException> { fixture.device.importTexture(name, fixture.description()) }
            fixture.functions.levels.getValue(name).remove(2)
            fixture.functions.wrongTarget = name
            assertFailsWith<OpenGLOperationException> { fixture.device.importTexture(name, fixture.description()) }
            fixture.functions.wrongTarget = null
            assertEquals(before, fixture.driver.textureBinding)
            assertTrue(fixture.functions.deleted.isEmpty())
            assertEquals(1, fixture.functions.allocations)
            assertEquals(0, fixture.functions.parameterWrites)
        }
    }

    @Test
    fun depthPrecisionAndContextAccessAreCheckedBeforeReturningASelection() {
        ImportFixture().use { fixture ->
            val name = fixture.host(TextureFormat.Depth24UnsignedNormalized)
            fixture.functions.levels.getValue(name)[0] = NativeLevel(0x81A6, 64, 64, 32)
            assertFailsWith<IllegalArgumentException> { fixture.device.importTexture(name, fixture.description(format = TextureFormat.Depth24UnsignedNormalized)) }
            fixture.functions.accessAllowed = false
            assertFailsWith<IllegalStateException> { fixture.device.importTexture(name, fixture.description(format = TextureFormat.Depth24UnsignedNormalized)) }
            fixture.functions.accessAllowed = true
            assertTrue(fixture.functions.deleted.isEmpty())
        }
    }

    @Test
    fun failedNativeQueriesRestoreBindingsAndKeepTheOriginalFailure() {
        ImportFixture().use { fixture ->
            val name = fixture.host(TextureFormat.Rgba8UnsignedNormalized)
            val before = fixture.driver.snapshot()
            val failure = AssertionError("native texture query")
            fixture.functions.queryFailure = failure
            assertSame(failure, assertFailsWith<AssertionError> { fixture.device.importTexture(name, fixture.description()) })
            assertEquals(before, fixture.driver.snapshot())
            assertTrue(fixture.functions.deleted.isEmpty())
            assertEquals(0, fixture.functions.allocations)
            assertEquals(0, fixture.functions.parameterWrites)
        }
    }

    @Test
    fun physicalAliasesRejectCopyAndImportsDuringEncodingBeforeNativeWork() {
        ImportFixture().use { fixture ->
            val owned = fixture.device.createTexture(fixture.description()).lifetime(fixture.lifetime) as OpenGLTexture
            val imported = fixture.imported(owned.name)
            val second = fixture.imported(owned.name)
            assertTrue(owned.hasSameStorage(imported))
            assertTrue(imported.hasSameStorage(second))
            for ((source, destination) in listOf(owned to imported, imported to second)) {
                assertFailsWith<UnsupportedOperationException> {
                    fixture.device.encode("alias copy") { copyTexture(ImageRegion.Texture(source), ImageRegion.Texture(destination)) }
                }
            }
            fixture.device.encode("import during encoding") {
                assertFailsWith<IllegalStateException> { fixture.device.importTexture(owned.name, fixture.description()) }
            }
            imported.close()
            second.close()
            assertTrue(fixture.functions.deleted.isEmpty())
            val ownedName = owned.name
            owned.close()
            assertEquals(listOf(ownedName), fixture.functions.deleted)
        }
    }

    @Test
    fun attachmentSelectionsCannotGrantMissingTransferPermissions() {
        ImportFixture().use { fixture ->
            val name = fixture.host(TextureFormat.Rgba8UnsignedNormalized)
            val description = TextureDescription(
                label = "attachment only",
                width = 64,
                height = 64,
                format = TextureFormat.Rgba8UnsignedNormalized,
                usage = setOf(TextureUsage.ColorAttachment),
            )
            val imported = fixture.device.importTexture(name, description).lifetime(fixture.lifetime)
            val attachment = fixture.device.createAttachmentView(fixture.device.createTextureView(imported, TextureViewDescription()))
            val owned = fixture.device.createTexture(fixture.description()).lifetime(fixture.lifetime)
            val before = fixture.driver.snapshot()
            assertFailsWith<IllegalArgumentException> {
                fixture.device.encode("attachment source permissions") { copyTexture(ImageRegion.Attachment(attachment), ImageRegion.Texture(owned)) }
            }
            assertFailsWith<IllegalArgumentException> {
                fixture.device.encode("attachment destination permissions") { copyTexture(ImageRegion.Texture(owned), ImageRegion.Attachment(attachment)) }
            }
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun hostRecreationRequiresANewSelectionAndNeverRetargetsClosedViews() {
        ImportFixture().use { fixture ->
            val name = fixture.host(TextureFormat.Rgba8UnsignedNormalized)
            val old = fixture.imported(name)
            val oldView = fixture.device.createTextureView(old, TextureViewDescription())
            old.close()
            fixture.functions.levels.getValue(name)[0] = NativeLevel(0x8058, 32, 32)
            val replacement = fixture.device.importTexture(name, fixture.description(width = 32, height = 32)).lifetime(fixture.lifetime)
            assertEquals(32, replacement.width)
            assertFailsWith<IllegalStateException> { fixture.device.createAttachmentView(oldView) }
            assertTrue(fixture.functions.deleted.isEmpty())
        }
    }

    @Test
    fun importedAliasDestructionDuringColorOrDepthUseRemainsFatal() {
        for (kind in listOf("color", "depth")) {
            val marker = File.createTempFile("lethal-import-$kind", ".marker")
            marker.delete()
            val process = ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").absolutePath,
                "-cp",
                System.getProperty("java.class.path"),
                "heckerpowered.render.opengl.OpenGLTextureImportFatalProbe",
                kind,
                marker.absolutePath,
            ).redirectErrorStream(true).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.exitValue() != 0, output)
            assertTrue("active render pass" in output, output)
            assertFalse(marker.exists(), output)
        }
    }

}

private data class NativeLevel(
    val format: Int,
    val width: Int,
    val height: Int,
    val depthBits: Int = 0,
)

private class ImportFunctions(val driver: DrawStateDriver) : OpenGLFunctions by driver.functions {
    val levels = mutableMapOf<TextureName, MutableMap<Int, NativeLevel>>()
    val allocated = mutableListOf<TextureName>()
    val deleted = mutableListOf<TextureName>()
    var allocations = 0
    var parameterWrites = 0
    var accessAllowed = true
    var wrongTarget: TextureName? = null
    var queryFailure: AssertionError? = null
    private var nextName = 100

    override val supportsDepthTextures = true
    override val supportsFloatDepthTextures = true

    override fun checkCurrentContext() {
        check(accessAllowed) { "The original OpenGL context is not current" }
    }

    override fun createTexture(): TextureName = TextureName(nextName++).also {
        allocations++
        allocated += it
    }
    override fun isTexture(texture: TextureName): Boolean = texture in levels

    override fun bindTexture2D(texture: TextureName) {
        if (texture == wrongTarget) {
            driver.nativeError = 0x0502
            return
        }
        driver.functions.bindTexture2D(texture)
        if (texture in allocated && texture !in levels) levels[texture] = mutableMapOf()
    }

    override fun textureImage2D(level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteBuffer?) {
        val name = TextureName(driver.textureBinding)
        val depthBits = when (internalFormat) {
            0x81A6 -> 24
            0x81A7, 0x8CAC -> 32
            else -> 0
        }
        levels.getValue(name)[level] = NativeLevel(internalFormat, width, height, depthBits)
    }

    override fun getTextureLevelParameter(level: Int, parameter: Int): Int {
        queryFailure?.let { failure ->
            queryFailure = null
            throw failure
        }
        val image = levels[TextureName(driver.textureBinding)]?.get(level) ?: return 0
        return when (parameter) {
            0x1000 -> image.width
            0x1001 -> image.height
            0x1003 -> image.format
            0x884A -> image.depthBits
            else -> error("Unexpected texture parameter $parameter")
        }
    }

    override fun textureParameter(parameter: Int, value: Int) {
        parameterWrites++
    }
    override fun deleteTexture(texture: TextureName) {
        deleted += texture
        levels.remove(texture)
    }
}

private class ImportFixture : AutoCloseable {
    val driver = DrawStateDriver(true)
    val functions = ImportFunctions(driver)
    val device = OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler())
    val lifetime = ResourceLifetime.build { this }

    fun host(format: TextureFormat): TextureName {
        val name = TextureName(300 + functions.levels.size)
        val native = when (format) {
            TextureFormat.Rgba8UnsignedNormalized -> NativeLevel(0x8058, 64, 64)
            TextureFormat.Depth24UnsignedNormalized -> NativeLevel(0x81A6, 64, 64, 24)
            TextureFormat.Depth32UnsignedNormalized -> NativeLevel(0x81A7, 64, 64, 32)
            TextureFormat.Depth32Float -> NativeLevel(0x8CAC, 64, 64, 32)
            else -> error("Unsupported fixture format")
        }
        functions.levels[name] = mutableMapOf(0 to native)
        return name
    }

    fun description(format: TextureFormat = TextureFormat.Rgba8UnsignedNormalized, width: Int = 64, height: Int = 64, samples: SampleCount = SampleCount.One): TextureDescription = TextureDescription(
        label = "host image",
        width = width,
        height = height,
        format = format,
        usage = if (format.hasDepth) setOf(TextureUsage.DepthStencilAttachment) else setOf(TextureUsage.ColorAttachment, TextureUsage.Sampled, TextureUsage.TransferSource, TextureUsage.TransferDestination),
        sampleCount = samples,
    )

    fun imported(name: TextureName, format: TextureFormat = TextureFormat.Rgba8UnsignedNormalized): OpenGLTexture =
        device.importTexture(name, description(format)).lifetime(lifetime) as OpenGLTexture

    fun pass(color: OpenGLTexture, depth: OpenGLTexture? = null, commands: RenderPass.() -> Unit) {
        val colorAttachment = device.createAttachmentView(device.createTextureView(color, TextureViewDescription()))
        val depthAttachment = depth?.let { device.createAttachmentView(device.createTextureView(it, TextureViewDescription(aspects = setOf(TextureAspect.Depth)))) }
        device.encode("import pass") {
            renderPass(RenderPassDescription(
                label = "import pass",
                renderArea = RenderArea(0, 0, color.width, color.height),
                colorAttachments = listOf(RenderPassAttachment(colorAttachment)),
                depthAttachment = depthAttachment?.let { RenderPassAttachment(it) },
            ), RenderPassResources.Empty, commands)
        }
    }

    override fun close() = terminateOnFailure {
        lifetime.close()
        device.close()
        driver.device.close()
    }
}

object OpenGLTextureImportFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { File(arguments[1]).writeText("hook ran") })
        ImportFixture().use { fixture ->
            val color = fixture.imported(fixture.host(TextureFormat.Rgba8UnsignedNormalized))
            val depth = fixture.imported(fixture.host(TextureFormat.Depth24UnsignedNormalized), TextureFormat.Depth24UnsignedNormalized)
            val alias = if (arguments[0] == "color") fixture.imported(color.name) else fixture.imported(depth.name, TextureFormat.Depth24UnsignedNormalized)
            fixture.pass(color, depth) { alias.close() }
        }
        error("Fatal boundary returned")
    }
}
