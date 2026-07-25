/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.adapter.client.render.ClientWorldRenderContext
import heckerpowered.bridge.adapter.client.render.ClientWorldRenderRule
import heckerpowered.bridge.adapter.client.render.RenderColor
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import heckerpowered.lethal.gameplay.client.BloomTest
import heckerpowered.lethal.gameplay.common.network.ZeusChainSegment

object ZeusChainEffect : ClientWorldRenderRule {
    private const val EFFECT_DURATION_NANOSECONDS = 100_000_000L
    private const val BEAM_WIDTH_PIXELS = 5F
    private const val BEAM_BRIGHTNESS_MULTIPLIER = 5F
    private val beamColor = RenderColor(0.56F, 0.64F, 1.0F, 1.0F)

    var LightningIntensity = 3.0F
        set(value) {
            require(value.isFinite() && value >= 0.0F) { "Lightning intensity must be finite and non-negative" }
            field = value
        }

    var LightningSpikeDensity = 4.0F
        set(value) {
            require(value.isFinite() && value > 0.0F) { "Lightning spike density must be finite and positive" }
            field = value
        }

    var LightningAnimationFrequency = 1.0F
        set(value) {
            require(value.isFinite() && value >= 0.0F) { "Lightning animation frequency must be finite and non-negative" }
            field = value
        }

    private val activeLines = mutableListOf<ZeusChainLine>()

    fun onInitialize() {
        RuleRegistry.register<ClientWorldRenderRule>(this)
    }

    fun play(chainSegments: List<ZeusChainSegment>) {
        require(chainSegments.isNotEmpty()) { "Zeus chain requires at least one segment" }

        val startedAtNanoseconds = System.nanoTime()
        for (segment in chainSegments) {
            activeLines += ZeusChainLine(segment.startPosition, segment.endPosition, startedAtNanoseconds)
        }
    }

    override fun onWorldRender(context: ClientWorldRenderContext) {
        val currentTimeNanoseconds = System.nanoTime()
        activeLines.removeAll { line -> line.opacityAt(currentTimeNanoseconds, EFFECT_DURATION_NANOSECONDS) <= 0.0F }
        if (activeLines.isEmpty()) return

        if (!context.isBloomSupported) {
            drawActiveLines(context, currentTimeNanoseconds, 1.0F)
            return
        }

        context.renderContentWithBloom(BloomTest.BrightnessThreshold) {
            drawActiveLines(context, currentTimeNanoseconds, BEAM_BRIGHTNESS_MULTIPLIER)
        }
    }

    internal fun clearActiveLines() {
        activeLines.clear()
    }

    private fun drawActiveLines(context: ClientWorldRenderContext, currentTimeNanoseconds: Long, brightnessMultiplier: Float) {
        for (line in activeLines) {
            val opacity = line.opacityAt(currentTimeNanoseconds, EFFECT_DURATION_NANOSECONDS)
            val color = RenderColor(beamColor.red * brightnessMultiplier, beamColor.green * brightnessMultiplier, beamColor.blue * brightnessMultiplier, opacity)
            context.drawLine(line.startPosition, line.endPosition, BEAM_WIDTH_PIXELS, color, LightningIntensity, LightningSpikeDensity, LightningAnimationFrequency)
        }
    }
}

internal data class ZeusChainLine(val startPosition: VectorView, val endPosition: VectorView, val startedAtNanoseconds: Long) {
    fun opacityAt(currentTimeNanoseconds: Long, durationNanoseconds: Long): Float {
        require(durationNanoseconds > 0)
        val elapsedNanoseconds = (currentTimeNanoseconds - startedAtNanoseconds).coerceAtLeast(0L)
        return (1.0 - elapsedNanoseconds.toDouble() / durationNanoseconds.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }
}
