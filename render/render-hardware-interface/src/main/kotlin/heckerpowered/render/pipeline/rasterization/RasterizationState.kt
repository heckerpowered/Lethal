/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.rasterization

import heckerpowered.render.pipeline.primitive.PrimitiveState

/**
 * Describes how assembled primitives are classified and converted into fragments.
 *
 * For polygon primitives, [frontFace] determines whether a projected triangle is front-facing or
 * back-facing. [cullMode] may then discard triangles of either orientation, and [polygonMode]
 * determines how each surviving polygon is rasterized.
 *
 * These options do not change how input vertices are assembled into primitives; that is controlled
 * by [PrimitiveState]. Point and line topologies use their own rasterization rules and are not
 * affected by face culling or polygon mode.
 */
data class RasterizationState(
    /**
     * Selects which polygon orientations are discarded before rasterization.
     *
     * Whether a triangle is front-facing or back-facing is determined by [frontFace].
     */
    val cullMode: CullMode = CullMode.None,

    /**
     * Selects the projected vertex winding that represents the front side of a polygon.
     *
     * Facing is determined in framebuffer coordinates after vertex processing and projection.
     */
    val frontFace: FrontFace = FrontFace.CounterClockwise,

    /**
     * Determines how surviving polygon primitives generate fragments.
     *
     * This does not change the primitive topology. For example, a triangle rendered using
     * [PolygonMode.Line] remains a triangle, but its edges are rasterized instead of its interior.
     */
    val polygonMode: PolygonMode = PolygonMode.Fill,
)
