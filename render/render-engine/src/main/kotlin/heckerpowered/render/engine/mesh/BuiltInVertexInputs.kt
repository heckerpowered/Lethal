/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.mesh

import heckerpowered.render.geometry.BuiltInPrimitives
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.pipeline.vertex.vertexState
import heckerpowered.render.resource.buffer.asView

private val UnitQuadVertexState = vertexState {
    buffer(8) {
        attribute(location = 0, format = VertexFormat.Float32x2, offset = 0)
    }
}

/**
 * Exposes the device's shared unit quad as ordinary fixed-function vertex input.
 *
 * The quad coordinate is supplied as `Float32x2` at vertex-shader input location 0. These values
 * are parametric coordinates rather than local-space positions, so this input carries no spatial
 * bounds; a [MeshBatch] using it must provide its own conservative bound or use `null` when one
 * cannot be proven.
 *
 * The returned value is a lightweight immutable wrapper and does not own the device buffer.
 */
fun BuiltInPrimitives.unitQuadVertexInput(): VertexInput =
    VertexInput(UnitQuadVertexState, mapOf(0 to unitQuad.asView()))