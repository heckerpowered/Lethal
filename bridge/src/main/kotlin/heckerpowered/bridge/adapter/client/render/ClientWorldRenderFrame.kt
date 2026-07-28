/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

import heckerpowered.bridge.math.VectorView
import heckerpowered.render.CommandScope
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.Matrix4
import heckerpowered.render.RenderTarget

/**
 * Opens RHI command recording for world content during the current host frame.
 *
 * The frame owns command-recording lifetime. Rendering rules receive [ClientWorldRenderScope] and may append RHI
 * commands without being able to close the underlying encoder.
 */
interface ClientWorldRenderFrame {
    fun record(renderContent: ClientWorldRenderScope.() -> Unit)
}

/**
 * RHI command scope for one selected world render target.
 */
interface ClientWorldRenderScope : CommandScope {
    val graphicsDevice: GraphicsDevice
    val target: RenderTarget
    val cameraPosition: VectorView
    val modelViewProjectionMatrix: Matrix4
}
