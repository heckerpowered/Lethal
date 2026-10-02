/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.engine.pass.RasterPassProcessor
import heckerpowered.render.resource.ResourceLifetime

/**
 * Prepares a stage's ordered passes and transfers for command recording.
 *
 * All passes are prepared before command recording begins. This makes their RHI resource
 * requirements available at pass entry without running drawing callbacks twice.
 *
 * Successful preparation leaves its temporary lifetime open for the executor. If preparation
 * fails, allocations registered during preparation are released before the failure propagates.
 */
internal object RenderStageProcessor {
    fun prepare(stage: RenderStage, raster: RasterPassProcessor): PreparedRenderStage {
        return ResourceLifetime.build {
            val operations = stage.operations.map { operation ->
                when (operation) {
                    is RenderStageOperation.ImageTransfer -> PreparedStageOperation.ImageTransfer(operation.transfer)
                    is RenderStageOperation.CopyBuffer -> PreparedStageOperation.CopyBuffer(operation.source, operation.destination)
                    is RenderStageOperation.Raster -> PreparedStageOperation.Pass(raster.prepare(operation.pass, this))
                }
            }
            PreparedRenderStage(operations, this)
        }
    }
}
