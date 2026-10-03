/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.TextureDataLayout
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.shader.binding.TextureSampleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Depth32UnsignedNormalizedTest {
    @Test
    fun hostDepthHasOnlyTheDepthAspectAndNormalizedShaderValues() {
        val format = TextureFormat.Depth32UnsignedNormalized
        assertTrue(format.hasDepth)
        assertFalse(format.hasStencil)
        assertFalse(format.isColor)
        assertEquals(TextureSampleType.Float, TextureSampleType.from(format, TextureAspect.Depth))
        assertFailsWith<IllegalArgumentException> { TextureSampleType.from(format, TextureAspect.Color) }
        assertFailsWith<IllegalArgumentException> { TextureSampleType.from(format, TextureAspect.Stencil) }
        TextureDescription("host depth", 8, 8, format = format, usage = setOf(TextureUsage.DepthStencilAttachment))
        assertFailsWith<IllegalArgumentException> {
            TextureDescription("invalid color", 8, 8, format = format, usage = setOf(TextureUsage.ColorAttachment))
        }
    }

    @Test
    fun normalized32BitDepthUsesFourBytesPerTransferTexel() {
        val attachment = object : RenderAttachment {
            override val width = 4
            override val height = 4
            override val format = TextureFormat.Depth32UnsignedNormalized
            override val sampleCount = SampleCount.One
            override val arrayLayerCount = 1
            override val aspects = setOf(TextureAspect.Depth)
        }
        val region = ImageRegion.Attachment(attachment, aspect = TextureAspect.Depth)
        val footprint = TextureDataLayout.TightlyPacked.footprintFor(region)
        assertEquals(4, footprint.texelSizeBytes)
        assertEquals(64L, footprint.requiredSizeBytes)
    }
}
