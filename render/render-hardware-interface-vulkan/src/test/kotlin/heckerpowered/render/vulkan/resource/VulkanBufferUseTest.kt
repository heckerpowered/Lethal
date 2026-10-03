/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.resource

import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class VulkanBufferUseTest {
    private val description = BufferDescription("tracked", 100, setOf(BufferUsage.TransferSource))

    @Test
    fun discardedRecordingReleasesItsReferenceAndCannotBeSubmittedLater() {
        val functions = FakeVulkanBufferFunctions()
        val buffer = createVulkanBuffer(functions, description) as VulkanBuffer
        val use = buffer.recordUse()
        assertEquals(7L, use.handle)
        use.discardRecording()
        assertFailsWith<IllegalStateException> { use.handle }
        assertFailsWith<IllegalStateException> { use.markSubmitted() }
        assertFailsWith<IllegalStateException> { use.discardRecording() }
        buffer.close()
        assertEquals(listOf("destroy:7", "free:9"), functions.calls.takeLast(2))
    }

    @Test
    fun acceptedSubmissionCannotBeDiscardedOrCompletedTwice() {
        val functions = FakeVulkanBufferFunctions()
        val buffer = createVulkanBuffer(functions, description) as VulkanBuffer
        val use = buffer.recordUse()
        assertFailsWith<IllegalStateException> { use.completeSubmission() }
        use.markSubmitted()
        assertFailsWith<IllegalStateException> { use.handle }
        assertFailsWith<IllegalStateException> { use.markSubmitted() }
        assertFailsWith<IllegalStateException> { use.discardRecording() }
        assertFalse(functions.calls.any { it.startsWith("destroy:") })
        use.completeSubmission()
        assertFailsWith<IllegalStateException> { use.completeSubmission() }
        buffer.close()
    }

    @Test
    fun independentRecordingsMustEachReleaseTheirReferencesBeforeFinalDestruction() {
        val functions = FakeVulkanBufferFunctions()
        val buffer = createVulkanBuffer(functions, description) as VulkanBuffer
        val first = buffer.recordUse()
        val second = buffer.recordUse()
        val third = buffer.recordUse()
        first.markSubmitted()
        second.markSubmitted()
        third.discardRecording()
        second.completeSubmission()
        assertFalse(functions.calls.any { it.startsWith("destroy:") })
        first.completeSubmission()
        buffer.close()
        assertEquals(1, functions.calls.count { it == "destroy:7" })
    }

    @Test
    fun rejectedWrongThreadTransitionsLeaveTheRecordingAvailableForItsDeviceThread() {
        val functions = FakeVulkanBufferFunctions()
        val buffer = createVulkanBuffer(functions, description) as VulkanBuffer
        val use = buffer.recordUse()
        functions.accessAllowed = false
        assertFailsWith<IllegalStateException> { buffer.recordUse() }
        assertFailsWith<IllegalStateException> { use.markSubmitted() }
        assertFailsWith<IllegalStateException> { use.discardRecording() }
        functions.accessAllowed = true
        assertEquals(7L, use.handle)
        use.markSubmitted()
        functions.accessAllowed = false
        assertFailsWith<IllegalStateException> { use.completeSubmission() }
        functions.accessAllowed = true
        use.completeSubmission()
        buffer.close()
    }

    @Test
    fun closedBufferCannotAcquireNewRecordingReferences() {
        val buffer = createVulkanBuffer(FakeVulkanBufferFunctions(), description) as VulkanBuffer
        buffer.close()
        assertFailsWith<IllegalStateException> { buffer.recordUse() }
    }

    @Test
    fun samePackageConsumersCannotConstructUnregisteredUsesOrRemovePendingReferences() {
        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "Entry-boundary test requires a JDK" }
        val classpath = listOf(VulkanBuffer::class.java, VulkanBufferUse::class.java, GpuBuffer::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        val directory = Files.createTempDirectory("vulkan-use-entry-").toFile()
        try {
            val cases = listOf(
                "VulkanBufferUse use = buffer.recordUse(); use.markSubmitted(); use.completeSubmission(); buffer.close();" to true,
                "VulkanBufferUse use = new VulkanBufferUse(buffer); use.markSubmitted(); buffer.close();" to false,
                "VulkanBufferUse use = buffer.recordUse(); use.markSubmitted(); buffer.releaseUse(use); buffer.close();" to false,
            )
            for ([index, case] in cases.withIndex()) {
                val source = File(directory, "UseEntry$index.java")
                source.writeText("package heckerpowered.render.vulkan.resource; class UseEntry$index { static void run(VulkanBuffer buffer) { ${case.first} } }")
                val diagnostics = ByteArrayOutputStream()
                val result = compiler.run(null, null, diagnostics, "-proc:none", "-classpath", classpath, "-d", directory.path, source.path)
                if (case.second) assertEquals(0, result, diagnostics.toString())
                else assertNotEquals(0, result, "Unsafe entry unexpectedly compiled: ${case.first}")
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
