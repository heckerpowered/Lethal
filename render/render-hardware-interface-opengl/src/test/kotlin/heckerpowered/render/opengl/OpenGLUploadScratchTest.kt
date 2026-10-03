/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.TextureDataLayout
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.asView
import heckerpowered.render.resource.texture.*
import java.lang.reflect.Proxy
import java.nio.FloatBuffer
import kotlin.test.*

class OpenGLUploadScratchTest {
    @Test
    fun legacyTextureUploadUsesIndependentQueryScratchWhenSourceFillsEncoderStack() {
        upload(false)
    }

    @Test
    fun failedLegacyTextureUploadRestoresHostStateAndKeepsFullStackSourceIntact() {
        upload(true)
    }

    private fun upload(fail: Boolean) {
        val driver = UploadDriver()
        val texture = driver.device.createTexture(TextureDescription("upload", 1, 1, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.TransferDestination)))
        driver.calls.clear()
        val stores = driver.stores.toMap()
        val transfers = driver.transfers.toMap()
        val failure = IllegalStateException("row failed")
        var rowCalls = 0
        val encode = { driver.device.encode("full source stack") {
            val stack = memoryStack
            stack.frame {
                val source = reserve(4, 4)
                val bytes = asByteBuffer(source, 4)
                bytes.putInt(0, 0x12345678)
                val end = reserve(0, 1)
                driver.row = { arguments ->
                    rowCalls++
                    assertEquals(listOf<Any?>(0, 0, 0, 1, 1, 0x1908, 0x1401, source.rawValue), arguments.toList())
                    assertEquals(end, stack.frame { reserve(0, 1) })
                    assertEquals(0x12345678, bytes.getInt(0))
                    if (fail) throw failure
                }
                try {
                    writeTexture(ImageRegion.Texture(texture), source, TextureDataLayout(4))
                } finally {
                    assertEquals(end, reserve(0, 1))
                    assertEquals(0x12345678, bytes.getInt(0))
                }
            }
        } }
        if (fail) assertSame(failure, assertFailsWith<IllegalStateException> { encode() }) else encode()
        assertEquals(1, rowCalls)
        assertEquals(stores, driver.stores)
        assertEquals(transfers, driver.transfers)
        assertEquals(TextureName(41), driver.textureBinding)
        assertEquals(listOf("bindTexture:7", "bindTexture:41"), driver.calls.filter { it.startsWith("bindTexture:") })
        assertEquals(listOf(0x0D14, 0x0D15, 0x0D18, 0x0D19, 0x0D1A, 0x0D1B, 0x0D1C, 0x0D1D).map { "float:$it" }, driver.calls.filter { it.startsWith("float:") })
        assertEquals(12, driver.calls.count { it.startsWith("store:") })
        assertEquals(18, driver.calls.count { it.startsWith("transfer:") })
        texture.close()
        driver.device.close()
    }

    @Test
    fun bufferUploadConsumesFullStackSourceWithoutAdditionalScratch() {
        val driver = UploadDriver()
        val buffer = driver.device.createBuffer(BufferDescription("upload", 4, setOf(BufferUsage.TransferDestination)))
        driver.calls.clear()
        driver.device.encode("full buffer stack") {
            val stack = memoryStack
            stack.frame {
                val source = reserve(4, 4)
                val bytes = asByteBuffer(source, 4).putInt(0, 99)
                val end = reserve(0, 1)
                driver.bufferUpload = { arguments ->
                    assertEquals(source.rawValue, arguments[3])
                    assertEquals(4L, arguments[2])
                    assertEquals(99, bytes.getInt(0))
                    assertEquals(end, stack.frame { reserve(0, 1) })
                }
                writeBuffer(buffer.asView(), source)
                assertEquals(end, reserve(0, 1))
            }
        }
        assertEquals(listOf("bindBuffer:8", "bufferUpload", "bindBuffer:42"), driver.calls.filter { it.startsWith("bindBuffer:") || it == "bufferUpload" })
        buffer.close()
        driver.device.close()
    }
}

private class UploadDriver {
    val calls = mutableListOf<String>()
    val stores = (0x0CF0..0x0CF5).associateWith { 3 }.toMutableMap().apply { put(0x0CF0, 1); put(0x0CF1, 1); put(0x0CF5, 8) }
    val transfers = listOf(0x0D14, 0x0D15, 0x0D18, 0x0D19, 0x0D1A, 0x0D1B, 0x0D1C, 0x0D1D).associateWith { 0.25f }.toMutableMap().apply { put(0x0D10, 1f) }
    var textureBinding = TextureName(41)
    var bufferBinding = BufferName(42)
    var row: (Array<out Any?>) -> Unit = { error("No upload expected") }
    var bufferUpload: (Array<out Any?>) -> Unit = { error("No buffer upload expected") }
    private val framebuffers = uploadProxy<OpenGLFramebufferFunctions> { name, _ -> error("Unexpected framebuffer call: $name") }
    private val functions = uploadProxy<OpenGLFunctions> { name, arguments ->
        val args = arguments ?: emptyArray()
        when (name) {
            "checkCurrentContext", "flush", "deleteTexture", "deleteBuffer", "textureParameter", "textureImage2D", "bufferData" -> null
            "getFramebuffers" -> framebuffers
            "getError" -> 0
            "getSupportsNonPowerOfTwoTextures", "getSupportsLegacyPixelTransfer" -> true
            "getSupportsPixelBuffers" -> false
            "getMaximumBufferSizeBytes" -> Long.MAX_VALUE
            "createTexture" -> 7
            "createBuffer" -> 8
            "getBoundBuffer" -> bufferBinding.value
            "bindBuffer" -> { bufferBinding = BufferName(args[1] as Int); calls.add("bindBuffer:${bufferBinding.value}"); null }
            "bufferSubData" -> { calls.add("bufferUpload"); bufferUpload(args); null }
            "getInteger" -> if (args[0] == 0x0D33) 4096 else if (args[0] == 0x0D10) transfers.getValue(0x0D10).toInt() else stores.getValue(args[0] as Int)
            "getBoundTexture2D" -> textureBinding.value
            "bindTexture2D" -> { textureBinding = TextureName(args[0] as Int); calls.add("bindTexture:${textureBinding.value}"); null }
            "getTextureLevelParameter" -> if (args[1] == 0x1003) 0x8058 else 1
            "getFloats" -> { val parameter = args[0] as Int; calls.add("float:$parameter"); (args[1] as FloatBuffer).put(0, transfers.getValue(parameter)); null }
            "pixelStore" -> { stores[args[0] as Int] = args[1] as Int; calls.add("store:${args[0]}"); null }
            "pixelTransfer" -> { transfers[args[0] as Int] = args[1] as Float; calls.add("transfer:${args[0]}"); null }
            "textureSubImage2D" -> { row(args); null }
            else -> error("Unexpected native call: $name")
        }
    }
    val device = OpenGLGraphicsDevice(functions, MemoryStack(4), canonicalShaderCompiler = RecordingCompiler())
}

private inline fun <reified T> uploadProxy(crossinline invoke: (String, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments -> invoke(method.name.substringBefore('-'), arguments) } as T
