/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.collection.toUnmodifiableList

/**
 * Groups geometry parts that should be submitted with the same appearance and drawing scope.
 *
 * Parts keep their individual streams, primitive arrangements, and draw ranges. Drawing a group
 * submits its parts in list order; it does not combine their storage or promise a single GPU draw.
 * The part list is copied, while the geometry values and any referenced GPU storage are shared.
 */
class GeometryGroup(parts: List<RenderGeometry>) {
    val parts = parts.toUnmodifiableList()
}
