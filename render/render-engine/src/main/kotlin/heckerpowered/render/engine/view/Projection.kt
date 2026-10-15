/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.MatrixView

/** A stable projection with supplied clip distances and a captured view-to-clip matrix. */
class Projection(
    override val nearPlane: Double,
    override val farPlane: Double,
    matrix: MatrixView,
) : ProjectionView {
    override val matrix: MatrixView = matrix.snapshot()

    constructor(projection: ProjectionView) : this(projection.nearPlane, projection.farPlane, projection.matrix)

    override fun snapshot(): Projection = this
}
