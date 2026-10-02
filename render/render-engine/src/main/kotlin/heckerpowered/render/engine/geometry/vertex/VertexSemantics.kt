/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.vertex

object VertexSemantics {
    val Position = VertexSemantic("position")
    val UV = VertexSemantic("uv")
    val Normal = VertexSemantic("normal")
    val Color = VertexSemantic("color")
    val Light = VertexSemantic("light")
    val Overlay = VertexSemantic("overlay")
}