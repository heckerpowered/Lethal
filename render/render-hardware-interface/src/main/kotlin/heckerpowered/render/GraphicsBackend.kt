/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.target.RenderTarget

interface GraphicsBackend {
    /**
     * The graphics device exposed by the host for the current rendering environment.
     *
     * It provides access to the operations and capabilities of the active graphics
     * backend. The device is a non-owning view of the host-managed graphics
     * environment rather than the owner of the underlying native graphics context.
     *
     * Resources and command encoders used during this rendering stage must be
     * compatible with this device. Rendering commands are encoded separately
     * through a [CommandEncoder].
     */
    val graphicsDevice: GraphicsDevice

    /**
     * The host's main render target for the current frame.
     *
     * In other words, this is the target that ultimately becomes the image seen
     * by the player. Intermediate render passes may use other targets before
     * composing their results into this one.
     */
    val mainRenderTarget: RenderTarget
}