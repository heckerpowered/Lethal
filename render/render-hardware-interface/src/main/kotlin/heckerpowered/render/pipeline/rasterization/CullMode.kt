/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.rasterization

/**
 * Determines which polygon orientations are discarded before rasterization.
 *
 * A triangle is classified using [RasterizationState.frontFace]. Culling then decides whether
 * triangles of that orientation may continue to rasterization.
 *
 * Face culling applies to polygon primitives such as triangles. It does not discard primitives
 * produced by point or line topologies.
 */
enum class CullMode {
    /**
     * Keeps both front-facing and back-facing polygons.
     *
     * This is useful for open or intentionally double-sided surfaces such as leaves, sheets of
     * paper, or other geometry that should remain visible from either side.
     */
    None,

    /**
     * Discards front-facing polygons and keeps back-facing polygons.
     *
     * This is useful when rendering the inside of a closed surface, such as viewing a cube-shaped
     * environment from within it, or for specialized rendering passes that require only back
     * faces.
     */
    Front,

    /**
     * Discards back-facing polygons and keeps front-facing polygons.
     *
     * This is the usual choice for closed opaque meshes whose triangles use a consistent outward
     * winding. Surfaces facing away from the viewer are removed before they generate fragments.
     */
    Back,
}