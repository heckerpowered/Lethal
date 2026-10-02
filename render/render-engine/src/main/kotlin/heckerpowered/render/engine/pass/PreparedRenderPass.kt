/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.command.pass.RenderPassResources
import heckerpowered.render.engine.draw.PreparedDrawCommand
import heckerpowered.render.engine.prepare.BufferUpload
import java.util.*

/**
 * A render pass with the draw bindings and uploads needed for command recording already resolved.
 *
 * The upload and draw lists are immutable snapshots. Uploads execute before entering the RHI pass;
 * [resources] collects shader and geometry accesses from every draw so the backend can prepare them
 * before native rendering begins. Attachments remain part of [description].
 *
 * The declaration is an access upper bound, not synchronization between arbitrary draws. Draw order
 * is preserved, and an empty draw list still permits attachment clear, store, and resolve operations.
 * Referenced GPU contents and pass descriptions are not copied or kept alive by this value.
 */
internal class PreparedRenderPass(
    val description: RenderPassDescription,
    uploads: List<BufferUpload>,
    draws: List<PreparedDrawCommand>,
) {
    val uploads: List<BufferUpload> = Collections.unmodifiableList(ArrayList(uploads))
    val draws: List<PreparedDrawCommand> = Collections.unmodifiableList(ArrayList(draws))
    val resources = if (this.draws.isEmpty()) RenderPassResources.Empty else RenderPassResources(
        descriptors = this.draws.flatMap { it.descriptorSets.values },
        vertexBuffers = this.draws.flatMap { it.vertexBuffers.values },
        indexBuffers = this.draws.mapNotNull { it.indexInput?.view },
    )

    init {
        resources.validateFor(description)
    }

    /**
     * Records the prepared draws in order inside an already active RHI pass.
     *
     * The caller must first record [uploads] and enter the pass with [description] and [resources].
     * This method does not create a pass or skip one when the draw list is empty.
     */
    fun encode(pass: RenderPass) {
        draws.forEach { it.encode(pass) }
    }
}
