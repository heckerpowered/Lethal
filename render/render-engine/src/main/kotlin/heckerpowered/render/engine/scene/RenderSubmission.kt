/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

/**
 * One render element together with the object state under which it was submitted.
 *
 * A submission is independent of any particular view or render pass. Visibility,
 * pass participation, sorting, and RHI commands are determined later.
 */
internal data class RenderSubmission(
    val element: RenderElement,
    val context: ObjectSubmitContext,
)