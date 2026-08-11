/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.rasterization

import heckerpowered.render.pipeline.primitive.PrimitiveTopology

/**
 * Determines how polygon primitives are converted into fragments.
 *
 * Polygon mode is applied after primitive assembly and face culling. It does not replace the
 * pipeline's [PrimitiveTopology].
 */
enum class PolygonMode {
    /**
     * Rasterizes the interior of each polygon.
     *
     * This is the normal mode for rendering solid surfaces.
     */
    Fill,

    /**
     * Rasterizes the edges of each polygon as line segments.
     *
     * This is commonly used for wireframe visualization and mesh debugging. Every triangle edge is
     * shown, including edges introduced by the mesh's triangulation, so this mode does not produce
     * only the visible silhouette of an object.
     *
     * Support for non-solid polygon rasterization depends on the graphics device.
     */
    Line,
}