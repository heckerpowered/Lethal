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

/** Submits elements in argument order; a failure stops submission of the remaining elements. */
context(context: ObjectSubmitContext)
fun RenderElementCollector.submit(vararg elements: RenderElement) {
    for (element in elements) {
        submit(element)
    }
}
