/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Matrices
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.scene.drawing.RasterScope
import heckerpowered.render.engine.stage.RenderStageBuilder
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.pipeline.depthstencil.CompareFunction

/** Appends the bound draw using [description]; fullscreen coverage is a requirement on [element]. */
fun RenderStageBuilder.screen(description: RenderPassDescription, element: GeometryElement, composition: CompositingMode = element.composition) {
    rasterPass(screenPass(description, element, composition))
}

/**
 * Declares an already-bound geometry contribution over the pass's render area.
 *
 * Drawing uses identity placement and bypasses scene depth tests. It does not force fullscreen
 * coverage or validate the shader's position output. The encoder chooses geometry
 * and draw selection; attachment load and store behavior still comes from [description].
 */
fun screenPass(description: RenderPassDescription, element: GeometryElement, composition: CompositingMode = element.composition): RasterPass {
    val area = description.renderArea
    val inputs = ViewParameters(Matrices.Identity, area.width, area.height, CompareFunction.Always)
    val context = ObjectSubmitContext(AffineTransforms.Identity)
    val submission = RenderSubmission(element.copy(composition = composition), context, RasterScope(), DepthMode.SeeThrough)
    val collection = RenderSubmissionList(listOf(submission))
    return RasterPass(description, collection, inputs)
}

