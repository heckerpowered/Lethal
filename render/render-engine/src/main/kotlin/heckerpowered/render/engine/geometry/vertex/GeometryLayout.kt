/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.vertex

import heckerpowered.render.engine.collection.toUnmodifiableList

/**
 * Describes which attribute semantics are available from a geometry's vertex streams.
 *
 * Stream order pairs each layout with the source at the same position in the geometry. Each
 * semantic may appear only once across all streams so shader inputs can be selected without
 * ambiguity. The stream list is copied at construction.
 *
 * This layout describes geometry data, not a particular shader. A shader input contract assigns
 * locations to the semantics it consumes; additional attributes can remain unused. An empty
 * layout is representable, but cannot satisfy a shader requiring vertex attributes.
 */
class GeometryLayout(streams: List<VertexStreamLayout>) {
    val streams: List<VertexStreamLayout> = streams.toUnmodifiableList()

    init {
        val semantics = this.streams.flatMap { it.attributes }.map { it.semantic }
        require(semantics.distinct().size == semantics.size) { "Geometry attributes must be unambiguous" }
    }
}
