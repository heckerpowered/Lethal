/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.adapter.client.render.ClientWorldRenderContext
import heckerpowered.bridge.adapter.client.render.RenderColor
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RotatorView
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.gameplay.common.network.ZeusChainSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeusChainEffectTest {
    @Test
    fun simultaneousChainsRetainIndependentLines() {
        val firstStart = Geometry.vector(1.0, 2.0, 3.0)
        val firstEnd = Geometry.vector(2.0, 3.0, 4.0)
        val secondStart = Geometry.vector(5.0, 6.0, 7.0)
        val secondEnd = Geometry.vector(8.0, 9.0, 10.0)
        val context = TestWorldRenderContext()

        try {
            ZeusChainEffect.clearActiveLines()
            ZeusChainEffect.play(listOf(ZeusChainSegment(firstStart, firstEnd)))
            ZeusChainEffect.play(listOf(ZeusChainSegment(secondStart, secondEnd)))
            ZeusChainEffect.onWorldRender(context)

            assertEquals(listOf(DrawnLine(firstStart, firstEnd), DrawnLine(secondStart, secondEnd)), context.drawnLines)
            assertEquals(1, context.bloomRenderCount)
        } finally {
            ZeusChainEffect.clearActiveLines()
        }
    }

    @Test
    fun branchingChainRendersOnlyProvidedSegments() {
        val rootPosition = Geometry.vector(1.0, 2.0, 3.0)
        val firstBranchPosition = Geometry.vector(2.0, 3.0, 4.0)
        val secondBranchPosition = Geometry.vector(5.0, 6.0, 7.0)
        val context = TestWorldRenderContext()

        try {
            ZeusChainEffect.clearActiveLines()
            ZeusChainEffect.play(
                listOf(
                    ZeusChainSegment(rootPosition, firstBranchPosition),
                    ZeusChainSegment(rootPosition, secondBranchPosition),
                ),
            )
            ZeusChainEffect.onWorldRender(context)

            assertEquals(
                listOf(
                    DrawnLine(rootPosition, firstBranchPosition),
                    DrawnLine(rootPosition, secondBranchPosition),
                ),
                context.drawnLines,
            )
        } finally {
            ZeusChainEffect.clearActiveLines()
        }
    }

    @Test
    fun chainLineFadesOutOverItsDuration() {
        val line = ZeusChainLine(Geometry.vector(0.0, 0.0, 0.0), Geometry.vector(1.0, 0.0, 0.0), 100L)

        assertEquals(1.0F, line.opacityAt(100L, 80L))
        assertEquals(0.5F, line.opacityAt(140L, 80L))
        assertEquals(0.0F, line.opacityAt(180L, 80L))
    }

    private class TestWorldRenderContext : ClientWorldRenderContext {
        override val partialTick = 0.5F
        override val isBloomSupported = true
        val drawnLines = mutableListOf<DrawnLine>()
        var bloomRenderCount = 0

        override fun rendersEntityInFirstPerson(entity: EntityAccess): Boolean {
            return false
        }

        override fun interpolateEyePosition(entity: EntityAccess): VectorView {
            return entity.eyePosition
        }

        override fun interpolateRotation(entity: EntityAccess): RotatorView {
            return entity.rotation
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
}
