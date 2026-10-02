/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.support.collection.toUnmodifiableList

/**
 * Keeps an ordered snapshot of geometry parts for callers to bind and submit individually.
 *
 * The part list is copied and rejects structural mutation. Geometry values and their referenced
 * resources remain shared; each part keeps its streams, primitive arrangement, and draw range.
 * The group neither selects a shader nor submits draws. Callers choose the bindings, drawing
 * scopes, and submission order; grouping does not combine storage or promise a single GPU draw.
 */
class GeometryGroup(parts: List<RenderGeometry>) {
    val parts = parts.toUnmodifiableList()
}
