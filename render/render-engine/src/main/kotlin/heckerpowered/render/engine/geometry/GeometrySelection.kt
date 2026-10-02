/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.geometry.index.IndexSource

/**
 * Selects geometry elements directly or through an index source paired with its element range.
 *
 * A vertex selection has no index source. An indexed selection supplies both its source and
 * range. For consuming draws, preparation and the RHI validate index-buffer and instance-data
 * ranges from metadata; they do not read stored index values. The caller must ensure each
 * non-restart index plus the signed base-vertex offset selects a valid vertex element.
 *
 * Ranges count elements rather than bytes and are retained without copying. Host index sources
 * keep their byte snapshots; resident sources retain views of existing GPU storage without
 * copying its contents or extending its validity.
 *
 * This selection does not choose vertex streams, shader parameters, or a primitive arrangement.
 * Those remain part of the geometry and can be reused with a different selection.
 */
sealed interface GeometrySelection {
    val range: DrawRange

    data class Vertices(override val range: VertexRange) : GeometrySelection

    data class Indexed(val source: IndexSource, override val range: IndexedRange) : GeometrySelection
}
