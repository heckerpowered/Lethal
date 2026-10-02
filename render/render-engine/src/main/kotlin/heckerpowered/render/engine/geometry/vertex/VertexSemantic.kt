/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.vertex

/**
 * Names the meaning of a geometry attribute independently of its shader input location.
 *
 * Geometry and shader interfaces match semantics by name. A name does not encode a storage
 * format, coordinate space, or conversion rule; those contracts must agree at the use site.
 */
data class VertexSemantic(val name: String)
