/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.command.pass.Viewport

/** Final dynamic state: the viewport and intersected scissor are already resolved for this draw. */
internal data class DrawDynamicState(
    val viewport: Viewport,
    val scissor: ScissorRectangle,
    val stencilReference: UByte = 0u,
)