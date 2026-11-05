/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.depthstencil.DepthStencilState
import heckerpowered.render.pipeline.multisample.MultisampleState
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.rasterization.RasterizationState
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.shader.ShaderStages

/**
 * Immutable, backend-independent description used to create a render pipeline.
 *
 * A render pipeline combines programmable shader stages with the fixed-function state required
 * to interpret vertex input, assemble and rasterize primitives, process fragments, and write
 * render attachments.
 *
 * ### Coordinate conventions
 *
 * Graphics pipelines use one backend-independent clip-space and framebuffer coordinate system.
 * Vertex positions are clipped against:
 *
 * ```
 * -clipPosition.w <= clipPosition.x <= clipPosition.w
 * -clipPosition.w <= clipPosition.y <= clipPosition.w
 * 0 <= clipPosition.z <= clipPosition.w
 * ```
 *
 * After perspective division, normalized `x` and `y` range from `-1` to `1`, while depth ranges
 * from `0` to `1`. The viewport maps `(-1, -1)` to its upper-left edge and `(1, 1)` to its
 * lower-right edge.
 *
 * Framebuffer coordinates therefore use an upper-left origin, with `x` increasing to the right
 * and `y` increasing downward:
 *
 * ```
 * (0, 0) ──────────> x
 *    │
 *    │
 *    v
 *    y
 * ```
 *
 * Integer framebuffer coordinates identify pixel edges. The center of the upper-left pixel is
 * `(0.5, 0.5)`.
 *
 * The depth interval does not prescribe which endpoint represents the near plane. Conventional
 * projection normally maps nearer geometry toward zero, while reverse-depth projection maps it
 * toward one and selects a corresponding depth comparison.
 *
 * Polygon facing is evaluated from projected vertices in this framebuffer coordinate system.
 * Implementations must preserve these conventions even when the underlying graphics environment
 * uses different native coordinates.
 *
 * These conventions do not prescribe a texture-coordinate origin. Texture coordinates are shader
 * data whose interpretation is determined separately by the sampled texture and shader.
 *
 * This description may reference existing GPU resources, such as [shaders] and [layout], but does
 * not take ownership of them.
 *
 * Attachment formats, sample counts, and other compatibility-relevant state form part of the
 * pipeline description. Render passes using the resulting pipeline must provide compatible
 * attachments and state.
 */
data class RenderPipelineDescription(
    val label: String,
    val shaders: ShaderStages,
    /**
     * Resource interface against which the selected shader stages are checked.
     *
     * The layout supplies descriptor-slot and push-constant declarations, not the resources
     * or parameter values for a draw. Those values are provided through pass commands.
     *
     * `null` exposes no descriptor sets or push constants. It is useful for shaders that need
     * neither, and is not a request to infer an interface or skip resource validation. A shader
     * that uses these resources requires an explicit compatible layout.
     */
    val layout: PipelineLayout? = null,
    val vertex: VertexState = VertexState.Empty,
    val primitive: PrimitiveState = PrimitiveState(),
    val rasterization: RasterizationState = RasterizationState(),
    val multisample: MultisampleState = MultisampleState(),
    val colorTargets: List<ColorTargetState>,
    val depthStencil: DepthStencilState? = null,
)
