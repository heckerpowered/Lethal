/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.geometry.index.IndexSource

/**
 * Selects geometry elements directly or through an index source paired with its element range.
 *
 * A vertex selection has no index source. An indexed selection always supplies both the source
 * and an indexed range; their byte capacity and eventual vertex accesses are checked during
 * preparation and by the RHI. The range counts elements rather than bytes and is retained without
 * copying. Index sources keep their existing host snapshots or borrowed resident resources.
 *
 * This selection does not choose vertex streams, shader parameters, or a primitive arrangement.
 * Those remain part of the geometry and can be reused with a different selection.
 */
sealed interface GeometrySelection {
    val range: DrawRange

    data class Vertices(override val range: VertexRange) : GeometrySelection

    data class Indexed(val source: IndexSource, override val range: IndexedRange) : GeometrySelection
}
