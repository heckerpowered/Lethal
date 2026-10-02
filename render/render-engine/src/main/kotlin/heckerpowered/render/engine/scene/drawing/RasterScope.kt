/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene.drawing

import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.command.pass.Viewport

/**
 * Carries dynamic raster settings shared by a group of submissions.
 *
 * A missing [viewport] uses the consuming pass's viewport. [scissors] are nested in list order
 * and intersect in framebuffer coordinates; they do not transform geometry. [stencilReference]
 * supplies the reference value for the pipeline's stencil test, without enabling that test.
 *
 * This value retains the supplied list. [heckerpowered.render.engine.scene.RenderSubmission]
 * takes an independent list snapshot when recording an occurrence.
 */
data class RasterScope(
    val viewport: Viewport? = null,
    val scissors: List<ScissorRectangle> = emptyList(),
    val stencilReference: UByte = 0u,
)
