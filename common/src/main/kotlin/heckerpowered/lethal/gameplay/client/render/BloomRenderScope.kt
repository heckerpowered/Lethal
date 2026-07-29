/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.adapter.client.render.ClientWorldRenderContext
import heckerpowered.bridge.adapter.client.render.ClientWorldRenderScope
import heckerpowered.render.RenderPass
import heckerpowered.render.RenderPassDescription
import heckerpowered.render.RenderTarget

internal interface BloomRenderRule {
    val hasBloomContent: Boolean

    fun onBloomRender(context: ClientWorldRenderContext, renderScope: BloomRenderScope)
}

internal class BloomRenderScope(private val source: ClientWorldRenderScope, override val target: RenderTarget, val isBloomEnabled: Boolean) : ClientWorldRenderScope by source {
    var hasRenderedContent = false
        private set

    override fun renderPass(description: RenderPassDescription, commands: RenderPass.() -> Unit) {
        hasRenderedContent = true
        source.renderPass(description, commands)
    }
}
