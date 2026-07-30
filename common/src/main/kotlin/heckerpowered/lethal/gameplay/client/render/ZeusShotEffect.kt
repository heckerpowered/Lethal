/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.adapter.client.render.ClientWorldRenderContext
import heckerpowered.bridge.adapter.client.render.ClientWorldRenderFrame
import heckerpowered.bridge.adapter.client.render.ClientWorldRenderRule
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.math.*
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import heckerpowered.render.Color

internal object ZeusShotEffect : ClientWorldRenderRule {
    private const val EFFECT_DURATION_NANOSECONDS = 80_000_000L
    private const val BEAM_RANGE_BLOCKS = 1024.0
    private const val BEAM_WIDTH_PIXELS = 5F
    private const val BEAM_BRIGHTNESS_MULTIPLIER = 5F
    private val FirstPersonMuzzleOffset = MuzzleOffset(0.9, 0.28, -0.2)
    private val ThirdPersonMuzzleOffset = MuzzleOffset(2.45, 0.2, -0.3)
    private val BeamColor = Color(0.56F, 0.64F, 1.0F, 1.0F)

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

    private val activeLines = mutableListOf<ZeusShotLine>()

    init {
        RuleRegistry.register<ClientWorldRenderRule>(this)
    }

    fun onInitialize() {
    }

    fun play(player: PlayerAccess, isMainHand: Boolean) {
        activeLines += createShotLine(player, isMainHand, System.nanoTime())
    }

    override fun onWorldRender(context: ClientWorldRenderContext, frame: ClientWorldRenderFrame) {
        val currentTimeNanoseconds = System.nanoTime()
        activeLines.removeAll { line -> line.opacityAt(currentTimeNanoseconds, EFFECT_DURATION_NANOSECONDS) <= 0.0F }
        if (activeLines.isEmpty()) return

        frame.encode {
            val brightnessMultiplier = if (BloomEffect.isSupported(graphicsDevice)) BEAM_BRIGHTNESS_MULTIPLIER else 1.0F
            val style = ElectricLineParameters(BEAM_WIDTH_PIXELS, LightningIntensity, LightningSpikeDensity, LightningAnimationFrequency)
            val lines = activeLines.asSequence().map { line ->
                val opacity = line.opacityAt(currentTimeNanoseconds, EFFECT_DURATION_NANOSECONDS)
                val color = Color(BeamColor.red * brightnessMultiplier, BeamColor.green * brightnessMultiplier, BeamColor.blue * brightnessMultiplier, opacity)
                ElectricLine(line.startPosition(context), line.endPosition, color)
            }
            renderPass(bloomPass("Electric world lines")) {
                drawElectricLine(style, lines)
            }
        }
    }

    internal fun clearActiveLines() {
        activeLines.clear()
    }

    internal fun createShotLine(player: PlayerAccess, isMainHand: Boolean, startedAtNanoseconds: Long): ZeusShotLine {
        val viewDirection = player.viewVector.normalized()
        val muzzlePosition = calculateMuzzlePosition(player.eyePosition, viewDirection, player.yaw, isMainHand, true)
        val endPosition = muzzlePosition + viewDirection * BEAM_RANGE_BLOCKS
        return ZeusShotLine(player, endPosition, isMainHand, startedAtNanoseconds)
    }

    internal fun calculateMuzzlePosition(eyePosition: VectorView, viewDirection: VectorView, yawDegrees: Double, isMainHand: Boolean, isFirstPerson: Boolean): VectorView {
        val muzzleOffset = if (isFirstPerson) FirstPersonMuzzleOffset else ThirdPersonMuzzleOffset
        val horizontalViewDirection = Geometry.rotator(0.0, yawDegrees)
            .toViewVector()
        val rightDirection = horizontalViewDirection
            .cross(Vectors.UnitY)
            .normalized()
        val upDirection = rightDirection
            .cross(viewDirection)
            .normalized()
        val handDirection = if (isMainHand) 1.0 else -1.0
        return eyePosition +
                viewDirection * muzzleOffset.forwardBlocks +
                rightDirection * (muzzleOffset.horizontalBlocks * handDirection) +
                upDirection * muzzleOffset.verticalBlocks
    }
}

private data class MuzzleOffset(
    val forwardBlocks: Double,
    val horizontalBlocks: Double,
    val verticalBlocks: Double,
)

internal data class ZeusShotLine(
    val player: PlayerAccess,
    val endPosition: VectorView,
    val isMainHand: Boolean,
    val startedAtNanoseconds: Long,
) {
    fun startPosition(context: ClientWorldRenderContext): VectorView {
        val interpolatedRotation = context.interpolateRotation(player)
        val eyePosition = context.interpolateEyePosition(player)
        val viewVector = interpolatedRotation.toViewVector()
        val rendersFirstPerson = context.rendersEntityInFirstPerson(player)
        return ZeusShotEffect.calculateMuzzlePosition(eyePosition, viewVector, interpolatedRotation.yaw, isMainHand, rendersFirstPerson)
    }

    fun opacityAt(currentTimeNanoseconds: Long, durationNanoseconds: Long): Float {
        require(durationNanoseconds > 0)
        val elapsedNanoseconds = (currentTimeNanoseconds - startedAtNanoseconds).coerceAtLeast(0L)
        return (1.0 - elapsedNanoseconds.toDouble() / durationNanoseconds.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
    }
}
