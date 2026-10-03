/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.resource

import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.BufferUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VulkanBufferAllocationPlanTest {
    private val description = BufferDescription("vertices", 100, setOf(BufferUsage.Vertex, BufferUsage.TransferDestination))
    private val requirements = VulkanBufferMemoryRequirements(128, 64, 0b111)

    @Test
    fun nativeSizeRatherThanRhiCapacityDeterminesAllocationSize() {
        val plan = planVulkanBufferAllocation(description, requirements, intArrayOf(1), emptySet(), emptySet())
        assertEquals(128L, plan.allocationSizeBytes)
        assertEquals(1, plan.memoryPropertyFlags)
        plan.validateBinding(128, 0)
        assertFailsWith<IllegalArgumentException> { plan.validateBinding(100, 0) }
    }

    @Test
    fun preferredPropertiesCannotOverrideNativeTypeBitsOrRequiredHostVisibility() {
        val plan = planVulkanBufferAllocation(description, requirements.copy(memoryTypeBits = 0b110), intArrayOf(3, 1, 2), setOf(VulkanMemoryProperty.HostVisible), setOf(VulkanMemoryProperty.DeviceLocal))
        assertEquals(2, plan.memoryTypeIndex)
    }

    @Test
    fun missingPreferredPropertiesDoNotMakeEligibleMemoryUnsupported() {
        val plan = planVulkanBufferAllocation(description, requirements, intArrayOf(2), setOf(VulkanMemoryProperty.HostVisible), setOf(VulkanMemoryProperty.HostCoherent))
        assertEquals(0, plan.memoryTypeIndex)
        assertEquals(2, plan.memoryPropertyFlags)
    }

    @Test
    fun preferenceCountsMatchedPropertiesAndKeepsNativeOrderOnTies() {
        val plan = planVulkanBufferAllocation(description, requirements, intArrayOf(2, 6, 6), setOf(VulkanMemoryProperty.HostVisible), setOf(VulkanMemoryProperty.HostCoherent))
        assertEquals(1, plan.memoryTypeIndex)
    }

    @Test
    fun missingRequiredPropertiesFailInsteadOfFallingBack() {
        assertFailsWith<UnsupportedOperationException> {
            planVulkanBufferAllocation(description, requirements, intArrayOf(1), setOf(VulkanMemoryProperty.HostVisible), emptySet())
        }
    }

    @Test
    fun signedBit31IsAValidNativeMemoryTypeBit() {
        val types = IntArray(32) { 1 }
        val plan = planVulkanBufferAllocation(description, requirements.copy(memoryTypeBits = Int.MIN_VALUE), types, emptySet(), emptySet())
        assertEquals(31, plan.memoryTypeIndex)
    }

    @Test
    fun nativeBitsOutsideReportedMemoryTypesCannotSelectInventedMemory() {
        assertFailsWith<UnsupportedOperationException> {
            planVulkanBufferAllocation(description, requirements.copy(memoryTypeBits = 0b100), intArrayOf(1, 1), emptySet(), emptySet())
        }
    }

    @Test
    fun lazyProtectedAndExtensionMemoryNeedAnotherAllocationPath() {
        for (properties in listOf(0x11, 0x21, 0x41, 0x81, 0x101)) {
            assertFailsWith<UnsupportedOperationException> {
                planVulkanBufferAllocation(description, requirements, intArrayOf(properties), emptySet(), emptySet())
            }
        }
    }

    @Test
    fun bindingChecksAlignmentRangeAndOverflowWithoutAddingSizes() {
        val plan = planVulkanBufferAllocation(description, requirements, intArrayOf(1), emptySet(), emptySet())
        plan.validateBinding(192, 64)
        assertFailsWith<IllegalArgumentException> { plan.validateBinding(192, 1) }
        assertFailsWith<IllegalArgumentException> { plan.validateBinding(191, 64) }
        assertFailsWith<IllegalArgumentException> { plan.validateBinding(128, -64) }
        assertFailsWith<IllegalArgumentException> { plan.validateBinding(128, 128) }
        assertFailsWith<IllegalArgumentException> { plan.validateBinding(Long.MAX_VALUE, Long.MAX_VALUE - 63) }
    }

    @Test
    fun dedicatedAllocationRejectsOtherwiseValidNonzeroBindingOffset() {
        val plan = planVulkanBufferAllocation(description, requirements.copy(requiresDedicatedAllocation = true), intArrayOf(1), emptySet(), emptySet())
        plan.validateBinding(128, 0)
        assertFailsWith<IllegalArgumentException> { plan.validateBinding(192, 64) }
    }

    @Test
    fun malformedNativeRequirementsAndTypeTablesAreRejected() {
        assertFailsWith<IllegalArgumentException> { VulkanBufferMemoryRequirements(0, 64, 1) }
        for (alignment in listOf(0L, -1L, 3L)) {
            assertFailsWith<IllegalArgumentException> { VulkanBufferMemoryRequirements(128, alignment, 1) }
        }
        assertFailsWith<IllegalArgumentException> { VulkanBufferMemoryRequirements(128, 64, 0) }
        assertFailsWith<IllegalArgumentException> {
            planVulkanBufferAllocation(description, requirements.copy(sizeBytes = 99), intArrayOf(1), emptySet(), emptySet())
        }
        for (count in listOf(0, 33)) {
            assertFailsWith<IllegalArgumentException> {
                planVulkanBufferAllocation(description, requirements, IntArray(count), emptySet(), emptySet())
            }
        }
    }

    @Test
    fun planKeepsSelectedPropertiesWhenNativeScratchArrayIsReused() {
        val types = intArrayOf(3)
        val plan = planVulkanBufferAllocation(description, requirements, types, emptySet(), emptySet())
        types[0] = 0
        assertEquals(3, plan.memoryPropertyFlags)
    }
}
