/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan

import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.resource.sampler.TextureFilter
import heckerpowered.render.vulkan.command.RasterTestQueue
import heckerpowered.render.vulkan.resource.FakeVulkanSamplerFunctions
import heckerpowered.render.vulkan.resource.defaultVulkanSamplerDescription
import kotlin.test.*

class VulkanEngineCreationTest {
    @Test
    fun ordinaryEngineCreationAndCloseOwnTheExactDefaultNativeSamplerWithoutOtherGpuWork() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val device = testVulkanGraphicsDevice(queue, emptyList(), MemoryStack(), false, functions)
        try {
            val engine = RenderEngine.create(device)
            assertEquals(listOf(defaultVulkanSamplerDescription()), functions.descriptions)
            assertEquals(setOf(10000L), functions.live)
            assertTrue(queue.calls.isEmpty())
            engine.close()
            engine.close()
            assertEquals(listOf("create", "destroy:10000"), functions.calls)
            assertTrue(functions.live.isEmpty())
            assertTrue(queue.calls.isEmpty())
            val callerOwned = device.createSampler(defaultVulkanSamplerDescription())
            callerOwned.close()
        } finally { device.close() }
        assertEquals(2, functions.calls.count { it == "create" })
        assertEquals(2, functions.calls.count { it.startsWith("destroy:") })
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun engineSamplerIsIndependentOfTheDeviceBorrowedSamplerCacheAcrossEngineLifetimes() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val device = testVulkanGraphicsDevice(queue, emptyList(), MemoryStack(), false, functions)
        val cached = device.resolveSampler(defaultVulkanSamplerDescription())
        try {
            repeat(2) {
                val engine = RenderEngine.create(device)
                assertEquals(2, functions.live.size)
                engine.close()
                assertEquals(setOf(device.requireSampler(cached).handle), functions.live)
                assertSame(cached, device.resolveSampler(defaultVulkanSamplerDescription()))
            }
            assertEquals(3, functions.calls.count { it == "create" })
            assertEquals(2, functions.calls.count { it.startsWith("destroy:") })
        } finally { device.close() }
        assertTrue(functions.live.isEmpty())
        assertEquals(3, functions.calls.count { it.startsWith("destroy:") })
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun failedDefaultSamplerCreationDoesNotPublishAnEngineAndTheSameDeviceCanRetry() {
        val queue = RasterTestQueue()
        val failure = IllegalStateException("native sampler allocation failure")
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity).apply { creationFailure = failure }
        val device = testVulkanGraphicsDevice(queue, emptyList(), MemoryStack(), false, functions)
        try {
            assertSame(failure, assertFailsWith<IllegalStateException> { RenderEngine.create(device) })
            assertTrue(functions.live.isEmpty())
            assertEquals(listOf("create"), functions.calls)
            assertTrue(queue.calls.isEmpty())
            functions.creationFailure = null
            RenderEngine.create(device).close()
        } finally { device.close() }
        assertEquals(listOf("create", "create", "destroy:10000"), functions.calls)
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun absentSamplerCapabilityStillRejectsOrdinaryEngineCreationBeforeOtherGpuWork() {
        val queue = RasterTestQueue()
        val device = testVulkanGraphicsDevice(queue)
        try {
            assertFailsWith<UnsupportedOperationException> { RenderEngine.create(device) }
            assertTrue(queue.calls.isEmpty())
        } finally { device.close() }
    }

    @Test
    fun consumerSuccessDoesNotExpandTheFiniteSamplerProfile() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val device = testVulkanGraphicsDevice(queue, emptyList(), MemoryStack(), false, functions)
        val engine = RenderEngine.create(device)
        try {
            val unsupported = defaultVulkanSamplerDescription().copy(minificationFilter = TextureFilter.Nearest)
            assertFailsWith<UnsupportedOperationException> { device.createSampler(unsupported) }
            assertFailsWith<UnsupportedOperationException> { device.resolveSampler(unsupported) }
            assertEquals(listOf(defaultVulkanSamplerDescription()), functions.descriptions)
        } finally { engine.close(); device.close() }
        assertTrue(queue.calls.isEmpty())
    }
}
