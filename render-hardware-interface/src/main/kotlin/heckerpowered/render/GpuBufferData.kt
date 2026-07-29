/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.memory.FloatElements
import heckerpowered.render.memory.IntElements

/**
 * Declares the std140 representation of typed data written into a [GpuBuffer].
 *
 * Every property must be an immutable abstract native field annotated with [FloatElements] or [IntElements]. The
 * generated API owns the temporary native allocation, exposes type-safe setters, writes it through the current
 * command encoder's uniform arena, and provides the matching [UniformBufferLayout]. Callers therefore do not repeat
 * field offsets or shader value types.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GpuBufferData
