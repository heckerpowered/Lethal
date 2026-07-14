/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.adapter.client.render.ClientWorldRenderContext
import heckerpowered.bridge.adapter.client.render.RenderColor
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RotatorView
import heckerpowered.bridge.math.VectorView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeusShotEffectTest {
    @Test
    fun `simultaneous shots retain both hand lines`() {
        val eyePosition = Geometry.vector(0.0, 2.0, 0.0)
        val player = createPlayer(eyePosition, Geometry.vector(0.0, 0.0, 1.0), 0.0)
        val context = TestWorldRenderContext(eyePosition, Geometry.rotator(0.0, 0.0), true)

        try {
            ZeusShotEffect.clearActiveLines()
            ZeusShotEffect.play(player, true)
            ZeusShotEffect.play(player, false)
            ZeusShotEffect.onWorldRender(context)

            assertEquals(2, context.drawnLines.size)
            assertEquals(1, context.bloomRenderCount)
            assertVectorEquals(Geometry.vector(-0.28, 1.8, 0.9), context.drawnLines[0].startPosition)
            assertVectorEquals(Geometry.vector(0.28, 1.8, 0.9), context.drawnLines[1].startPosition)
        } finally {
            ZeusShotEffect.clearActiveLines()
        }
    }

    @Test
    fun `off hand muzzle mirrors the horizontal offset`() {
        val player = createPlayer(Geometry.vector(0.0, 2.0, 0.0), Geometry.vector(0.0, 0.0, 1.0), 0.0)
        val shotLine = ZeusShotEffect.createShotLine(player, false, 0L)
        val context = TestWorldRenderContext(Geometry.vector(0.0, 2.0, 0.0), Geometry.rotator(0.0, 0.0))

        assertVectorEquals(Geometry.vector(0.28, 1.8, 0.9), shotLine.startPosition(context))
    }

    @Test
    fun `muzzle follows the interpolated eye while the endpoint remains fixed`() {
        val initialEyePosition = Geometry.vector(0.0, 2.0, 0.0)
        val player = createPlayer(initialEyePosition, Geometry.vector(0.0, 0.0, 1.0), 0.0)
        val shotLine = ZeusShotEffect.createShotLine(player, true, 0L)
        val context = TestWorldRenderContext(initialEyePosition, Geometry.rotator(0.0, 0.0))
        val fixedEndPosition = shotLine.endPosition

        context.eyePosition = Geometry.vector(1.0, 2.5, 3.0)

        assertVectorEquals(Geometry.vector(0.72, 2.3, 3.9), shotLine.startPosition(context))
        assertVectorEquals(fixedEndPosition, shotLine.endPosition)
    }

    @Test
    fun `shot line fades out over its duration`() {
        val player = createPlayer(Geometry.vector(0.0, 0.0, 0.0), Geometry.vector(0.0, 0.0, 1.0), 0.0)
        val shotLine = ZeusShotEffect.createShotLine(player, true, 100L)

        assertEquals(1.0F, shotLine.opacityAt(100L, 80L))
        assertEquals(0.5F, shotLine.opacityAt(140L, 80L))
        assertEquals(0.0F, shotLine.opacityAt(180L, 80L))
    }

    private fun createPlayer(eyePosition: VectorView, viewVector: VectorView, yawDegrees: Double): PlayerAccess {
        return Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java)) { player, method, arguments ->
            when (method.name) {
                "getEyePosition" -> eyePosition
                "getViewVector" -> viewVector
                "getYaw" -> yawDegrees
                "hashCode" -> System.identityHashCode(player)
                "equals" -> player === arguments?.firstOrNull()
                else -> error("Unsupported PlayerAccess method: " + method.name)
            }
        } as PlayerAccess
    }

    private fun assertVectorEquals(expected: VectorView, actual: VectorView) {
        assertEquals(expected.x, actual.x, VECTOR_TOLERANCE)
        assertEquals(expected.y, actual.y, VECTOR_TOLERANCE)
        assertEquals(expected.z, actual.z, VECTOR_TOLERANCE)
    }

    private class TestWorldRenderContext(var eyePosition: VectorView, private val rotation: RotatorView, override val isBloomSupported: Boolean = false) : ClientWorldRenderContext {
        override val partialTick = 0.5F
        val drawnLines = mutableListOf<DrawnLine>()
        var bloomRenderCount = 0

        override fun interpolateEyePosition(entity: EntityAccess): VectorView {
            return eyePosition
        }

        override fun interpolateRotation(entity: EntityAccess): RotatorView {
            return rotation
        }

        override fun drawLine(startPosition: VectorView, endPosition: VectorView, widthPixels: Float, color: RenderColor, lightningIntensity: Float, lightningSpikeDensity: Float, lightningAnimationFrequency: Float) {
            drawnLines += DrawnLine(startPosition, endPosition)
        }

        override fun renderContentWithBloom(brightnessThreshold: Float, renderContent: () -> Unit) {
            bloomRenderCount++
            renderContent()
        }
    }

    private data class DrawnLine(val startPosition: VectorView, val endPosition: VectorView)

    private companion object {
        const val VECTOR_TOLERANCE = 1.0E-9
    }
}
