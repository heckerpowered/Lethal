/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Matrices
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.GeometryProtocol
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.scene.drawing.RasterScope
import heckerpowered.render.engine.shader.program.MeshShading
import heckerpowered.render.engine.stage.RenderStageBuilder
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology

private val fullscreen = ShaderGeometry(
    GeometryProtocol("fullscreen"), ParameterValues(), null,
    DrawRange.vertices(3), PrimitiveState(PrimitiveTopology.TriangleList)
)

/** Appends a fullscreen draw using [description]'s attachment operations and render area. */
fun RenderStageBuilder.screen(description: RenderPassDescription, shading: MeshShading, composition: CompositingMode = CompositingMode.Replace) {
    rasterPass(screenPass(description, shading, composition))
}

/**
 * Declares bound shading over the pass's render area using a generated fullscreen triangle.
 *
 * The bound shader must accept the `fullscreen` geometry protocol. The shader
 * generates positions without vertex buffers or scene transforms, and drawing bypasses scene
 * depth tests. Attachment load and store behavior still comes from [description].
 */
fun screenPass(description: RenderPassDescription, shading: MeshShading, composition: CompositingMode = CompositingMode.Replace): RasterPass {
    val area = description.renderArea
    val inputs = ViewParameters(Matrices.Identity, area.width, area.height, CompareFunction.Always)
    val element = GeometryElement(fullscreen, shading, composition)
    val context = ObjectSubmitContext(AffineTransforms.Identity)
    val submission = RenderSubmission(element, context, RasterScope(), DepthMode.SeeThrough)
    val collection = RenderSubmissionList(listOf(submission))
    return RasterPass(description, collection, inputs)
}

