/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.util.shaderc.Shaderc.shaderc_include_type_relative
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ShaderIncludesFailureTest {
    @Test
    fun unexpectedIncludeCallbackFailureTerminatesOnlyForkedProcess() {
        val classpath = (System.getProperty("java.class.path").split(java.io.File.pathSeparator) +
            generateSequence(javaClass.classLoader) { it.parent }.filterIsInstance<URLClassLoader>()
                .flatMap { it.urLs.asSequence() }.map { Paths.get(it.toURI()).toString() }.toList()).distinct().joinToString(java.io.File.pathSeparator)
        val command = mutableListOf(Paths.get(System.getProperty("java.home"), "bin", "java").toString())
        val javaVersion = System.getProperty("java.specification.version").substringAfterLast('.').toInt()
        if (javaVersion >= 17) command.add("--enable-native-access=ALL-UNNAMED")
        command.addAll(listOf("-cp", classpath, IncludeCallbackFailureMain::class.java.name))
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Forked fatal-boundary probe timed out")
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(1, process.exitValue(), output)
            assertContains(output, "NullPointerException")
            assertFalse("CALLBACK_RETURNED" in output)
        } finally { process.destroyForcibly() }
    }
}

object IncludeCallbackFailureMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val requested = memUTF8("fixture.glsl")
        val requesting = memUTF8("")
        ShaderIncludes(Paths.get(".")).use { includes ->
            includes.resolve.invoke(0L, memAddress(requested), shaderc_include_type_relative, memAddress(requesting), 0L)
            println("CALLBACK_RETURNED")
        }
    }
}
