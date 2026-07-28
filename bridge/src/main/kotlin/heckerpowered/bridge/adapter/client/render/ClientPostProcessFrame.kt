/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

import heckerpowered.render.CommandScope
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.RenderTarget
import heckerpowered.render.TextureRenderTarget

/**
 * Opens RHI command recording for post-processing the current host frame.
 */
interface ClientPostProcessFrame {
    fun record(renderContent: ClientPostProcessScope.() -> Unit)
}

/**
 * RHI command scope whose source is the completed host scene.
 */
interface ClientPostProcessScope : CommandScope {
    val graphicsDevice: GraphicsDevice
    val source: TextureRenderTarget
    val target: RenderTarget
}
