/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.pipeline.primitive.PrimitiveState

/**
 * Describes the data and element selection needed to draw one piece of geometry.
 *
 * [VertexGeometry] supplies attributes from vertex streams; [ShaderGeometry] supplies parameters
 * from which a shader produces its vertices. Both carry a primitive arrangement and one draw
 * range, so pass preparation can handle their storage without assuming a traditional mesh.
 * Shading and placement belong to the submitted render element rather than to this data.
 *
 * Geometry does not choose a shader or pass. A typed shader encoder binds it with its numeric
 * and resource inputs; preparation checks the consumed attributes and storage ranges.
 */
sealed interface RenderGeometry {
    val primitive: PrimitiveState
    val range: DrawRange
}
