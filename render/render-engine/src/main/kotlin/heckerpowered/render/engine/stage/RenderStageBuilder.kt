/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Collects the passes and transfers declared in one `RenderEngine.stage` callback.
 *
 * Operations execute in insertion order. This builder does not reorder draws, merge passes, or
 * infer a dependency graph; declare a producing operation before the operation that reads it.
 *
 * Building copies the operation list and leaves collection available for further additions. GPU
 * resources and pass descriptions referenced by a snapshot must remain valid for its execution.
 */
class RenderStageBuilder internal constructor() {
    private val operations = ArrayList<RenderStageOperation>()
    private var imageOrdinal = 0

    /**
     * Allocates a name prefix for one effect invocation within this builder.
     *
     * The ordinal follows invocation order. Repeating that order in later stages lets an image store
     * reuse corresponding names; changing it can associate a name with a different invocation.
     */
    fun imageNamespace(base: String): String {
        return "$base.node.${imageOrdinal++}"
    }

    fun rasterPass(pass: RasterPass) {
        operations += RenderStageOperation.Raster(pass)
    }

    fun transfer(transfer: Transfer) {
        operations += RenderStageOperation.ImageTransfer(transfer)
    }

    fun copy(source: GpuBufferView, destination: GpuBufferView) {
        source.validateCopyTo(destination)
        operations += RenderStageOperation.CopyBuffer(source, destination)
    }

    internal fun build(): RenderStage = RenderStage(operations.toList())
}
