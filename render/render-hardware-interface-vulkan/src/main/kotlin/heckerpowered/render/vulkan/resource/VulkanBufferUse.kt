/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.resource

/**
 * Prevents a buffer from closing while a transfer recording may reference it.
 *
 * The session acquires this use before emitting commands. It releases recorded
 * references only after the recording can no longer be submitted, with fence or
 * queue-completion proof for any submitted work.
 */
internal sealed interface VulkanBufferUse : VulkanResourceUse
