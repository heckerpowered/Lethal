/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

/**
 * Receives local-space contributions under an explicit object placement.
 *
 * Implementations capture the supplied [ObjectSubmitContext] when an element is submitted.
 * Drawing facades also supply their active raster settings; the element itself remains reusable
 * independently of those settings.
 */
interface RenderElementCollector {
    context(context: ObjectSubmitContext)
    fun submit(element: RenderElement)
}
