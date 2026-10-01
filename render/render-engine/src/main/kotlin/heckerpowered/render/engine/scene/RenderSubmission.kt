/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import java.util.*

/** One occurrence of an element, with captured spatial and raster state. */
class RenderSubmission(
    val element: RenderElement,
    objectState: ObjectSubmitContext,
    rasterScope: RasterScope = RasterScope(),
    val visibility: DepthMode = DepthMode.World,
    val sourceOrder: Int = 0,
) {
    val objectState = objectState.snapshot()
    val rasterScope = rasterScope.copy(scissors = Collections.unmodifiableList(ArrayList(rasterScope.scissors)))

    fun copy(element: RenderElement = this.element, visibility: DepthMode = this.visibility): RenderSubmission =
        RenderSubmission(element, objectState, rasterScope, visibility, sourceOrder)
}
