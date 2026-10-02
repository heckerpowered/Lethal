/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.material.parameter

/**
 * Identifies a value shared between parameter providers and a shader input interface.
 *
 * Names compare by string value. They are semantic keys rather than numeric descriptor addresses
 * or push-constant offsets; the input interface decides how each named value reaches the shader.
 */
data class ParameterName(val value: String)
