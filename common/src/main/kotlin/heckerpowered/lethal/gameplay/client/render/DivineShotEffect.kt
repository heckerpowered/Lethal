/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.adapter.client.render.ClientWorldRenderContext
import heckerpowered.bridge.adapter.client.render.ClientWorldRenderRule
import heckerpowered.bridge.adapter.client.render.RenderColor
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.math.*
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import heckerpowered.lethal.gameplay.client.BloomTest

object DivineShotEffect : ClientWorldRenderRule {
    private const val EFFECT_DURATION_NANOSECONDS = 80_000_000L
    private const val BEAM_RANGE_BLOCKS = 1024.0
    private const val MUZZLE_FORWARD_OFFSET_BLOCKS = 0.9
    private const val MUZZLE_HORIZONTAL_OFFSET_BLOCKS = 0.28
    private const val MUZZLE_VERTICAL_OFFSET_BLOCKS = -0.2
    private const val BEAM_WIDTH_PIXELS = 5F
    private const val BEAM_BRIGHTNESS_MULTIPLIER = 5F
    private val BeamColor = RenderColor(0.56F, 0.64F, 1.0F, 1.0F)

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

    private val activeLines = mutableListOf<DivineShotLine>()

    fun onInitialize() {
        RuleRegistry.register<ClientWorldRenderRule>(this)
    }

    fun play(player: PlayerAccess, isMainHand: Boolean) {
        activeLines += createShotLine(player, isMainHand, System.nanoTime())
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
            val color = RenderColor(BeamColor.red * brightnessMultiplier, BeamColor.green * brightnessMultiplier, BeamColor.blue * brightnessMultiplier, opacity)
            context.drawLine(line.startPosition(context), line.endPosition, BEAM_WIDTH_PIXELS, color, LightningIntensity, LightningSpikeDensity, LightningAnimationFrequency)
        }
    }

    internal fun createShotLine(player: PlayerAccess, isMainHand: Boolean, startedAtNanoseconds: Long): DivineShotLine {
        val viewDirection = player.viewVector.normalized()
        val muzzlePosition = calculateMuzzlePosition(player.eyePosition, viewDirection, player.yaw, isMainHand)
        val endPosition = muzzlePosition + viewDirection * BEAM_RANGE_BLOCKS
        return DivineShotLine(player, endPosition, isMainHand, startedAtNanoseconds)
    }

    internal fun calculateMuzzlePosition(eyePosition: VectorView, viewDirection: VectorView, yawDegrees: Double, isMainHand: Boolean): VectorView {
        val horizontalViewDirection = Geometry.rotator(0.0, yawDegrees).toViewVector()
        val rightDirection = horizontalViewDirection.cross(Vectors.UnitY).normalized()
        val upDirection = rightDirection.cross(viewDirection).normalized()
        val handDirection = if (isMainHand) 1.0 else -1.0
        return eyePosition +
                viewDirection * MUZZLE_FORWARD_OFFSET_BLOCKS +
                rightDirection * (MUZZLE_HORIZONTAL_OFFSET_BLOCKS * handDirection) +
                upDirection * MUZZLE_VERTICAL_OFFSET_BLOCKS
    }
}

internal data class DivineShotLine(val player: PlayerAccess, val endPosition: VectorView, val isMainHand: Boolean, val startedAtNanoseconds: Long) {
    fun startPosition(context: ClientWorldRenderContext): VectorView {
        val interpolatedRotation = context.interpolateRotation(player)
        return DivineShotEffect.calculateMuzzlePosition(context.interpolateEyePosition(player), interpolatedRotation.toViewVector(), interpolatedRotation.yaw, isMainHand)
    }

    fun opacityAt(currentTimeNanoseconds: Long, durationNanoseconds: Long): Float {
        require(durationNanoseconds > 0)
        val elapsedNanoseconds = (currentTimeNanoseconds - startedAtNanoseconds).coerceAtLeast(0L)
        return (1.0 - elapsedNanoseconds.toDouble() / durationNanoseconds.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }
}
