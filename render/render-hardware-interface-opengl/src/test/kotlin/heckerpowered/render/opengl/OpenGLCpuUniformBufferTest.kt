/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.opengl

import heckerpowered.render.memory.directBufferAddress
import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.resource.buffer.*
import heckerpowered.render.resource.buffer.BufferUsage
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class OpenGLCpuUniformBufferTest {
    @Test
    fun ownedCpuStorageUploadsAndCopiesExactViewBytesWithoutNativeAllocation() {
        val driver = DrawStateDriver(true)
        val functions = withoutUniformBuffers(driver.functions)
        OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler()).use { device ->
            val usage = setOf(BufferUsage.Uniform, BufferUsage.TransferSource, BufferUsage.TransferDestination)
            val first = device.createBuffer(BufferDescription("first CPU uniform", 64, usage)) as OpenGLBuffer
            val second = device.createBuffer(BufferDescription("second CPU uniform", 64, usage)) as OpenGLBuffer
            try {
                val source = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).putFloat(0, 0.25f).putFloat(4, 0.75f)
                device.encode("CPU upload and copy") {
                    writeBuffer(GpuBufferView(first, 7, 8), directBufferAddress(source))
                    copyBuffer(GpuBufferView(first, 7, 8), GpuBufferView(second, 11, 8))
                    copyBuffer(GpuBufferView(second, 11, 8), GpuBufferView(second, 32, 8))
                }
                val bytes = checkNotNull(second.uniformBytes)
                assertEquals(0.25f, bytes.getFloat(11))
                assertEquals(0.75f, bytes.getFloat(15))
                assertEquals(0.25f, bytes.getFloat(32))
                assertEquals(0, bytes.position())
                assertEquals(64, bytes.limit())
                assertEquals(0, driver.bufferCount)
                assertFailsWith<IllegalArgumentException> {
                    device.encode("overlapping CPU copy") { copyBuffer(GpuBufferView(second, 32, 8), GpuBufferView(second, 36, 8)) }
                }
                assertFailsWith<IllegalStateException> { first.name }
            } finally {
                second.close()
                first.close()
            }
            assertFailsWith<IllegalStateException> { first.uniformBytes }
        }
    }

    @Test
    fun missingUboRejectsMixedGeometryUsageAndOversizedCpuStorageBeforeAllocation() {
        val driver = DrawStateDriver(true)
        OpenGLGraphicsDevice(withoutUniformBuffers(driver.functions), canonicalShaderCompiler = RecordingCompiler()).use { device ->
            for (role in listOf(BufferUsage.Vertex, BufferUsage.Index)) {
                assertFailsWith<UnsupportedOperationException> { device.createBuffer(BufferDescription("mixed", 64, setOf(BufferUsage.Uniform, role))) }
            }
            assertFailsWith<UnsupportedOperationException> { device.createBuffer(BufferDescription("large CPU", Int.MAX_VALUE.toLong() + 1, setOf(BufferUsage.Uniform))) }
            assertEquals(0, driver.bufferCount)
        }
        OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler()).use { device ->
            val native = device.createBuffer(BufferDescription("native mixed", 64, setOf(BufferUsage.Uniform, BufferUsage.Vertex))) as OpenGLBuffer
            try { assertNull(native.uniformBytes); assertTrue(native.name != BufferName.None) } finally { native.close() }
        }
    }

    @Test
    fun cpuStorageRetainsDeviceAndContextChecks() {
        val driver = DrawStateDriver(true)
        OpenGLGraphicsDevice(withoutUniformBuffers(driver.functions), canonicalShaderCompiler = RecordingCompiler()).use { device ->
            val buffer = device.createBuffer(BufferDescription("thread CPU", 4, setOf(BufferUsage.Uniform))) as OpenGLBuffer
            try {
                var failure: Throwable? = null
                val thread = Thread { try { buffer.checkOpen() } catch (caught: IllegalStateException) { failure = caught } }
                thread.start(); thread.join()
                assertNotNull(failure)
            } finally { buffer.close() }
        }
    }
}

private fun withoutUniformBuffers(base: OpenGLFunctions): OpenGLFunctions {
    val ownerThread = Thread.currentThread()
    return Proxy.newProxyInstance(
    OpenGLFunctions::class.java.classLoader, arrayOf(OpenGLFunctions::class.java),
) { _, method, arguments ->
    if (method.name == "checkCurrentContext") check(Thread.currentThread() === ownerThread)
    if (method.name == "getUniformBuffers") null else try {
        method.invoke(base, *(arguments ?: emptyArray()))
    } catch (failure: InvocationTargetException) { throw failure.cause ?: failure }
} as OpenGLFunctions
}
