/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.MatrixView
import heckerpowered.math.times
import heckerpowered.math.toMatrix4

/**
 * A prepared view of the scene.
 *
 * [frame] defines the validated view coordinate system; [worldToView] maps world positions into it.
 * [projection] then maps view space into the renderer's clip space.
 */
class View(
    val frame: ViewFrame,
    val projection: Projection,
) {
    val worldToView = frame.worldToView

    /**
     * Maps world-space homogeneous coordinates directly into clip space.
     */
    val worldToClip: MatrixView =
        projection.matrix * worldToView.toMatrix4()
}
