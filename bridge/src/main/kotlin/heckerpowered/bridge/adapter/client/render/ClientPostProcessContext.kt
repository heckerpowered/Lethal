/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

interface ClientPostProcessContext {
    val isBloomSupported: Boolean

    fun applyBloomToScene(brightnessThreshold: Float)

    fun renderContentWithBloom(brightnessThreshold: Float, renderContent: () -> Unit)
}
