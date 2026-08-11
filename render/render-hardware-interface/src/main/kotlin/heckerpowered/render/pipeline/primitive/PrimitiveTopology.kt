/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.primitive

/**
 * Determines how a sequence of vertices is assembled into geometric primitives.
 *
 * The topology does not change the vertex data itself. It defines how consecutive vertices are
 * grouped or connected before rasterization.
 */
enum class PrimitiveTopology {
    /**
     * Treats every vertex as an independent point.
     *
     * ```
     * vertices:  0   1   2   3
     *
     * points:   (0) (1) (2) (3)
     * ```
     *
     * This is useful when each input vertex represents an independent point-like object, such as
     * particles, debug markers, or point-cloud samples.
     */
    PointList,

    /**
     * Groups every two consecutive vertices into an independent line.
     *
     * ```
     * vertices: 0 1   2 3   4 5
     *
     * lines:   (0-1) (2-3) (4-5)
     * ```
     *
     * Lines do not share vertices implicitly. This is useful for unrelated line segments such as
     * debug rays, bounding-box edges, or independent strokes.
     */
    LineList,

    /**
     * Connects consecutive vertices into one continuous chain of lines.
     *
     * ```
     * vertices: 0 1 2 3
     *
     * lines:   (0-1)
     *             (1-2)
     *                 (2-3)
     * ```
     *
     * Each line after the first reuses the previous line's final vertex. This is useful for
     * polylines, paths, graphs, trails, and other connected line geometry.
     */
    LineStrip,

    /**
     * Groups every three consecutive vertices into an independent triangle.
     *
     * ```
     * vertices: 0 1 2   3 4 5
     *
     * triangles:
     *   (0, 1, 2)
     *   (3, 4, 5)
     * ```
     *
     * Triangles do not share vertices implicitly. This is the usual topology for ordinary meshes,
     * where vertex reuse is typically expressed explicitly through an index buffer.
     */
    TriangleList,

    /**
     * Connects consecutive vertices into a strip of triangles in which each triangle after the
     * first reuses an edge from the previous triangle.
     *
     * ```
     * vertices: 0 1 2 3 4
     *
     * triangles:
     *   (0, 1, 2)
     *      (1, 2, 3)
     *         (2, 3, 4)
     * ```
     *
     * A strip can represent connected surfaces using fewer vertex or index entries than an
     * equivalent triangle list because neighboring triangles reuse vertices implicitly.
     *
     * It is commonly useful for naturally strip-shaped geometry such as ribbons, trails, terrain
     * strips, or procedurally generated connected surfaces. General meshes often prefer
     * [TriangleList] because arbitrary connectivity is easier to express with an index buffer.
     */
    TriangleStrip,
}