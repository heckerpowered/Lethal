/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.command

import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.vulkan.function.VulkanFenceStatus
import heckerpowered.render.vulkan.resource.FakeVulkanBufferFunctions
import heckerpowered.render.vulkan.resource.createVulkanBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class VulkanBufferTransfersTest {
    private fun createBuffer(functions: FakeVulkanTransferFunctions, vararg usage: BufferUsage): GpuBuffer =
        createVulkanBuffer(functions.buffers, BufferDescription("copy", 100, usage.toSet()))

    @Test
    fun byteGranularityAndDependenciesArePreservedAcrossSuccessiveCopies() {
        val functions = FakeVulkanTransferFunctions()
        val source = createBuffer(functions, BufferUsage.TransferSource, BufferUsage.TransferDestination)
        val destination = createBuffer(functions, BufferUsage.TransferSource, BufferUsage.TransferDestination)
        val session = VulkanTransferSession.create(functions)
        session.record {
            copyBuffer(GpuBufferView(source, 1, 3), GpuBufferView(destination, 5, 3))
            copyBuffer(GpuBufferView(destination, 5, 3), GpuBufferView(source, 9, 3))
            copyBuffer(GpuBufferView(source, 9, 3), GpuBufferView(destination, 5, 3))
        }
        assertEquals(listOf("createRecording", "before", "copy:1:5:3", "before", "copy:5:9:3", "before", "copy:9:5:3", "after", "end", "createFence", "submit"), functions.calls)
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        source.close()
        destination.close()
    }

    @Test
    fun missingRhiPermissionCannotBeRecoveredFromSharedNativeUsageFlags() {
        val functions = FakeVulkanTransferFunctions()
        val source = createBuffer(functions, BufferUsage.TransferSource)
        val destination = createBuffer(functions, BufferUsage.QueryResolve)
        val session = VulkanTransferSession.create(functions)
        session.record {
            assertFailsWith<IllegalArgumentException> { copyBuffer(GpuBufferView(source, 0, 4), GpuBufferView(destination, 0, 4)) }
        }
        assertFalse(functions.calls.any { it.startsWith("copy:") })
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        source.close()
        destination.close()
    }

    @Test
    fun emptyCopyStillChecksDeviceAndPermissionWithoutIssuingNativeCopy() {
        val functions = FakeVulkanTransferFunctions()
        val source = createBuffer(functions, BufferUsage.TransferSource)
        val foreign = createVulkanBuffer(FakeVulkanBufferFunctions(), BufferDescription("foreign", 100, setOf(BufferUsage.TransferDestination)))
        val destination = createBuffer(functions, BufferUsage.TransferDestination)
        val session = VulkanTransferSession.create(functions)
        session.record {
            assertFailsWith<IllegalArgumentException> { copyBuffer(GpuBufferView(source, 100, 0), GpuBufferView(foreign, 100, 0)) }
            copyBuffer(GpuBufferView(source, 100, 0), GpuBufferView(destination, 100, 0))
        }
        assertFalse("before" in functions.calls)
        assertFalse(functions.calls.any { it.startsWith("copy:") })
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        source.close()
        foreign.close()
        destination.close()
    }

    @Test
    fun sameBufferDisjointRangesWorkWhileActualOverlapIsRejected() {
        val functions = FakeVulkanTransferFunctions()
        val buffer = createBuffer(functions, BufferUsage.TransferSource, BufferUsage.TransferDestination)
        val session = VulkanTransferSession.create(functions)
        session.record {
            assertFailsWith<IllegalArgumentException> { copyBuffer(GpuBufferView(buffer, 0, 4), GpuBufferView(buffer, 2, 4)) }
            copyBuffer(GpuBufferView(buffer, 0, 3), GpuBufferView(buffer, 3, 3))
        }
        assertEquals(1, functions.calls.count { it.startsWith("copy:") })
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        buffer.close()
    }
}
