/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.AffineTransformView
import heckerpowered.math.MatrixView
import heckerpowered.math.times
import heckerpowered.math.toMatrix4

/**
 * A prepared view of the scene.
 *
 * [worldToView] maps world-space positions into the view's coordinate system.
 * [projection] then maps view space into the renderer's clip space.
 */
class View(
    val worldToView: AffineTransformView,
    val projection: Projection,
) {
    /**
     * Maps world-space homogeneous coordinates directly into clip space.
     */
    val worldToClip: MatrixView =
        projection.matrix * worldToView.toMatrix4()
}