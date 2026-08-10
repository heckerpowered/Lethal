/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.vertex

/**
 * Determines how elements of a vertex-buffer binding are selected while drawing.
 *
 * The step mode controls whether the current element changes between vertices or between instances
 * of the same geometry.
 */
enum class VertexStepMode {
    /**
     * Selects a new buffer element for each vertex.
     *
     * This mode is used for data that describes the individual vertices of geometry. For example,
     * a triangle may use a buffer containing:
     *
     * ```
     * element 0 -> position (-1, -1), uv (0, 0)
     * element 1 -> position ( 1, -1), uv (1, 0)
     * element 2 -> position ( 0,  1), uv (0.5, 1)
     * ```
     *
     * As the three vertices are processed, the binding supplies elements 0, 1, and 2 respectively.
     * Attributes in the [VertexBufferLayout] then extract the position, texture coordinates, or
     * other values from the selected element.
     *
     * Typical uses include positions, normals, texture coordinates, vertex colors, and other data
     * that may differ between vertices of the same geometry.
     */
    Vertex,


    /**
     * Selects one buffer element for each instance of the geometry and keeps that element selected
     * for every vertex belonging to the instance.
     *
     * This mode is useful when the same mesh is rendered multiple times with different per-object
     * data. For example, suppose one cube mesh is drawn as three instances and a separate buffer
     * contains:
     *
     * ```
     * element 0 -> transform = left,   color = red
     * element 1 -> transform = center, color = green
     * element 2 -> transform = right,  color = blue
     * ```
     *
     * The cube's ordinary per-vertex binding still advances through the vertices of the cube for
     * every instance. This binding instead behaves as:
     *
     * ```
     * instance 0 -> every cube vertex reads element 0 -> red cube on the left
     * instance 1 -> every cube vertex reads element 1 -> green cube in the center
     * instance 2 -> every cube vertex reads element 2 -> blue cube on the right
     * ```
     *
     * The geometry therefore needs to be stored only once, while this binding supplies the values
     * that differ between rendered instances.
     *
     * Typical uses include object transforms, colors, material indices, object identifiers, and
     * other values shared by all vertices of one instance.
     */
    Instance,
}