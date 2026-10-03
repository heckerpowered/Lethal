/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.compiler

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.test.*

class ShaderArtifactPublisherTest {
    @Test
    fun failedSecondPublicationRestoresPreviousPair() = output { directory ->
        val openGL = directory.resolve("shader.rhigl")
        val spirV = directory.resolve("shader.spv")
        Files.write(openGL, byteArrayOf(1))
        Files.write(spirV, byteArrayOf(2))
        var moves = 0
        assertFailsWith<IOException> {
            publishShaderArtifacts(openGL, byteArrayOf(3), spirV, byteArrayOf(4)) { source, target ->
                if (++moves == 2) throw IOException("second publication failed")
                Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
            }
        }
        assertContentEquals(byteArrayOf(1), Files.readAllBytes(openGL))
        assertContentEquals(byteArrayOf(2), Files.readAllBytes(spirV))
        Files.list(directory).use { assertEquals(2L, it.count()) }
    }

    @Test
    fun failedFirstPairLeavesNeitherNewArtifact() = output { directory ->
        var moves = 0
        assertFailsWith<IOException> {
            publishShaderArtifacts(directory.resolve("shader.rhigl"), byteArrayOf(3), directory.resolve("shader.spv"), byteArrayOf(4)) { source, target ->
                if (++moves == 2) throw IOException("second publication failed")
                Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
            }
        }
        Files.list(directory).use { assertEquals(0L, it.count()) }
    }

    @Test
    fun nonRegularTargetsAreRejectedBeforeReplacingOldArtifact() = output { directory ->
        val openGL = directory.resolve("shader.rhigl")
        val spirV = directory.resolve("shader.spv")
        Files.write(openGL, byteArrayOf(1))
        Files.createDirectory(spirV)
        assertFailsWith<IllegalArgumentException> { publishShaderArtifacts(openGL, byteArrayOf(3), spirV, byteArrayOf(4)) }
        assertContentEquals(byteArrayOf(1), Files.readAllBytes(openGL))
        Files.delete(spirV)
        val linked = directory.resolve("linked")
        Files.write(linked, byteArrayOf(2))
        Files.createSymbolicLink(spirV, linked)
        assertFailsWith<IllegalArgumentException> { publishShaderArtifacts(openGL, byteArrayOf(3), spirV, byteArrayOf(4)) }
        assertTrue(Files.isSymbolicLink(spirV))
        assertContentEquals(byteArrayOf(2), Files.readAllBytes(linked))
    }

    @Test
    fun failedThirdPublicationRestoresAllThreeExistingArtifacts() = output { directory ->
        val targets = listOf("shader.rhigl", "shader.spv", "shader.rhif").map(directory::resolve)
        for (index in targets.indices) Files.write(targets[index], byteArrayOf(index.toByte()))
        var moves = 0
        assertFailsWith<IOException> {
            publishShaderArtifacts(targets.map { it to byteArrayOf(9) }) { source, target ->
                if (++moves == 3) throw IOException("third publication failed")
                Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
            }
        }
        for (index in targets.indices) assertContentEquals(byteArrayOf(index.toByte()), Files.readAllBytes(targets[index]))
        Files.list(directory).use { assertEquals(3L, it.count()) }
    }

    @Test
    fun failedThirdPublicationRemovesAllThreePreviouslyAbsentArtifacts() = output { directory ->
        val targets = listOf("shader.rhigl", "shader.spv", "shader.rhif").map(directory::resolve)
        var moves = 0
        assertFailsWith<IOException> {
            publishShaderArtifacts(targets.map { it to byteArrayOf(9) }) { source, target ->
                if (++moves == 3) throw IOException("third publication failed")
                Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
            }
        }
        Files.list(directory).use { assertEquals(0L, it.count()) }
    }

    @Test
    fun failedRestorationKeepsBackupsAndReportsTheirLocation() = output { directory ->
        val openGL = directory.resolve("shader.rhigl")
        val spirV = directory.resolve("shader.spv")
        Files.write(openGL, byteArrayOf(1))
        Files.write(spirV, byteArrayOf(2))
        var moves = 0
        val failure = assertFailsWith<IOException> {
            publishShaderArtifacts(openGL, byteArrayOf(3), spirV, byteArrayOf(4)) { source, target ->
                if (++moves >= 2) throw IOException("publication and restoration failed")
                Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
            }
        }
        val recovery = Files.list(directory).use { entries -> entries.filter { Files.isDirectory(it) }.findFirst().orElseThrow { NoSuchElementException("No value present") } }
        assertContains(failure.message.orEmpty(), recovery.toString())
        assertContentEquals(byteArrayOf(1), Files.readAllBytes(recovery.resolve("previous-0")))
        assertContentEquals(byteArrayOf(2), Files.readAllBytes(recovery.resolve("previous-1")))
    }
}

private fun output(action: (Path) -> Unit) {
    val directory = Files.createTempDirectory("rhi-artifact-publication")
    try { action(directory) } finally { directory.toFile().deleteRecursively() }
}
