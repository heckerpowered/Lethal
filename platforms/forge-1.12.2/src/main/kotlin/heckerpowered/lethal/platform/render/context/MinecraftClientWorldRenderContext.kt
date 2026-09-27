/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render.context

import heckerpowered.bridge.adapter.client.render.context.WorldRenderContext
import heckerpowered.bridge.math.VectorView
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.geometry.Matrix4
import heckerpowered.render.resource.target.RenderTarget

internal class MinecraftClientWorldRenderContext(
    override val graphicsDevice: GraphicsDevice,
    override val mainRenderTarget: RenderTarget,
    override val cameraPosition: VectorView,
    override val viewProjectionMatrix: Matrix4,
) : WorldRenderContext
