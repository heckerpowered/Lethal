/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.pipeline.primitive.PrimitiveState

/**
 * Describes geometry whose vertices are produced by a shader rather than vertex-buffer attributes.
 *
 * [parameters] supplies numeric values and resources interpreted by the shader input contract
 * produced by its encoder. For example, a fullscreen shader can derive positions from vertex IDs with no
 * geometry parameters or vertex streams. An index source can still control the generated vertex
 * IDs; [selection] keeps an indexed range together with its source, or selects generated
 * vertex IDs directly without an index source.
 *
 * Geometry parameter names must not overlap appearance or view parameter names when they are
 * combined during preparation. Resource parameters reference existing GPU storage rather than
 * snapshotting its contents, and must remain valid through GPU completion.
 */
class ShaderGeometry(
    val parameters: ParameterValues,
    override val selection: GeometrySelection,
    override val primitive: PrimitiveState,
) : RenderGeometry
