/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.presentation

import heckerpowered.render.target.RenderTarget

/**
 * Gives rendering code access to the output selected by a window or host for presentation.
 *
 * Use the current target for direct scene rendering, final composition, or overlays on an image
 * prepared earlier in the same frame. Several render passes can contribute to the same output
 * before it is presented.
 *
 * Frame acquisition, command submission, and presentation are controlled outside this interface.
 */
interface PresentationSink {
    /**
     * Returns the target already prepared for the active rendering scope.
     *
     * The provider establishes this scope before invoking rendering code. Repeated calls within
     * that scope refer to the same selected output; this function does not advance to another frame.
     *
     * The returned target and its attachments may be used to record rendering commands only within
     * that scope. Obtain the current target again when entering a new scope rather than retaining
     * a previous frame's target.
     *
     * @throws IllegalStateException if no rendering scope with an available output is active.
     */
    fun currentTarget(): RenderTarget
}