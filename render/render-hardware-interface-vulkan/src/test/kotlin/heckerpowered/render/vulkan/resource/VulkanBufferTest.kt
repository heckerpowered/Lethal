/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.resource

import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.vulkan.function.VulkanBufferFunctions
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VulkanBufferTest {
    private val description = BufferDescription("mesh", 100, setOf(BufferUsage.Vertex, BufferUsage.TransferDestination))

    @Test
    fun creationUsesQueriedSizeAndHandsOffCleanupToResource() {
        val functions = FakeVulkanBufferFunctions()
        val buffer = createVulkanBuffer(functions, description) as VulkanBuffer
        assertEquals(listOf("create:100:130", "query:7", "properties", "allocate:128:1:0", "bind:7:9:0"), functions.calls)
        assertEquals(100L, buffer.sizeBytes)
        assertEquals(description.usage, buffer.usage)
        assertEquals(7L, buffer.handle)
        buffer.close()
        buffer.close()
        assertEquals(listOf("destroy:7", "free:9"), functions.calls.takeLast(2))
        assertFailsWith<IllegalStateException> { buffer.handle }
    }

    @Test
    fun eachOperationalFailureReleasesOnlyResourcesAlreadyAcquired() {
        val cases = mapOf(
            "create" to emptyList(),
            "query" to listOf("destroy:7"),
            "properties" to listOf("destroy:7"),
            "allocate" to listOf("destroy:7"),
            "bind" to listOf("destroy:7", "free:9"),
        )
        for ([stage, cleanup] in cases) {
            val functions = FakeVulkanBufferFunctions(stage)
            val failure = assertFailsWith<IllegalStateException> { createVulkanBuffer(functions, description) }
            assertSame(functions.failure, failure)
            assertEquals(cleanup, functions.calls.filter { it.startsWith("destroy:") || it.startsWith("free:") })
        }
    }

    @Test
    fun errorsAlsoReleaseUntransferredResourcesWithoutReplacingTheFailure() {
        val failure = AssertionError("query Error")
        val functions = FakeVulkanBufferFunctions("query", failure)
        assertSame(failure, assertFailsWith<AssertionError> { createVulkanBuffer(functions, description) })
        assertEquals("destroy:7", functions.calls.last())
    }

    @Test
    fun unsupportedMemoryAndMalformedRequirementsDestroyTheCreatedBuffer() {
        for (size in listOf(0L, 99L, 128L)) {
            val functions = FakeVulkanBufferFunctions().apply {
                requirementSize = size
                if (size == 128L) types = intArrayOf(0x21)
            }
            if (size == 128L) assertFailsWith<UnsupportedOperationException> { createVulkanBuffer(functions, description) }
            else assertFailsWith<IllegalArgumentException> { createVulkanBuffer(functions, description) }
            assertEquals("destroy:7", functions.calls.last())
            assertFalse(functions.calls.any { it.startsWith("allocate:") })
        }
    }

    @Test
    fun dedicatedRequirementsAttachTheQueriedBufferToAllocation() {
        val functions = FakeVulkanBufferFunctions().apply { dedicated = true }
        createVulkanBuffer(functions, description).close()
        assertTrue("allocate:128:1:7" in functions.calls)
    }

    @Test
    fun nativeBit31SurvivesTheCompleteResourceCreationPath() {
        val functions = FakeVulkanBufferFunctions().apply {
            types = IntArray(32) { 1 }
            typeBits = Int.MIN_VALUE
        }
        createVulkanBuffer(functions, description).close()
        assertTrue("allocate:128:31:0" in functions.calls)
    }

    @Test
    fun creationAndLiveHandleAccessRejectTheWrongThreadBeforeNativeCalls() {
        val functions = FakeVulkanBufferFunctions()
        val buffer = createVulkanBuffer(functions, description) as VulkanBuffer
        functions.accessAllowed = false
        assertFailsWith<IllegalStateException> { createVulkanBuffer(functions, description) }
        assertFailsWith<IllegalStateException> { buffer.handle }
        assertEquals(5, functions.calls.size)
        functions.accessAllowed = true
        buffer.close()
    }

    @Test
    fun cleanupFailuresHaltIsolatedProcessesWithoutContinuingOrRunningShutdownHooks() {
        val classpath = listOf(VulkanBufferFatalProbe::class.java, VulkanBufferFunctions::class.java, GpuBuffer::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (stage in listOf("destroy", "free", "access", "failedCreationCleanup", "recorded", "pending", "remainingUse")) {
            val output = File.createTempFile("vulkan-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanBufferFatalProbe::class.java.name, stage)
                    .redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, "Fatal probe timed out: $stage")
                assertEquals(1, process.exitValue(), output.readText())
                val log = output.readText()
                assertTrue("fatal-probe-start" in log, log)
                if (stage in listOf("recorded", "pending", "remainingUse")) {
                    assertTrue("still has recorded or pending uses" in log, log)
                    assertFalse("destroy:7" in log, log)
                    assertFalse("free:9" in log, log)
                } else assertTrue("fatal-probe-failure" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("shutdown-hook" in log, log)
                if (stage == "destroy" || stage == "failedCreationCleanup") assertFalse("free:9" in log, log)
            } finally {
                output.delete()
            }
        }
    }
}

internal class FakeVulkanBufferFunctions(
    var failureStage: String = "",
    val failure: Throwable = IllegalStateException("fatal-probe-failure"),
) : VulkanBufferFunctions {
    val calls = mutableListOf<String>()
    var types = intArrayOf(2, 1)
    var typeBits = 3
    var requirementSize = 128L
    var dedicated = false
    var accessAllowed = true
    var printCalls = false

    override fun checkAccess() {
        check(accessAllowed) { "fatal-probe-failure: access" }
    }

    private fun record(stage: String, call: String) {
        calls.add(call)
        if (printCalls) println(call)
        if (stage == failureStage) throw failure
    }

    override fun memoryTypePropertyFlags(): IntArray {
        record("properties", "properties")
        return types.copyOf()
    }

    override fun createBuffer(sizeBytes: Long, usageFlags: Int): Long {
        record("create", "create:$sizeBytes:$usageFlags")
        return 7
    }

    override fun <R> withBufferMemoryRequirements(buffer: Long, consume: (Long, Long, Int, Boolean) -> R): R {
        record("query", "query:$buffer")
        return consume(requirementSize, 64, typeBits, dedicated)
    }

    override fun allocateMemory(sizeBytes: Long, memoryTypeIndex: Int, dedicatedBuffer: Long): Long {
        record("allocate", "allocate:$sizeBytes:$memoryTypeIndex:$dedicatedBuffer")
        return 9
    }

    override fun bindBufferMemory(buffer: Long, memory: Long, offsetBytes: Long) = record("bind", "bind:$buffer:$memory:$offsetBytes")
    override fun destroyBuffer(buffer: Long) = record("destroy", "destroy:$buffer")
    override fun freeMemory(memory: Long) = record("free", "free:$memory")
}

object VulkanBufferFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        println("fatal-probe-start")
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val functions = FakeVulkanBufferFunctions().apply { printCalls = true }
        val description = BufferDescription("fatal", 100, setOf(BufferUsage.Vertex))
        if (arguments.single() == "failedCreationCleanup") {
            functions.requirementSize = 0
            functions.failureStage = "destroy"
            createVulkanBuffer(functions, description)
        } else {
            val buffer = createVulkanBuffer(functions, description) as VulkanBuffer
            if (arguments.single() == "access") functions.accessAllowed = false
            else if (arguments.single() in listOf("recorded", "pending", "remainingUse")) {
                val use = buffer.recordUse()
                if (arguments.single() == "pending") use.markSubmitted()
                if (arguments.single() == "remainingUse") {
                    buffer.recordUse().discardRecording()
                    use.markSubmitted()
                }
            } else functions.failureStage = arguments.single()
            buffer.close()
        }
        println("after-close")
    }
}
