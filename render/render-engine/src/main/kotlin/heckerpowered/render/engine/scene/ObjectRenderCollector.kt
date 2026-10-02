/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

/**
 * Submits an object renderer within a drawing scope.
 *
 * The scope supplies placement and raster settings, so the object renderer can describe local
 * contributions without duplicating the caller's transform, clipping, or depth policy.
 */
interface ObjectRenderCollector {
    fun <S> submit(renderer: ObjectRenderer<S>, state: S)
}
