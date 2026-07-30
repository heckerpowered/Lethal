/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

/**
 * Encodes ordinary world content before the world post-processing stage.
 */
interface ClientWorldRenderRule {
    fun ClientWorldRenderEncoder.render(context: ClientWorldRenderContext)
}
