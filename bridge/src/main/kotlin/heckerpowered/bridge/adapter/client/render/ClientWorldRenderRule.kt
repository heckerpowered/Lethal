/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

interface ClientWorldRenderRule {
    fun onWorldRender(context: ClientWorldRenderContext, frame: ClientWorldRenderFrame)
}
