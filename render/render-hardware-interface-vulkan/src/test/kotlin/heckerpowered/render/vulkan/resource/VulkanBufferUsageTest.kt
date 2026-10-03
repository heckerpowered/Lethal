/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.resource

import heckerpowered.render.resource.buffer.BufferUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VulkanBufferUsageTest {
    @Test
    fun everyRhiRoleRetainsItsNativePermission() {
        val expected = mapOf(
            BufferUsage.TransferSource to 0x01,
            BufferUsage.TransferDestination to 0x02,
            BufferUsage.QueryResolve to 0x02,
            BufferUsage.UniformTexel to 0x04,
            BufferUsage.StorageTexel to 0x08,
            BufferUsage.Uniform to 0x10,
            BufferUsage.Storage to 0x20,
            BufferUsage.Index to 0x40,
            BufferUsage.Vertex to 0x80,
            BufferUsage.Indirect to 0x100,
        )
        assertEquals(BufferUsage.entries.toSet(), expected.keys)
        expected.forEach { [role, flags] -> assertEquals(flags, vulkanBufferUsageFlags(setOf(role))) }
    }

    @Test
    fun combinedRolesDoNotAddUnrequestedPermissions() {
        assertEquals(0x82, vulkanBufferUsageFlags(setOf(BufferUsage.Vertex, BufferUsage.TransferDestination)))
        assertEquals(0x02, vulkanBufferUsageFlags(setOf(BufferUsage.TransferDestination, BufferUsage.QueryResolve)))
        assertEquals(0x1ff, vulkanBufferUsageFlags(BufferUsage.entries.toSet()))
    }

    @Test
    fun emptyUsageIsRejected() {
        assertFailsWith<IllegalArgumentException> { vulkanBufferUsageFlags(emptySet()) }
    }
}
