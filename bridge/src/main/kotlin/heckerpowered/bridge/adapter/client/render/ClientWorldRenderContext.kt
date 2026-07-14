/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.math.RotatorView
import heckerpowered.bridge.math.VectorView

data class RenderColor(val red: Float, val green: Float, val blue: Float, val alpha: Float)

interface ClientWorldRenderContext {
    val partialTick: Float
    val isBloomSupported: Boolean

    fun interpolateEyePosition(entity: EntityAccess): VectorView
    fun interpolateRotation(entity: EntityAccess): RotatorView
    fun drawLine(startPosition: VectorView, endPosition: VectorView, widthPixels: Float, color: RenderColor, lightningIntensity: Float, lightningSpikeDensity: Float, lightningAnimationFrequency: Float)
    fun renderContentWithBloom(brightnessThreshold: Float, renderContent: () -> Unit)
}
