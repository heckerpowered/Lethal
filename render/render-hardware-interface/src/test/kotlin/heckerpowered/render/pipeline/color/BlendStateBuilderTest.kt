/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlendStateBuilderTest {
    @Test
    fun independentEquationsPreserveDestinationAlphaAndExistingPresets() {
        assertEquals(
            BlendState(BlendComponent(BlendFactor.One, BlendFactor.One), BlendComponent(BlendFactor.Zero, BlendFactor.One)),
            blendState {
                color(BlendFactor.One, BlendFactor.One)
                alpha(BlendFactor.Zero, BlendFactor.One)
            },
        )
        assertEquals(BlendState.PremultipliedAlpha, blendState {
            color(BlendFactor.One, BlendFactor.OneMinusSourceAlpha)
            alpha(BlendFactor.One, BlendFactor.OneMinusSourceAlpha)
        })
        assertEquals(BlendState.StraightAlpha, blendState {
            color(BlendFactor.SourceAlpha, BlendFactor.OneMinusSourceAlpha)
            alpha(BlendFactor.One, BlendFactor.OneMinusSourceAlpha)
        })
        assertEquals(BlendState.Additive, blendState {
            color(BlendFactor.One, BlendFactor.One)
            alpha(BlendFactor.One, BlendFactor.One)
        })
    }

    @Test
    fun everyFactorAndOperationRemainAvailableIndependentlyForBothChannels() {
        val retainedAlpha = BlendComponent(BlendFactor.Zero, BlendFactor.One, BlendOperation.ReverseSubtract)
        val retainedColor = BlendComponent(BlendFactor.SourceAlpha, BlendFactor.DestinationColor, BlendOperation.Subtract)
        for (source in BlendFactor.entries) {
            for (destination in BlendFactor.entries) {
                for (operation in BlendOperation.entries) {
                    val equation = BlendComponent(source, destination, operation)
                    assertEquals(BlendState(equation, retainedAlpha), blendState {
                        color(source, destination, operation)
                        alpha(retainedAlpha.sourceFactor, retainedAlpha.destinationFactor, retainedAlpha.operation)
                    })
                    assertEquals(BlendState(retainedColor, equation), blendState {
                        alpha(source, destination, operation)
                        color(retainedColor.sourceFactor, retainedColor.destinationFactor, retainedColor.operation)
                    })
                }
            }
        }
    }

    @Test
    fun missingEquationsCannotIntroduceImplicitBlendDefaults() {
        assertFailsWith<IllegalStateException> { blendState {} }
        assertFailsWith<IllegalStateException> { blendState { color(BlendFactor.One, BlendFactor.One) } }
        assertFailsWith<IllegalStateException> { blendState { alpha(BlendFactor.One, BlendFactor.One) } }
    }

    @Test
    fun repeatedEquationsCannotSilentlyReplaceEarlierDeclarations() {
        assertFailsWith<IllegalStateException> {
            blendState {
                color(BlendFactor.One, BlendFactor.One)
                color(BlendFactor.Zero, BlendFactor.One)
                alpha(BlendFactor.One, BlendFactor.One)
            }
        }
        assertFailsWith<IllegalStateException> {
            blendState {
                color(BlendFactor.One, BlendFactor.One)
                alpha(BlendFactor.One, BlendFactor.One)
                alpha(BlendFactor.Zero, BlendFactor.One)
            }
        }
    }
}
