/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.command.pass.AttachmentLoadOperation
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.view.ViewParameters

/**
 * Places see-through and always-on-top geometry into separate passes with suitable depth behavior.
 *
 * See-through geometry uses a target without depth or stencil attachments. Always-on-top geometry
 * uses a freshly cleared depth attachment: scene depth no longer hides it, but its contributions
 * can still test and order depth among themselves. They are therefore submitted with ordinary scene
 * depth mode inside that pass.
 *
 * The order within each selected phase is preserved. See-through drawing runs first, then
 * always-on-top drawing, followed by [integration] when an always-on-top contribution exists.
 * Ordinary scene contributions are not appended by this helper and must be scheduled separately.
 */
class DepthPhases(
    val seeThroughTarget: RenderPassDescription,
    val onTopTarget: RenderPassDescription,
    val integration: RasterPass?,
) {
    fun append(stageBuilder: RenderStageBuilder, collection: RenderSubmissionList, inputs: ViewParameters) {
        val through = collection.submissions.filter { it.visibility == DepthMode.SeeThrough }
        val onTop = collection.submissions.filter { it.visibility == DepthMode.AlwaysOnTop }

        require(seeThroughTarget.depthAttachment == null && seeThroughTarget.stencilAttachment == null)
        require(onTopTarget.depthAttachment?.operation?.load is AttachmentLoadOperation.Clear)
        
        if (through.isNotEmpty()) stageBuilder.rasterPass(RasterPass(seeThroughTarget, RenderSubmissionList(through), inputs))
        if (onTop.isNotEmpty()) {
            val resolved = RenderSubmissionList(onTop.map { it.copy(visibility = DepthMode.Scene) })
            stageBuilder.rasterPass(RasterPass(onTopTarget, resolved, inputs))
            integration?.let(stageBuilder::rasterPass)
        }
    }
}
