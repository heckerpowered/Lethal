/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

/**
 * Non-owning access to immutable geometry shared by a graphics device.
 *
 * The device owns every returned buffer. Rendering code may bind these buffers but must not close them.
 */
interface BuiltInPrimitives {
    /**
     * Four two-component vertices covering the parametric rectangle `[0, 1] x [-1, 1]` as a triangle strip.
     */
    val unitQuad: GpuBuffer
}
