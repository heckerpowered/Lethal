/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.math.AffineTransformView

/**
 * Spatial state captured for an object submitted to the renderer.
 *
 * [localToWorld] transforms positions from the object's local coordinate
 * system into world space. View and projection transforms belong to the
 * view rather than to the object.
 */
class ObjectSubmitContext(
    val localToWorld: AffineTransformView,
)