/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * One operation in declaration order, with raster work still expressed as submitted elements.
 *
 * A raster operation requires pass preparation; transfer and buffer-copy selections are already
 * concrete. Their position in the stage defines when their results are available to later work.
 */
internal sealed interface RenderStageOperation {
    class ImageTransfer(val transfer: Transfer) : RenderStageOperation
    class Raster(val pass: RasterPass) : RenderStageOperation
    class CopyBuffer(val source: GpuBufferView, val destination: GpuBufferView) : RenderStageOperation
}
