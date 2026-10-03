/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.Matrices
import heckerpowered.math.MatrixView
import heckerpowered.math.times
import heckerpowered.math.toMatrix4

/**
 * A prepared view of the scene.
 *
 * [frame] defines the validated view coordinate system; [worldToView] maps world positions into it.
 * [projection] then maps view space into the renderer's clip space. Construction captures its
 * plane distances and Double matrix so derived fields retain the same projection values.
 * Sampling is sequential and does not synchronize concurrent source changes.
 */
class View(
    val frame: ViewFrame,
    projection: Projection,
) {
    val projection: Projection = snapshotProjection(projection)
    val worldToView = frame.worldToView

    /**
     * Maps world-space homogeneous coordinates directly into clip space.
     */
    val worldToClip: MatrixView = this.projection.matrix * worldToView.toMatrix4()

    val frustum: Frustum = Frustum.fromWorldToClip(worldToClip)
}

private fun snapshotProjection(projection: Projection): Projection {
    val near = projection.nearPlane
    val far = projection.farPlane
    val matrix = Matrices.copyOf(projection.matrix)
    return object : Projection {
        override val nearPlane = near
        override val farPlane = far
        override val matrix = matrix
    }
}
