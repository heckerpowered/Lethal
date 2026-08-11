/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.rasterization

/**
 * Determines which projected triangle winding is classified as front-facing.
 *
 * Winding is evaluated from vertex positions in the RHI's framebuffer coordinate system after
 * vertex processing, projection, and viewport mapping. Vertex normals do not participate in this
 * classification.
 *
 * The diagrams below show vertex placement as it appears in that framebuffer coordinate system.
 * Backend implementations must preserve this interpretation even when their native coordinate
 * conventions differ.
 *
 * Reversing the order of two triangle vertices reverses its facing. Transformations that mirror
 * geometry, such as a negative scale along one axis, may have the same effect.
 */
enum class FrontFace {
    /**
     * Treats triangles whose projected vertices follow a counter-clockwise order as front-facing.
     *
     * For example:
     *
     * ```
     *           2
     *          / \
     *         /   \
     *        0-----1
     *
     * vertex order: 0 -> 1 -> 2 -> 0
     * ```
     *
     * Following the vertices from `0` through `1` and `2` moves counter-clockwise around the
     * triangle. With this mode, the triangle is therefore front-facing.
     *
     * This is the conventional default for geometry authored with counter-clockwise outward-facing
     * triangles.
     */
    CounterClockwise,

    /**
     * Treats triangles whose projected vertices follow a clockwise order as front-facing.
     *
     * For example:
     *
     * ```
     *           1
     *          / \
     *         /   \
     *        0-----2
     *
     * vertex order: 0 -> 1 -> 2 -> 0
     * ```
     *
     * Following the vertices from `0` through `1` and `2` moves clockwise around the triangle. With
     * this mode, the triangle is therefore front-facing.
     *
     * This is useful for geometry authored with clockwise outward-facing triangles or when a
     * coordinate conversion reverses the projected winding.
     */
    Clockwise,
}
