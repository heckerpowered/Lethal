/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.stage

import heckerpowered.render.engine.prepare.StaticVertexStreams
import heckerpowered.render.resource.ResourceLifetime

/**
 * A stage whose passes have concrete draw bindings and temporary upload storage.
 *
 * [temporaryLifetime] keeps preparation-created allocations valid until GPU completion is known.
 * The executor releases that lifetime after a successful wait, so this result cannot be executed
 * again after its temporary resources have been released. The value has no separate consumed state;
 * resource validity is determined by the lifetime itself.
 *
 * [staticStreamsToInitialize] is a fixed snapshot of upload targets, confirmed only when recording
 * and the following GPU wait both succeed. Recovery waits release resources without confirming it.
 */
internal class PreparedRenderStage(
    val operations: List<PreparedStageOperation>,
    val temporaryLifetime: ResourceLifetime,
    val staticStreamsToInitialize: List<StaticVertexStreams.Stream> = emptyList(),
)
