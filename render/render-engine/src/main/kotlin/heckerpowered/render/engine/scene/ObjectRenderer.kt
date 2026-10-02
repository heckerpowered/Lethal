/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

/**
 * Describes one object's visual contributions using its rendering state.
 *
 * An object can emit several [RenderElement] values, such as a solid core and a transparent glow.
 * Implementations emit geometry in local coordinates; [ObjectSubmitContext] supplies placement
 * for the collector to capture. Applying that placement to vertices as well would transform the
 * object twice.
 *
 * Submission is synchronous. The drawing facade supplies its current context and raster scope,
 * then pass preparation later turns the collected elements into GPU draws. The engine does not
 * copy [S]; callers provide stable rendering state, and emitted elements must remain valid while
 * their collected submissions may be processed.
 */
interface ObjectRenderer<in S> {
    context(context: ObjectSubmitContext)
    fun submit(state: S, collector: RenderElementCollector)
}
