/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.scene.drawing.RasterScope

internal class ScopedRenderElementCollector(
    private val collector: RenderSubmissionCollector,
    private val rasterScope: RasterScope,
    private val depthMode: DepthMode,
) : RenderElementCollector {
    context(context: ObjectSubmitContext)
    override fun submit(element: RenderElement) {
        collector.collect(element, context, rasterScope, depthMode)
    }
}
