/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.resource

import heckerpowered.render.resource.buffer.BufferUsage

private const val VK_BUFFER_USAGE_TRANSFER_SRC_BIT = 0x00000001
private const val VK_BUFFER_USAGE_TRANSFER_DST_BIT = 0x00000002
private const val VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT = 0x00000004
private const val VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT = 0x00000008
private const val VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT = 0x00000010
private const val VK_BUFFER_USAGE_STORAGE_BUFFER_BIT = 0x00000020
private const val VK_BUFFER_USAGE_INDEX_BUFFER_BIT = 0x00000040
private const val VK_BUFFER_USAGE_VERTEX_BUFFER_BIT = 0x00000080
private const val VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT = 0x00000100

/** Maps every RHI role to VkBufferUsageFlagBits from Vulkan 1.0, without implying host access. */
internal fun vulkanBufferUsageFlags(usage: Set<BufferUsage>): Int {
    require(usage.isNotEmpty()) { "Vulkan buffer requires at least one usage" }
    return usage.fold(0) { flags, role ->
        flags or when (role) {
            BufferUsage.TransferSource -> VK_BUFFER_USAGE_TRANSFER_SRC_BIT
            BufferUsage.TransferDestination, BufferUsage.QueryResolve -> VK_BUFFER_USAGE_TRANSFER_DST_BIT
            BufferUsage.UniformTexel -> VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT
            BufferUsage.StorageTexel -> VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT
            BufferUsage.Uniform -> VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
            BufferUsage.Storage -> VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
            BufferUsage.Index -> VK_BUFFER_USAGE_INDEX_BUFFER_BIT
            BufferUsage.Vertex -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
            BufferUsage.Indirect -> VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
        }
    }
}
