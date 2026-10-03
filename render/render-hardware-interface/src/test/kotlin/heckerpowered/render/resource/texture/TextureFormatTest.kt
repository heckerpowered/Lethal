/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

import heckerpowered.render.pipeline.color.ColorWriteMask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TextureFormatTest {
    @Test
    fun everyFormatExposesExactlyItsStoredColorChannels() {
        val red = ColorWriteMask(red = true, green = false, blue = false, alpha = false)
        val redGreen = ColorWriteMask(red = true, green = true, blue = false, alpha = false)
        val expected = mapOf(
            TextureFormat.R8UnsignedNormalized to red,
            TextureFormat.Rg8UnsignedNormalized to redGreen,
            TextureFormat.Rgba8UnsignedNormalized to ColorWriteMask.All,
            TextureFormat.Rgba8UnsignedNormalizedSrgb to ColorWriteMask.All,
            TextureFormat.Bgra8UnsignedNormalized to ColorWriteMask.All,
            TextureFormat.Bgra8UnsignedNormalizedSrgb to ColorWriteMask.All,
            TextureFormat.R16Float to red,
            TextureFormat.Rg16Float to redGreen,
            TextureFormat.Rgba16Float to ColorWriteMask.All,
            TextureFormat.R32Float to red,
            TextureFormat.Rg32Float to redGreen,
            TextureFormat.Rgba32Float to ColorWriteMask.All,
            TextureFormat.Depth24UnsignedNormalized to ColorWriteMask.None,
            TextureFormat.Depth32UnsignedNormalized to ColorWriteMask.None,
            TextureFormat.Depth32Float to ColorWriteMask.None,
            TextureFormat.Depth24UnsignedNormalizedStencil8 to ColorWriteMask.None,
            TextureFormat.Depth32FloatStencil8UnsignedInteger to ColorWriteMask.None,
        )
        assertEquals(TextureFormat.entries.toSet(), expected.keys)
        for ([format, components] in expected) {
            assertEquals(components, format.colorComponents, format.name)
            assertEquals(components != ColorWriteMask.None, format.isColor, format.name)
        }
    }

    @Test
    fun missingAlphaAndDepthStencilNeverClaimAnAlphaChannel() {
        val withoutAlpha = setOf(
            TextureFormat.R8UnsignedNormalized,
            TextureFormat.Rg8UnsignedNormalized,
            TextureFormat.R16Float,
            TextureFormat.Rg16Float,
            TextureFormat.R32Float,
            TextureFormat.Rg32Float,
            TextureFormat.Depth24UnsignedNormalized,
            TextureFormat.Depth32UnsignedNormalized,
            TextureFormat.Depth32Float,
            TextureFormat.Depth24UnsignedNormalizedStencil8,
            TextureFormat.Depth32FloatStencil8UnsignedInteger,
        )
        assertEquals(withoutAlpha, TextureFormat.entries.filter { !it.colorComponents.alpha }.toSet())
        for (format in withoutAlpha) assertFalse(format.colorComponents.alpha, format.name)
        for (format in TextureFormat.entries.filter { it.hasDepth || it.hasStencil }) {
            assertEquals(ColorWriteMask.None, format.colorComponents, format.name)
        }
    }
}
