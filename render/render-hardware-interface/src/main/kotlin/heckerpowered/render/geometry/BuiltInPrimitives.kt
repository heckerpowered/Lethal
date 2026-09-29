/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.geometry

import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.shader.ShaderLibrary

/**
 * Non-owning access to immutable geometry shared by a graphics device.
 *
 * The device owns every returned buffer. Rendering code may bind these buffers but must not close them.
 * Device and pass accessors expose the same buffer objects while that device remains valid, so
 * a range declared before a pass also covers the shared geometry selected within it.
 */
interface BuiltInPrimitives : ShaderLibrary {
    /**
     * Four tightly packed Float32x2 vertices covering `[0, 1] x [-1, 1]` as a triangle strip.
     * Each vertex contains x followed by y, with an eight-byte stride and no padding.
     * These are parametric coordinates, not a full-screen clip-space rectangle.
     *
     * [heckerpowered.render.command.pass.drawUnitQuad] binds and draws this geometry without
     * requiring a renderer-local buffer field. Its vertex-input access must still be declared
     * before entering the pass.
     */
    val unitQuad: GpuBuffer
}