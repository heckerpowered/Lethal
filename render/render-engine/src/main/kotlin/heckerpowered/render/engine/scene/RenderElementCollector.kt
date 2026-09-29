/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

interface RenderElementCollector {
    context(context: ObjectSubmitContext)
    fun submit(element: RenderElement)
}