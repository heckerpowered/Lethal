/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.engine.pass.PreparedRenderPass
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * One stage operation ready for command recording.
 *
 * Prepared passes already carry their draws, uploads, and resource declarations. The operation
 * list preserves the ordering of the corresponding unprepared stage.
 */
internal sealed interface PreparedStageOperation {
    fun encode(encoder: CommandEncoder)

    class ImageTransfer(private val transfer: Transfer) : PreparedStageOperation {
        override fun encode(encoder: CommandEncoder) = transfer.encode(encoder)
    }

    class Pass(private val pass: PreparedRenderPass) : PreparedStageOperation {
        override fun encode(encoder: CommandEncoder) {
            pass.uploads.forEach { it.encode(encoder) }
            // Empty draws can still clear, store, or resolve attachments.
            encoder.renderPass(pass.description, pass.resources) { pass.encode(this) }
        }
    }

    class CopyBuffer(private val source: GpuBufferView, private val destination: GpuBufferView) : PreparedStageOperation {
        override fun encode(encoder: CommandEncoder) = encoder.copyBuffer(source, destination)
    }
}
