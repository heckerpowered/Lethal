/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import heckerpowered.render.resource.texture.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class OpenGLHdrColorTest {
    @Test
    fun nativeProfilePrefersExact16Or32AndCachesWithoutRetainingProbeResources() {
        for (promoted in listOf(false, true)) HdrFixture().use { fixture ->
            fixture.promote16 = promoted
            val before = fixture.driver.snapshot()
            val expected = if (promoted) TextureFormat.Rgba32Float else TextureFormat.Rgba16Float
            assertEquals(expected, fixture.device.preferredHdrColorFormat())
            assertEquals(expected, fixture.device.preferredHdrColorFormat())
            assertEquals(if (promoted) listOf(0x881A, 0x8814) else listOf(0x881A), fixture.requestedFormats)
            assertEquals(fixture.requestedFormats.size, fixture.deletedTextures.size)
            assertEquals(fixture.createdFramebuffers, fixture.deletedFramebuffers)
            assertTrue(fixture.images.isEmpty())
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun absentCapabilityAndIncompleteAttachmentsKeepHonestRgba8Fallback() {
        HdrFixture().use { fixture ->
            fixture.supported = false
            assertEquals(TextureFormat.Rgba8UnsignedNormalized, fixture.device.preferredHdrColorFormat())
            assertTrue(fixture.requestedFormats.isEmpty())
            assertFailsWith<UnsupportedOperationException> { fixture.device.createTexture(fixture.description(TextureFormat.Rgba16Float)) }
        }
        HdrFixture().use { fixture ->
            fixture.incomplete = true
            val before = fixture.driver.snapshot()
            assertEquals(TextureFormat.Rgba8UnsignedNormalized, fixture.device.preferredHdrColorFormat())
            assertEquals(listOf(0x881A, 0x8814), fixture.requestedFormats)
            assertEquals(2, fixture.deletedTextures.size)
            assertEquals(fixture.createdFramebuffers, fixture.deletedFramebuffers)
            assertEquals(before, fixture.driver.snapshot())
            fixture.device.createTexture(fixture.description(TextureFormat.Rgba8UnsignedNormalized)).close()
        }
    }

    @Test
    fun everyFloatComponentFormatAndDimensionMustMatchAndFailedCreationReleasesTheName() {
        for (case in 0..5) HdrFixture().use { fixture ->
            fixture.corrupt = case
            val before = fixture.driver.snapshot()
            val failure = assertFailsWith<UnsupportedOperationException> { fixture.device.createTexture(fixture.description(TextureFormat.Rgba16Float)) }
            assertTrue("requested Rgba16Float" in checkNotNull(failure.message))
            assertTrue("component bits=" in failure.message!!)
            assertTrue("types=" in failure.message!!)
            assertEquals(1, fixture.deletedTextures.size)
            assertTrue(fixture.images.isEmpty())
            assertEquals(before, fixture.driver.snapshot())
        }
    }

    @Test
    fun nativeGlErrorsPropagateInsteadOfBeingReportedAsUnsupportedProfile() {
        HdrFixture().use { fixture ->
            fixture.queryError = true
            val before = fixture.driver.snapshot()
            assertFailsWith<OpenGLOperationException> { fixture.device.preferredHdrColorFormat() }
            assertEquals(listOf(0x881A), fixture.requestedFormats)
            assertEquals(1, fixture.deletedTextures.size)
            assertEquals(before, fixture.driver.snapshot())
            fixture.queryError = false
            assertEquals(TextureFormat.Rgba16Float, fixture.device.preferredHdrColorFormat())
        }
    }

    @Test
    fun floatLinearTransfersRejectBeforeAllocationAndRgba8TransfersRemainAvailable() {
        HdrFixture().use { fixture ->
            for (format in listOf(TextureFormat.Rgba16Float, TextureFormat.Rgba32Float)) {
                for (transfer in listOf(TextureUsage.TransferSource, TextureUsage.TransferDestination)) {
                    assertFailsWith<UnsupportedOperationException> {
                        fixture.device.createTexture(fixture.description(format, setOf(TextureUsage.Sampled, transfer)))
                    }
                }
            }
            assertTrue(fixture.requestedFormats.isEmpty())
            fixture.device.createTexture(fixture.description(TextureFormat.Rgba8UnsignedNormalized, setOf(TextureUsage.TransferDestination))).close()
        }
    }

    @Test
    fun cachedProfileStillChecksContextThreadAndEncodingLifetime() {
        HdrFixture().use { fixture ->
            fixture.device.preferredHdrColorFormat()
            fixture.device.encode("HDR selection scope") {
                assertFailsWith<IllegalStateException> { fixture.device.preferredHdrColorFormat() }
            }
            val failure = AtomicReference<Throwable>()
            Thread { try { fixture.device.preferredHdrColorFormat() } catch (caught: IllegalStateException) { failure.set(caught) } }.apply { start(); join() }
            assertTrue(failure.get() is IllegalStateException)
            fixture.device.close()
            assertFailsWith<IllegalStateException> { fixture.device.preferredHdrColorFormat() }
        }
    }
}

private class HdrFixture : AutoCloseable {
    val driver = DrawStateDriver(true)
    private val thread = Thread.currentThread()
    var supported = true
    var promote16 = false
    var incomplete = false
    var corrupt = -1
    var queryError = false
    val requestedFormats = mutableListOf<Int>()
    val images = mutableMapOf<Int, Image>()
    val deletedTextures = mutableListOf<Int>()
    var createdFramebuffers = 0
    var deletedFramebuffers = 0
    private var nextTexture = 1000
    private val framebuffers = proxy<OpenGLFramebufferFunctions> { method, arguments ->
        when (method.name.substringBefore('-')) {
            "createFramebuffer" -> { createdFramebuffers++; 20 }
            "deleteFramebuffer" -> { deletedFramebuffers++; null }
            "checkFramebufferStatus" -> if (incomplete) 0x8CDD else 0x8CD5
            else -> invoke(method, checkNotNull(driver.functions.framebuffers), arguments)
        }
    }
    val functions = proxy<OpenGLFunctions> { method, arguments ->
        when (method.name.substringBefore('-')) {
            "checkCurrentContext" -> { check(Thread.currentThread() === thread); null }
            "getSupportsFloatColorTextures" -> supported
            "getFramebuffers" -> framebuffers
            "createTexture" -> ++nextTexture
            "deleteTexture" -> { val name = arguments!![0] as Int; deletedTextures += name; images.remove(name); null }
            "isTexture" -> images.containsKey(arguments!![0] as Int)
            "textureImage2D" -> {
                val internal = arguments!![1] as Int
                requestedFormats += internal
                val actual = if (promote16 && internal == 0x881A || corrupt == 4) 0x8814 else internal
                if (internal != 0x8058) assertEquals(0x1406, arguments[5])
                images[driver.textureBinding] = Image(actual, arguments[2] as Int, arguments[3] as Int)
                null
            }
            "getTextureLevelParameter" -> {
                if (arguments!![0] != 0) 0 else {
                    val parameter = arguments[1] as Int
                    val image = checkNotNull(images[driver.textureBinding])
                    when (parameter) {
                        0x1000 -> image.width + if (corrupt == 5) 1 else 0
                        0x1001 -> image.height
                        0x1003 -> image.format
                        in 0x805C..0x805F -> if (corrupt in 0..3 && parameter == 0x805C + corrupt) 8 else if (image.format == 0x8814) 32 else 16
                        in 0x8C10..0x8C13 -> {
                            if (queryError) driver.nativeError = 0x0502
                            if (corrupt == 4) 0x8C17 else 0x1406
                        }
                        else -> error("Unexpected texture query $parameter")
                    }
                }
            }
            else -> invoke(method, driver.functions, arguments)
        }
    }
    val device = OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler())
    fun description(format: TextureFormat, usage: Set<TextureUsage> = setOf(TextureUsage.Sampled, TextureUsage.ColorAttachment)) = TextureDescription("HDR test", 4, 4, format = format, usage = usage)
    override fun close() = terminateOnFailure { device.close(); driver.device.close() }
    data class Image(val format: Int, val width: Int, val height: Int)
}

private inline fun <reified T> proxy(crossinline body: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments -> body(method, arguments) } as T

private fun invoke(method: java.lang.reflect.Method, target: Any, arguments: Array<Any?>?): Any? = try {
    method.invoke(target, *(arguments ?: emptyArray()))
} catch (failure: InvocationTargetException) { throw failure.cause ?: failure }
