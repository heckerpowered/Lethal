/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.ColorClampMode

/**
 * Controls vertex-stage and fragment-stage shader output clamping as one
 * capability.
 *
 * The shared backend changes both states together before drawing, and adapters
 * expose this group only when both controls are available. Read-color clamping
 * is not included because the backend does not change readback clamping state.
 * Equivalent extension entry points are normalized by adapters.
 */
interface OpenGLShaderColorClampingFunctions {
    fun clampVertexColor(mode: ColorClampMode)
    fun clampFragmentColor(mode: ColorClampMode)
}