/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.render.engine.collection.toUnmodifiableList
import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.scene.drawing.RasterScope

/**
 * Records one occurrence of a render element at a particular placement and raster scope.
 *
 * Submitting a reusable element twice produces two occurrences without modifying its local
 * geometry. Construction copies the placement and scissor list, so later changes to those inputs
 * do not affect this occurrence. The element and any resources it refers to are retained by
 * reference; their contents and lifetimes are not snapshotted or extended.
 *
 * [sourceOrder] records collection order. A pass strategy may use it to preserve order or break
 * sorting ties; it is not a guarantee that every strategy draws in that order.
 */
class RenderSubmission(
    val element: RenderElement,
    objectState: ObjectSubmitContext,
    rasterScope: RasterScope = RasterScope(),
    val visibility: DepthMode = DepthMode.Scene,
    val sourceOrder: Int = 0,
) {
    val objectState = objectState.snapshot()
    val rasterScope = rasterScope.copy(scissors = rasterScope.scissors.toUnmodifiableList())

    fun copy(element: RenderElement = this.element, visibility: DepthMode = this.visibility): RenderSubmission =
        RenderSubmission(element, objectState, rasterScope, visibility, sourceOrder)
}
