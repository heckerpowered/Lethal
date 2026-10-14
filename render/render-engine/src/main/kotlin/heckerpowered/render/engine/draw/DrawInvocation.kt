/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.IndexedRange
import heckerpowered.render.engine.geometry.VertexRange
import heckerpowered.render.engine.geometry.index.IndexSelection

/** A concrete draw invocation; indexed ranges always retain their prepared GPU index selection. */
internal sealed interface DrawInvocation {
    val range: DrawRange

    data class Vertices(override val range: VertexRange) : DrawInvocation
    data class Indexed(override val range: IndexedRange, val indices: IndexSelection) : DrawInvocation
}
