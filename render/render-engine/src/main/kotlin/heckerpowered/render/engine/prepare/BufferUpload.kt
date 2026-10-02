/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.prepare

import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.engine.geometry.UploadData
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Records a host byte snapshot into a selected GPU buffer range before its consumers execute.
 *
 * [destination] must select exactly [bytes]' length. The bytes are copied into temporary native
 * staging during [encode]; that staging is valid only for the call. The RHI consumes or saves it
 * before returning, while completion of the destination's GPU writes is a separate concern.
 *
 * The destination buffer is borrowed and requires transfer-destination access at recording time.
 * This value does not allocate or release that buffer.
 */
class BufferUpload(
    val destination: GpuBufferView,
    val bytes: UploadData,
) {
    init {
        require(destination.sizeBytes == bytes.sizeBytes.toLong())
    }

    fun encode(encoder: CommandEncoder) {
        bytes.consumeNative(encoder.memoryStack) { address -> encoder.writeBuffer(destination, address) }
    }
}
