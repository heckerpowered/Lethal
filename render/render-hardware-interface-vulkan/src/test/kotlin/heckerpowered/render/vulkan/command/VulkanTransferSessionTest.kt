/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.command

import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.vulkan.function.*
import heckerpowered.render.vulkan.resource.FakeVulkanBufferFunctions
import heckerpowered.render.vulkan.resource.createVulkanBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.io.File
import java.util.concurrent.TimeUnit

class VulkanTransferSessionTest {
    @Test
    fun acceptedSubmissionKeepsRecordingUntilFenceActuallyCompletes() {
        val functions = FakeVulkanTransferFunctions()
        val session = VulkanTransferSession.create(functions)
        session.record {}
        assertFalse(session.pollCompletion())
        assertFalse("destroyRecording" in functions.calls)
        assertFalse(session.awaitCompletion(0))
        functions.completion = VulkanFenceStatus.Complete
        assertTrue(session.pollCompletion())
        assertTrue(session.pollCompletion())
        assertEquals(1, functions.calls.count { it == "destroyRecording" })
        session.close()
        session.close()
        assertEquals("destroyFence", functions.calls.last())
        assertEquals(1, functions.calls.count { it == "destroyFence" })
    }

    @Test
    fun scopedCopiesAreUnavailableAfterCallbackAndSessionCannotBeResubmitted() {
        val functions = FakeVulkanTransferFunctions()
        val source = createVulkanBuffer(functions.buffers, BufferDescription("source", 100, setOf(BufferUsage.TransferSource)))
        val destination = createVulkanBuffer(functions.buffers, BufferDescription("destination", 100, setOf(BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var escaped: VulkanBufferTransfers
        session.record {
            escaped = this
            copyBuffer(GpuBufferView(source, 1, 3), GpuBufferView(destination, 5, 3))
        }
        assertFailsWith<IllegalStateException> { escaped.copyBuffer(GpuBufferView(source, 1, 3), GpuBufferView(destination, 5, 3)) }
        assertFailsWith<IllegalStateException> { session.record {} }
        assertFailsWith<IllegalStateException> { session.discard() }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        source.close()
        destination.close()
    }

    @Test
    fun callbackErrorsAbandonNativeRecordingBeforeReleasingItsBufferReferences() {
        val functions = FakeVulkanTransferFunctions()
        val source = createVulkanBuffer(functions.buffers, BufferDescription("source", 100, setOf(BufferUsage.TransferSource)))
        val destination = createVulkanBuffer(functions.buffers, BufferDescription("destination", 100, setOf(BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        val failure = AssertionError("record callback")
        assertSame(failure, assertFailsWith<AssertionError> {
            session.record {
                copyBuffer(GpuBufferView(source, 0, 4), GpuBufferView(destination, 0, 4))
                throw failure
            }
        })
        assertEquals("destroyRecording", functions.calls.last())
        assertFalse("submit" in functions.calls)
        session.close()
        source.close()
        destination.close()
    }

    @Test
    fun failuresBeforeSubmissionAreDiscardedAndOriginalFailurePropagates() {
        for (stage in listOf("after", "end", "createFence")) {
            val functions = FakeVulkanTransferFunctions().apply { failureStage = stage }
            val session = VulkanTransferSession.create(functions)
            assertSame(functions.failure, assertFailsWith<IllegalStateException> { session.record {} })
            assertEquals("destroyRecording", functions.calls.last())
            assertFalse("submit" in functions.calls)
            session.close()
        }
    }

    @Test
    fun catchingNativeCopyFailureInsideCallbackCannotMakeRecordingSubmittable() {
        for (stage in listOf("before", "copy")) {
            val functions = FakeVulkanTransferFunctions().apply { failureStage = stage }
            val source = createVulkanBuffer(functions.buffers, BufferDescription("source", 100, setOf(BufferUsage.TransferSource)))
            val destination = createVulkanBuffer(functions.buffers, BufferDescription("destination", 100, setOf(BufferUsage.TransferDestination)))
            val session = VulkanTransferSession.create(functions)
            assertFailsWith<IllegalStateException> {
                session.record {
                    assertFailsWith<IllegalStateException> { copyBuffer(GpuBufferView(source, 0, 4), GpuBufferView(destination, 0, 4)) }
                    assertFailsWith<IllegalStateException> { copyBuffer(GpuBufferView(source, 0, 4), GpuBufferView(destination, 0, 4)) }
                }
            }
            assertFalse("submit" in functions.calls)
            assertEquals("destroyRecording", functions.calls.last())
            session.close()
            source.close()
            destination.close()
        }
    }

    @Test
    fun outOfMemorySubmissionIsUnsubmittedAndCanBeDiscarded() {
        for (status in listOf(VulkanSubmissionStatus.OutOfHostMemory, VulkanSubmissionStatus.OutOfDeviceMemory)) {
            val functions = FakeVulkanTransferFunctions().apply { submission = status }
            val session = VulkanTransferSession.create(functions)
            assertFailsWith<IllegalStateException> { session.record {} }
            assertEquals("destroyRecording", functions.calls.last())
            session.close()
        }
    }

    @Test
    fun lostOrUncertainSubmissionRetainsRecordingAndRejectsFurtherOperations() {
        for (unknown in listOf(false, true)) {
            val functions = FakeVulkanTransferFunctions().apply {
                submission = VulkanSubmissionStatus.DeviceLost
                if (unknown) failureStage = "submit"
            }
            val session = VulkanTransferSession.create(functions)
            assertFailsWith<IllegalStateException> { session.record {} }
            assertFalse("destroyRecording" in functions.calls)
            assertFalse("destroyFence" in functions.calls)
            assertFailsWith<IllegalStateException> { session.pollCompletion() }
            assertFailsWith<IllegalStateException> { session.discard() }
            assertFailsWith<IllegalStateException> { session.record {} }
        }
    }

    @Test
    fun lostOrThrowingFenceQueryNeverReleasesPotentiallyPendingRecording() {
        for (unknown in listOf(false, true)) {
            val functions = FakeVulkanTransferFunctions().apply {
                completion = VulkanFenceStatus.DeviceLost
                if (unknown) failureStage = "poll"
            }
            val session = VulkanTransferSession.create(functions)
            session.record {}
            assertFailsWith<IllegalStateException> { session.pollCompletion() }
            assertFalse("destroyRecording" in functions.calls)
            assertFailsWith<IllegalStateException> { session.awaitCompletion(0) }
        }
    }

    @Test
    fun unusedSessionMustBeExplicitlyDiscardedAndCannotRecordAgain() {
        val functions = FakeVulkanTransferFunctions()
        val session = VulkanTransferSession.create(functions)
        session.discard()
        assertFailsWith<IllegalStateException> { session.record {} }
        assertFailsWith<IllegalStateException> { session.discard() }
        session.close()
        assertFalse("createFence" in functions.calls)
        assertFalse("submit" in functions.calls)
        assertFalse("wait:0" in functions.calls)
    }

    @Test
    fun wrongThreadAndInvalidTimeoutFailWithoutConsumingSessionState() {
        val functions = FakeVulkanTransferFunctions()
        val session = VulkanTransferSession.create(functions)
        functions.bufferState.accessAllowed = false
        assertFailsWith<IllegalStateException> { session.record {} }
        functions.bufferState.accessAllowed = true
        session.record {}
        assertFailsWith<IllegalArgumentException> { session.awaitCompletion(-1) }
        assertFalse(session.pollCompletion())
        functions.completion = VulkanFenceStatus.Complete
        session.awaitCompletion(0)
        session.close()
    }

    @Test
    fun trackedSessionViolationsAndCleanupFailuresHaltBeforeUnsafeNativeDestruction() {
        val classpath = listOf(VulkanTransferFatalProbe::class.java, VulkanTransferSession::class.java, GpuBuffer::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (mode in listOf("recordedBuffer", "pendingBuffer", "pendingSession", "uncertainSubmission", "destroyRecording", "unmap")) {
            val output = File.createTempFile("vulkan-transfer-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanTransferFatalProbe::class.java.name, mode)
                    .redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, "Transfer fatal probe timed out: $mode")
                val log = output.readText()
                assertEquals(1, process.exitValue(), log)
                assertTrue("fatal-transfer-start" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("shutdown-hook" in log, log)
                assertFalse("destroy:7" in log, log)
                assertFalse("free:9" in log, log)
                if (mode == "recordedBuffer" || mode == "pendingBuffer") assertTrue("still has recorded or pending uses" in log, log)
                else if (mode == "destroyRecording") assertTrue("transfer failure" in log, log)
                else if (mode == "unmap") {
                    assertTrue("host memory failure" in log, log)
                    assertFalse("destroy:9" in log, log)
                }
                else assertTrue("still has recording or pending use" in log, log)
            } finally {
                output.delete()
            }
        }
    }
}

internal class FakeVulkanTransferFunctions : VulkanTransferFunctions {
    val bufferState = FakeVulkanBufferFunctions()
    val buffers: VulkanBufferFunctions = object : VulkanBufferFunctions by bufferState {
        private var nextBuffer = 7L
        private var nextMemory = 9L

        override fun createBuffer(sizeBytes: Long, usageFlags: Int): Long {
            bufferState.createBuffer(sizeBytes, usageFlags)
            return nextBuffer++
        }

        override fun allocateMemory(sizeBytes: Long, memoryTypeIndex: Int, dedicatedBuffer: Long): Long {
            bufferState.allocateMemory(sizeBytes, memoryTypeIndex, dedicatedBuffer)
            val memory = nextMemory++
            (hostMemoryFunctions as? FakeVulkanHostMemoryFunctions)?.allocate(memory, sizeBytes)
            return memory
        }

        override fun bindBufferMemory(buffer: Long, memory: Long, offsetBytes: Long) {
            bufferState.bindBufferMemory(buffer, memory, offsetBytes)
            (hostMemoryFunctions as? FakeVulkanHostMemoryFunctions)?.bind(buffer, memory)
        }
    }
    override val bufferFunctions get() = buffers
    override var hostMemoryFunctions: VulkanHostMemoryFunctions? = null
    private val copies = mutableListOf<() -> Unit>()
    val calls = mutableListOf<String>()
    var submission = VulkanSubmissionStatus.Accepted
    var completion = VulkanFenceStatus.Pending
    var failureStage = ""
    var printCalls = false
    val failure = IllegalStateException("transfer failure")

    private fun call(stage: String, details: String = stage) {
        calls.add(details)
        if (printCalls) println(details)
        if (failureStage == stage) throw failure
    }

    override fun createRecording(): VulkanTransferRecording {
        call("createRecording")
        return VulkanTransferRecording(11, 13)
    }

    override fun beforeBufferCopy(recording: VulkanTransferRecording) = call("before")
    override fun afterBufferCopies(recording: VulkanTransferRecording) = call("after")
    override fun endRecording(recording: VulkanTransferRecording) = call("end")

    override fun copyBuffer(recording: VulkanTransferRecording, source: Long, sourceOffsetBytes: Long, destination: Long, destinationOffsetBytes: Long, sizeBytes: Long) {
        call("copy", "copy:$sourceOffsetBytes:$destinationOffsetBytes:$sizeBytes")
        val memory = hostMemoryFunctions as? FakeVulkanHostMemoryFunctions ?: return
        copies.add { memory.copy(source, sourceOffsetBytes, destination, destinationOffsetBytes, sizeBytes) }
    }

    override fun createFence(): Long {
        call("createFence")
        return 17
    }

    override fun submit(recording: VulkanTransferRecording, fence: Long): VulkanSubmissionStatus {
        call("submit")
        if (submission == VulkanSubmissionStatus.Accepted) for (copy in copies) copy()
        return submission
    }

    override fun fenceStatus(fence: Long): VulkanFenceStatus {
        call("poll")
        return completion
    }

    override fun waitForFence(fence: Long, timeoutNanoseconds: Long): VulkanFenceStatus {
        call("wait", "wait:$timeoutNanoseconds")
        return completion
    }

    override fun destroyRecording(recording: VulkanTransferRecording) = call("destroyRecording")
    override fun destroyFence(fence: Long) = call("destroyFence")
}

object VulkanTransferFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        println("fatal-transfer-start")
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val mode = arguments.single()
        val functions = FakeVulkanTransferFunctions().apply {
            printCalls = true
            bufferState.printCalls = true
            if (mode == "uncertainSubmission") failureStage = "submit"
            if (mode == "unmap") hostMemoryFunctions = FakeVulkanHostMemoryFunctions(buffers).apply { printCalls = true }
        }
        val source = createVulkanBuffer(functions.buffers, BufferDescription("fatal source", 100, setOf(BufferUsage.TransferSource)))
        val destination = createVulkanBuffer(functions.buffers, BufferDescription("fatal destination", 100, setOf(BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        try {
            session.record {
                if (mode == "unmap") writeBuffer(GpuBufferView(destination, 1, 3), byteArrayOf(1, 2, 3))
                else copyBuffer(GpuBufferView(source, 0, 4), GpuBufferView(destination, 0, 4))
                if (mode == "recordedBuffer") source.close()
            }
        } catch (failure: IllegalStateException) {
            if (mode != "uncertainSubmission") throw failure
        }
        when (mode) {
            "pendingBuffer" -> source.close()
            "pendingSession", "uncertainSubmission" -> session.close()
            "destroyRecording" -> {
                functions.failureStage = "destroyRecording"
                functions.completion = VulkanFenceStatus.Complete
                session.pollCompletion()
            }
            "unmap" -> {
                functions.completion = VulkanFenceStatus.Complete
                session.pollCompletion()
                (functions.hostMemoryFunctions as FakeVulkanHostMemoryFunctions).failureStage = "unmap"
                session.close()
            }
        }
        println("after-close")
    }
}
