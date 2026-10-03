/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render.postprocess

import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.color.ColorWriteMask
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.shader.program.FragmentOutput
import heckerpowered.render.pipeline.color.BlendState
import heckerpowered.render.resource.texture.TextureFormat
import kotlin.test.*

class ElectricContentCompositionTest {
    @Test
    fun restrictionIntersectsTheOriginalMaskAndCannotInventACoveragePromise() {
        val source = FragmentOutput(AlphaRepresentation.Premultiplied, AlphaQuantity.Coverage)
        var targetCalls = 0
        val original = object : CompositingMode {
            override fun target(format: TextureFormat, source: FragmentOutput?, destination: AlphaQuantity?): ColorTargetState {
                targetCalls++
                return ColorTargetState(format, BlendState.PremultipliedAlpha, ColorWriteMask.Rgb)
            }
            override fun preservesCoverage(source: FragmentOutput, target: ColorTargetState) = true
        }
        assertSame(original, original.withWriteMask(ColorWriteMask.All))
        val restricted = original.withWriteMask(ColorWriteMask(true, false, false, true))
        val state = restricted.target(TextureFormat.Rgba8UnsignedNormalized, source, AlphaQuantity.Coverage)
        assertEquals(ColorWriteMask(true, false, false, false), state.writeMask)
        assertEquals(BlendState.PremultipliedAlpha, state.blend)
        assertFalse(restricted.preservesCoverage(source, state))
        assertEquals(1, targetCalls)
    }

    @Test
    fun numericContentUsesTheLegacyEquationWithoutACoveragePromise() {
        val source = FragmentOutput(AlphaRepresentation.Premultiplied, AlphaQuantity.Signal)
        val composition = PostProcessRenderer.ElectricContentComposition
        val state = composition.target(TextureFormat.Rgba8UnsignedNormalized, source, AlphaQuantity.Signal)
        assertEquals(BlendState.PremultipliedAlpha, state.blend)
        assertFalse(composition.preservesCoverage(source, state))
        assertFailsWith<IllegalArgumentException> { composition.target(state.format, source, AlphaQuantity.Coverage) }
        assertFailsWith<IllegalArgumentException> { composition.target(state.format, FragmentOutput(AlphaRepresentation.Premultiplied, AlphaQuantity.Coverage), AlphaQuantity.Signal) }
    }
}
