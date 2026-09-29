/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

/**
 * Converts immutable rendering state for one object into renderer submissions.
 *
 * Implementations operate in the object's local coordinate system. World
 * placement is supplied by [ObjectSubmitContext].
 */
interface ObjectRenderer<in S> {
    context(context: ObjectSubmitContext)
    fun submit(state: S, collector: RenderElementCollector)
}