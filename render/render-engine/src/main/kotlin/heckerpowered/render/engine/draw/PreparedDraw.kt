/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.RenderPass

/**
 * A resolved draw ready for recording in an active pass with its resource accesses declared.
 *
 * Geometry and shader binding membership, and push bytes, are immutable snapshots. GPU contents
 * and the pipeline description remain shared; keep them valid and the description unchanged through
 * recording and GPU completion. This value does not extend their lifetime.
 *
 * Each draw supplies every consumed shader input independently of prior draw state. Preparation
 * cannot prove that completeness from the public RHI reflection; backend draw validation still applies.
 */
internal class PreparedDraw(
    val pipeline: RenderPipelineDescription,
    val geometry: PreparedGeometry,
    val bindings: ShaderBindings,
    val state: DrawDynamicState,
) {
    init {
        geometry.validate(pipeline.vertex)
    }

    /**
     * Encodes this draw into an active pass whose resource declaration covers its bindings.
     * The caller enters the prepared pass without additional raster scopes. RHI scope operations
     * restore its viewport and clip after this command; stencil reference is selected by every draw.
     */
    fun encode(pass: RenderPass) {
        pass.withViewport(state.viewport) {
            withScissor(state.scissor) {
                setStencilReference(state.stencilReference)
                encodeDraw()
            }
        }
    }

    private fun RenderPass.encodeDraw() {
        bindPipeline(pipeline)
        bindGeometry(geometry)
        bindBindings(bindings)
        draw(geometry.invocation)
    }
}
