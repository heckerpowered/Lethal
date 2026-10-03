/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.shader.ShaderLanguage
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderStage
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.net.URLClassLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OpenGLCanonicalShaderCompilerTest {
    @Test
    fun delegatesOriginalDescriptionOriginAndIncludeGenerationWithoutCreatingGpuObjects() {
        val driver = CompilationDriver()
        val compiler = RecordingCompiler()
        OpenGLGraphicsDevice(
            driver.functions,
            canonicalShaderCompiler = compiler,
        ).use { device ->
            val description = description().copy(entryPoint = "custom-entry")
            val includes = linkedMapOf("shared/constants.glsl" to "const float value = 1.0;")
            val expected = compiler.result
            assertSame(expected, device.compileCanonicalShader(description, "shaders/root.vert", includes))
            assertSame(description, compiler.description)
            assertEquals("shaders/root.vert", compiler.origin)
            assertSame(includes, compiler.includes)
            assertTrue(driver.nativeCalls.isEmpty())
        }
        assertEquals(1, compiler.closes)
    }

    @Test
    fun repeatedCompilationsUseInstalledCompilerWithoutCreatingGpuObjects() {
        val driver = CompilationDriver()
        val compiler = RecordingCompiler()
        OpenGLGraphicsDevice(
            driver.functions,
            canonicalShaderCompiler = compiler,
        ).use { device ->
            repeat(2) {
                assertSame(compiler.result, device.compileCanonicalShader(description(), "root.vert"))
            }
            assertEquals(2, compiler.calls)
            assertEquals(emptyMap(), compiler.includes)
            assertTrue(driver.nativeCalls.isEmpty())
        }
        assertEquals(1, compiler.closes)
    }

    @Test
    fun wrongContextAndThreadRejectBeforeCallingCompiler() {
        val driver = CompilationDriver()
        val compiler = RecordingCompiler()
        OpenGLGraphicsDevice(
            driver.functions,
            canonicalShaderCompiler = compiler,
        ).use { device ->
            driver.current = false
            assertFailsWith<IllegalStateException> { device.compileCanonicalShader(description(), "root.vert") }
            driver.current = true
            val failure = AtomicReference<Throwable>()
            Thread {
                try {
                    device.compileCanonicalShader(description(), "root.vert")
                } catch (caught: IllegalStateException) {
                    failure.set(caught)
                }
            }.apply { start(); join() }
            assertTrue(failure.get() is IllegalStateException)
            assertEquals(0, compiler.calls)
        }
    }

    @Test
    fun deviceCloseReleasesCompilerOnceAndClosedDeviceRejectsFurtherCompilation() {
        val driver = CompilationDriver()
        val compiler = RecordingCompiler()
        val device = OpenGLGraphicsDevice(
            driver.functions,
            canonicalShaderCompiler = compiler,
        )
        device.close()
        device.close()
        assertEquals(1, compiler.closes)
        assertFailsWith<IllegalStateException> { device.compileCanonicalShader(description(), "root.vert") }
        assertEquals(0, compiler.calls)
    }

    @Test
    fun failedDeviceConstructionLeavesCompilerOwnedByAssembly() {
        val driver = CompilationDriver().apply { hasFramebuffers = false }
        val compiler = RecordingCompiler()
        assertFailsWith<UnsupportedOperationException> {
            OpenGLGraphicsDevice(
                driver.functions,
                canonicalShaderCompiler = compiler,
            )
        }
        assertEquals(0, compiler.closes)
        compiler.close()
        assertEquals(1, compiler.closes)
    }

    @Test
    fun compilationFailurePropagatesWithoutClosingCapabilityOrPreventingRetry() {
        val driver = CompilationDriver()
        val compiler = RecordingCompiler()
        OpenGLGraphicsDevice(
            driver.functions,
            canonicalShaderCompiler = compiler,
        ).use { device ->
            val failure = IllegalArgumentException("unsupported canonical shape")
            compiler.failure = failure
            assertSame(failure, assertFailsWith<IllegalArgumentException> { device.compileCanonicalShader(description(), "root.vert") })
            assertEquals(0, compiler.closes)
            compiler.failure = null
            assertSame(compiler.result, device.compileCanonicalShader(description(), "root.vert"))
            assertEquals(2, compiler.calls)
            assertTrue(driver.nativeCalls.isEmpty())
        }
    }

    @Test
    fun compilerReleaseFailureHaltsWithoutRunningShutdownHooks() {
        val entries = mutableSetOf<String>()
        var loader: ClassLoader? = javaClass.classLoader
        while (loader != null) {
            if (loader is URLClassLoader) entries.addAll(loader.getURLs().map { File(it.toURI()).path })
            loader = loader.parent
        }
        entries.addAll(System.getProperty("java.class.path").split(File.pathSeparator))
        val process = ProcessBuilder(
            File(
                System.getProperty("java.home"),
                "bin/java",
            ).path,
            "-cp",
            entries.joinToString(File.pathSeparator),
            "heckerpowered.render.opengl.CanonicalCompilerReleaseFailureProbe",
        ).redirectErrorStream(true).start()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Compiler release probe did not terminate")
        }
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(1, process.exitValue(), output)
        assertTrue(output.contains("device-created"), output)
        assertTrue(output.contains("canonical compiler release failed"), output)
        assertTrue(!output.contains("shutdown hook ran"), output)
    }

    @Test
    fun compilationDuringEncodingKeepsExistingDeviceAccessContract() {
        val driver = CompilationDriver()
        val compiler = RecordingCompiler()
        OpenGLGraphicsDevice(
            driver.functions,
            canonicalShaderCompiler = compiler,
        ).use { device ->
            device.encode("compilation") {
                assertSame(compiler.result, device.compileCanonicalShader(description(), "root.vert"))
            }
            assertEquals(1, compiler.calls)
            assertEquals(listOf("flush"), driver.nativeCalls)
        }
    }
}

internal object CanonicalCompilerReleaseFailureProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown hook ran") })
        val compiler = RecordingCompiler().apply { closeFailure = AssertionError("canonical compiler release failed") }
        val device = OpenGLGraphicsDevice(
            CompilationDriver().functions,
            canonicalShaderCompiler = compiler,
        )
        println("device-created")
        device.close()
        error("Compiler release failure returned")
    }
}

private fun description(): ShaderModuleDescription = ShaderModuleDescription(
    ShaderStage.Vertex,
    ShaderSource(
        ShaderLanguage.Glsl,
        "#version 450\nvoid main() {}",
        "canonical text",
    ),
)

private class CompilationDriver {
    private val thread = Thread.currentThread()
    var current = true
    var hasFramebuffers = true
    val nativeCalls = mutableListOf<String>()
    private val framebuffers = Proxy.newProxyInstance(OpenGLFramebufferFunctions::class.java.classLoader, arrayOf(OpenGLFramebufferFunctions::class.java)) { _, method, _ ->
        error("Unexpected framebuffer operation ${method.name}")
    } as OpenGLFramebufferFunctions
    val functions = Proxy.newProxyInstance(OpenGLFunctions::class.java.classLoader, arrayOf(OpenGLFunctions::class.java)) { _, method, _ ->
        when (method.name) {
            "checkCurrentContext" -> { check(current && Thread.currentThread() === thread); null }
            "getError" -> 0
            "getFramebuffers" -> if (hasFramebuffers) framebuffers else null
            "flush" -> { nativeCalls.add("flush"); null }
            else -> { nativeCalls.add(method.name); error("Unexpected native operation ${method.name}") }
        }
    } as OpenGLFunctions
}
