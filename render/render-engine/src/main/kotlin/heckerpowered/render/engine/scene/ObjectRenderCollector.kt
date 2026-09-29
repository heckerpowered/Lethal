/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

interface ObjectRenderCollector {
    context(context: ObjectSubmitContext)
    fun <S> submit(renderer: ObjectRenderer<S>, state: S)
}