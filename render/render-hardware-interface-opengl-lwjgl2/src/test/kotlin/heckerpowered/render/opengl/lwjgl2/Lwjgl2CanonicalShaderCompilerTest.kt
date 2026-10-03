/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.opengl.function.OpenGLFunctionsProvider
import heckerpowered.render.opengl.shader.OpenGLShaderArtifact
import heckerpowered.render.shader.CanonicalShaderCompiler
import heckerpowered.render.shader.ShaderBinary
import heckerpowered.render.shader.ShaderCompilation
import heckerpowered.render.shader.ShaderLanguage
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderStage
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Lwjgl2CanonicalShaderCompilerTest {
    @Test
    fun canonicalCompilationPreservesHostRhiAndSealedLwjgl2Identities() {
        val host = Lwjgl2CanonicalShaderCompilerTest::class.java.classLoader
        val oldBuffer = Class.forName("org.lwjgl.BufferUtils", false, host)
        val oldPointers = Class.forName("org.lwjgl.PointerBuffer", false, host)
        assertTrue(oldBuffer.`package`.isSealed)
        val properties = Properties().apply { putAll(System.getProperties()) }
        createLwjgl2CanonicalShaderCompiler().use { compiler ->
            val privateClasses = compiler.javaClass.classLoader
            assertNotSame(oldBuffer, privateClasses.loadClass("org.lwjgl.BufferUtils"))
            assertNotSame(oldPointers, privateClasses.loadClass("org.lwjgl.PointerBuffer"))
            assertFalse(privateClasses.loadClass("org.lwjgl.BufferUtils").`package`.isSealed)
            assertSame(CanonicalShaderCompiler::class.java, privateClasses.loadClass(CanonicalShaderCompiler::class.java.name))
            assertSame(ShaderCompilation::class.java, privateClasses.loadClass(ShaderCompilation::class.java.name))
            assertFailsWith<ClassNotFoundException> { host.loadClass("org.lwjgl.system.MemoryUtil") }
            val compiled = compile(compiler)
            assertSame(host, compiled.javaClass.classLoader)
            assertSame(host, compiled.reflection.javaClass.classLoader)
            val code = compiled.module.code as ShaderBinary
            assertSame(null, code.bytes.javaClass.classLoader)
            assertTrue(code.bytes.isDirect)
            val artifact = OpenGLShaderArtifact.decode(code.bytes)
            assertEquals(ShaderStage.Vertex, artifact.stage)
            assertTrue(compiled.reflection.inputs.isNotEmpty())
            assertNotSame(OpenGLShaderArtifact::class.java, privateClasses.loadClass(OpenGLShaderArtifact::class.java.name))
        }
        assertTrue(oldBuffer.`package`.isSealed)
        assertEquals(properties, System.getProperties())
    }

    @Test
    fun separateCompilerHandlesReuseTheRuntimeAfterSiblingCleanup() {
        val first = createLwjgl2CanonicalShaderCompiler()
        val second = createLwjgl2CanonicalShaderCompiler()
        val classes = first.javaClass.classLoader
        try {
            assertSame(classes, second.javaClass.classLoader)
            compile(first)
            first.close()
            compile(second)
        } finally {
            first.close()
            second.close()
        }
        createLwjgl2CanonicalShaderCompiler().use { reopened ->
            assertSame(classes, reopened.javaClass.classLoader)
            compile(reopened)
        }
    }

    @Test
    fun rejectedGlslKeepsOriginDiagnosticsAndTheCompilerUsable() {
        createLwjgl2CanonicalShaderCompiler().use { compiler ->
            val description = ShaderModuleDescription(ShaderStage.Vertex, ShaderSource(ShaderLanguage.Glsl, "#version 450\ninvalid shader\n", "invalid"))
            val failure = assertFailsWith<IllegalStateException> { compiler.compile(description, "invalid.vert", emptyMap()) }
            assertSame(null, failure.javaClass.classLoader)
            assertTrue(failure.message.orEmpty().contains("invalid.vert"))
            compile(compiler)
        }
    }

    @Test
    fun missingBundleIdentifiesTheRequiredDeploymentResource() {
        val resources = object : ClassLoader(null) {}
        val failure = assertFailsWith<IllegalStateException> { loadLwjgl2CompilerClasses(resources) }
        assertTrue(failure.message.orEmpty().contains("compiler-runtime/manifest.properties"))
        assertTrue(failure.message.orEmpty().contains("deploy"))
    }

    @Test
    fun platformSelectionUsesRuntimeArchitectureAndRejectsUnsupportedPairs() {
        assertEquals("natives-macos", compilerNativeClassifier("Mac OS X", "x86_64"))
        assertEquals("natives-macos-arm64", compilerNativeClassifier("Mac OS X", "aarch64"))
        assertEquals("natives-linux", compilerNativeClassifier("Linux", "amd64"))
        assertEquals("natives-windows-x86", compilerNativeClassifier("Windows 10", "x86"))
        assertFailsWith<IllegalStateException> { compilerNativeClassifier("Mac OS X", "i386") }
        assertFailsWith<IllegalStateException> { compilerNativeClassifier("Linux", "x86") }
        assertFailsWith<IllegalStateException> { compilerNativeClassifier("unknown", "x86_64") }
    }

    @Test
    fun factoryPreservesProviderFailureBeforeConstructingADevice() {
        val failure = IllegalStateException("no current host context")
        val provider = object : OpenGLFunctionsProvider {
            override fun create(): OpenGLFunctions = throw failure
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { Lwjgl2GraphicsDeviceFactory(provider).create() })
    }

    private fun compile(compiler: CanonicalShaderCompiler): ShaderCompilation {
        val source = "#version 450\nlayout(location=0) in vec2 position;\nvoid main(){gl_Position=vec4(position,0.0,1.0);}\n"
        return compiler.compile(ShaderModuleDescription(ShaderStage.Vertex, ShaderSource(ShaderLanguage.Glsl, source, "probe")), "shaders/probe.vert", emptyMap())
    }
}
