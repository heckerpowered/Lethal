/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.command

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.vulkan.function.*
import heckerpowered.render.vulkan.resource.createVulkanBuffer
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VulkanBufferStagingTest {
    private fun functions(coherent: Boolean = false): FakeVulkanTransferFunctions = FakeVulkanTransferFunctions().apply {
        if (coherent) bufferState.types = intArrayOf(6, 1)
        hostMemoryFunctions = FakeVulkanHostMemoryFunctions(buffers)
    }

    @Test
    fun savedHostBytesSurviveMutationAndReadbackRequiresActualCompletion() {
        val functions = functions()
        val memory = functions.hostMemoryFunctions as FakeVulkanHostMemoryFunctions
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("bytes", 100, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var readback: VulkanBufferReadback
        val bytes = byteArrayOf(11, 22, 33)
        session.record {
            writeBuffer(GpuBufferView(buffer, 1, 3), bytes)
            bytes.fill(99)
            readback = readBuffer(GpuBufferView(buffer, 1, 3))
        }
        assertFailsWith<IllegalStateException> { readback.readBytes() }
        assertFalse(session.pollCompletion())
        assertFalse(memory.calls.any { it.startsWith("invalidate:") || it.startsWith("unmap:") })
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        assertContentEquals(byteArrayOf(11, 22, 33), readback.readBytes())
        assertEquals(1, memory.calls.count { it.startsWith("flush:") })
        assertEquals(1, memory.calls.count { it.startsWith("invalidate:") })
        assertTrue(memory.calls.filter { it.startsWith("map:") }.all { it.endsWith(":128") })
        session.close()
        assertEquals(2, memory.calls.count { it.startsWith("unmap:") })
        assertFailsWith<IllegalStateException> { readback.readBytes() }
        buffer.close()
    }

    @Test
    fun copiesReadExecutionPositionContentsAndSubrangeUploadPreservesDefinedNeighbors() {
        val functions = functions()
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("ordered", 6, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var first: VulkanBufferReadback
        lateinit var second: VulkanBufferReadback
        session.record {
            writeBuffer(GpuBufferView(buffer, 0, 6), byteArrayOf(1, 2, 3, 4, 5, 6))
            first = readBuffer(GpuBufferView(buffer, 0, 6))
            writeBuffer(GpuBufferView(buffer, 1, 3), byteArrayOf(7, 8, 9))
            second = readBuffer(GpuBufferView(buffer, 0, 6))
        }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), first.readBytes())
        assertContentEquals(byteArrayOf(1, 7, 8, 9, 5, 6), second.readBytes())
        session.close()
        buffer.close()
    }

    @Test
    fun hostCoherentAllocationsSkipCacheOperationsWhileKeepingByteCopyAndCompletionRules() {
        val functions = functions(true)
        val memory = functions.hostMemoryFunctions as FakeVulkanHostMemoryFunctions
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("coherent", 3, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var readback: VulkanBufferReadback
        session.record {
            writeBuffer(GpuBufferView(buffer, 0, 3), byteArrayOf(4, 5, 6))
            readback = readBuffer(GpuBufferView(buffer, 0, 3))
        }
        assertFailsWith<IllegalStateException> { readback.readBytes() }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        assertContentEquals(byteArrayOf(4, 5, 6), readback.readBytes())
        assertFalse(memory.calls.any { it.startsWith("flush:") || it.startsWith("invalidate:") })
        session.close()
        buffer.close()
    }

    @Test
    fun scopedNativeInputIsCopiedBeforeItsMemoryFrameExpiresAndCanReadIntoAnotherFrame() {
        val functions = functions()
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("native source", 3, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        val stack = MemoryStack(32)
        lateinit var readback: VulkanBufferReadback
        session.record {
            stack.frame {
                val source = reserve(3, 1)
                asByteBuffer(source, 3).put(byteArrayOf(31, 32, 33))
                writeBuffer(GpuBufferView(buffer, 0, 3), source)
            }
            stack.frame { reserveBuffer(3).put(byteArrayOf(90, 90, 90)) }
            readback = readBuffer(GpuBufferView(buffer, 0, 3))
        }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        stack.frame {
            val destination = reserve(3, 1)
            readback.read(destination)
            val bytes = asByteBuffer(destination, 3)
            assertEquals(31.toByte(), bytes.get(0))
            assertEquals(32.toByte(), bytes.get(1))
            assertEquals(33.toByte(), bytes.get(2))
        }
        session.close()
        buffer.close()
    }

    @Test
    fun largeUnalignedUploadAndReadbackDoNotInheritInlineUpdateLimits() {
        val functions = functions().apply { bufferState.requirementSize = 131072 }
        val bytes = ByteArray(65539) { (it % 251).toByte() }
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("large", 65540, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var readback: VulkanBufferReadback
        session.record {
            writeBuffer(GpuBufferView(buffer, 1, bytes.size.toLong()), bytes)
            readback = readBuffer(GpuBufferView(buffer, 1, bytes.size.toLong()))
        }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        assertContentEquals(bytes, readback.readBytes())
        session.close()
        buffer.close()
    }

    @Test
    fun unavailableHostVisibleMemoryFailsBeforeMappingWithoutChangingBufferPlacementPermission() {
        val functions = functions().apply { bufferState.types = intArrayOf(1) }
        val memory = functions.hostMemoryFunctions as FakeVulkanHostMemoryFunctions
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("device only", 3, setOf(BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        assertFailsWith<UnsupportedOperationException> { session.record { writeBuffer(GpuBufferView(buffer, 0, 3), byteArrayOf(1, 2, 3)) } }
        assertTrue(memory.calls.isEmpty())
        assertFalse("submit" in functions.calls)
        session.close()
        buffer.close()
    }

    @Test
    fun mappingAndFlushFailurePreserveTheOriginalFailureAndReleaseUnsubmittedStorage() {
        for (stage in listOf("map", "write", "flush")) {
            val functions = functions()
            val memory = functions.hostMemoryFunctions as FakeVulkanHostMemoryFunctions
            memory.failureStage = stage
            val buffer = createVulkanBuffer(functions.buffers, BufferDescription("failed upload", 3, setOf(BufferUsage.TransferDestination)))
            val session = VulkanTransferSession.create(functions)
            assertSame(memory.failure, assertFailsWith<IllegalStateException> {
                session.record { writeBuffer(GpuBufferView(buffer, 0, 3), byteArrayOf(1, 2, 3)) }
            })
            assertFalse("submit" in functions.calls)
            session.close()
            if (stage != "map") assertEquals(1, memory.calls.count { it.startsWith("unmap:") })
            buffer.close()
        }
    }

    @Test
    fun invalidationFailureCanBeRetriedAfterCompletionWithoutPretendingBytesWereRead() {
        val functions = functions()
        val memory = functions.hostMemoryFunctions as FakeVulkanHostMemoryFunctions
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("invalidate", 3, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var readback: VulkanBufferReadback
        session.record {
            writeBuffer(GpuBufferView(buffer, 0, 3), byteArrayOf(1, 2, 3))
            readback = readBuffer(GpuBufferView(buffer, 0, 3))
        }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        memory.failureStage = "invalidate"
        assertSame(memory.failure, assertFailsWith<IllegalStateException> { readback.readBytes() })
        assertFalse(memory.calls.any { it.startsWith("read:") })
        memory.failureStage = ""
        assertContentEquals(byteArrayOf(1, 2, 3), readback.readBytes())
        session.close()
        buffer.close()
    }

    @Test
    fun emptyTransfersStillValidateRolesAndNeedNoHostMemoryCapabilityOrMapping() {
        val functions = FakeVulkanTransferFunctions()
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("empty", 3, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var empty: VulkanBufferReadback
        session.record {
            writeBuffer(GpuBufferView(buffer, 3, 0), byteArrayOf())
            empty = readBuffer(GpuBufferView(buffer, 3, 0))
            assertFailsWith<IllegalArgumentException> { writeBuffer(GpuBufferView(buffer, 0, 2), byteArrayOf(1)) }
        }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        assertContentEquals(byteArrayOf(), empty.readBytes())
        session.close()
        buffer.close()
    }

    @Test
    fun uncertainSubmissionKeepsMappedStorageAndCannotBeReadAsCompleted() {
        val functions = functions().apply { failureStage = "submit" }
        val memory = functions.hostMemoryFunctions as FakeVulkanHostMemoryFunctions
        val buffer = createVulkanBuffer(functions.buffers, BufferDescription("uncertain upload", 3, setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var readback: VulkanBufferReadback
        assertFailsWith<IllegalStateException> {
            session.record {
                writeBuffer(GpuBufferView(buffer, 0, 3), byteArrayOf(1, 2, 3))
                readback = readBuffer(GpuBufferView(buffer, 0, 3))
            }
        }
        assertFailsWith<IllegalStateException> { readback.readBytes() }
        assertFalse(memory.calls.any { it.startsWith("unmap:") || it.startsWith("read:") || it.startsWith("invalidate:") })
        assertFalse(functions.bufferState.calls.any { it.startsWith("destroy:") || it.startsWith("free:") })
    }
}

/** A CPU byte model executing queued copies on fake submission; it never calls a Vulkan driver. */
internal class FakeVulkanHostMemoryFunctions(override val bufferFunctions: VulkanBufferFunctions) : VulkanHostMemoryFunctions {
    private val allocations = mutableMapOf<Long, ByteArray>()
    private val bindings = mutableMapOf<Long, Long>()
    private val mappings = mutableSetOf<Long>()
    val calls = mutableListOf<String>()
    var failureStage = ""
    var printCalls = false
    val failure = IllegalStateException("host memory failure")

    fun allocate(memory: Long, sizeBytes: Long) { allocations[memory] = ByteArray(sizeBytes.toInt()) }
    fun bind(buffer: Long, memory: Long) { bindings[buffer] = memory }

    fun copy(source: Long, sourceOffset: Long, destination: Long, destinationOffset: Long, size: Long) {
        val sourceBytes = allocations.getValue(bindings.getValue(source))
        val destinationBytes = allocations.getValue(bindings.getValue(destination))
        System.arraycopy(sourceBytes, sourceOffset.toInt(), destinationBytes, destinationOffset.toInt(), size.toInt())
    }

    private fun call(stage: String, details: String) {
        calls.add(details)
        if (printCalls) println(details)
        if (failureStage == stage) throw failure
    }

    override fun mapWholeAllocation(memory: Long, allocationSizeBytes: Long): Long {
        call("map", "map:$memory:$allocationSizeBytes")
        check(allocations.getValue(memory).size.toLong() == allocationSizeBytes)
        check(mappings.add(memory))
        return memory
    }

    override fun unmapMemory(memory: Long) {
        call("unmap", "unmap:$memory")
        check(mappings.remove(memory))
    }

    override fun flushWholeAllocation(memory: Long) {
        call("flush", "flush:$memory")
        check(memory in mappings)
    }

    override fun invalidateWholeAllocation(memory: Long) {
        call("invalidate", "invalidate:$memory")
        check(memory in mappings)
    }

    override fun writeMappedBytes(mappedAddress: Long, source: NativeAddress, sizeBytes: Long) {
        call("write", "write:$mappedAddress:$sizeBytes")
        val destination = allocations.getValue(mappedAddress)
        unsafe.copyMemory(null, source.rawValue, destination, byteArrayBase, sizeBytes)
    }

    override fun writeMappedBytes(mappedAddress: Long, source: ByteArray) {
        call("write", "write:$mappedAddress:${source.size}")
        source.copyInto(allocations.getValue(mappedAddress))
    }

    override fun readMappedBytes(mappedAddress: Long, destination: NativeAddress, sizeBytes: Long) {
        call("read", "read:$mappedAddress:$sizeBytes")
        unsafe.copyMemory(allocations.getValue(mappedAddress), byteArrayBase, null, destination.rawValue, sizeBytes)
    }

    override fun readMappedBytes(mappedAddress: Long, destination: ByteArray) {
        call("read", "read:$mappedAddress:${destination.size}")
        allocations.getValue(mappedAddress).copyInto(destination, endIndex = destination.size)
    }

    companion object {
        private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        private val byteArrayBase = unsafe.arrayBaseOffset(ByteArray::class.java).toLong()
    }
}
