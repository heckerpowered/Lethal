/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.geometry.vertex.GeometryAttribute
import heckerpowered.render.engine.geometry.vertex.VertexSemantic
import heckerpowered.render.engine.geometry.vertex.VertexStreamLayout
import heckerpowered.render.pipeline.vertex.VertexFormat

/**
 * Declares one vertex attribute required by a shader, such as a position, normal, UV, or color.
 *
 * This is the shader's input contract. [VertexInterface] finds the supplying geometry attribute
 * by [semantic], independently of the shader variable's name, and maps it to [location]. For
 * example, a position semantic can supply shader input location 0 from any geometry stream.
 * The location identifies the vertex-shader input, not a vertex-buffer slot or a byte offset.
 * Input semantics and locations must each be unique within the containing [VertexInterface].
 *
 * The supplying [GeometryAttribute] defines the
 * stored format and byte offset within each element; its containing
 * [VertexStreamLayout] defines the stride and
 * stepping behavior. Those storage choices are preserved when the shader inputs are resolved.
 * Every requested semantic must exist, and [format] must exactly equal its stored format.
 * This check does not insert a format conversion; any normalization inherent in the chosen
 * [VertexFormat] still applies when the RHI fetches the attribute.
 */
data class AttributeInput(
    val semantic: VertexSemantic,
    val location: Int,
    val format: VertexFormat,
)
