/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.engine.pass.PreparedRenderPass
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * One stage operation ready for command recording.
 *
 * Prepared passes already carry their draws, uploads, and resource declarations. The operation
 * list preserves the ordering of the corresponding unprepared stage.
 */
internal sealed interface PreparedStageOperation {
    class ImageTransfer(val transfer: Transfer) : PreparedStageOperation
    class Pass(val pass: PreparedRenderPass) : PreparedStageOperation
    class CopyBuffer(val source: GpuBufferView, val destination: GpuBufferView) : PreparedStageOperation
}
