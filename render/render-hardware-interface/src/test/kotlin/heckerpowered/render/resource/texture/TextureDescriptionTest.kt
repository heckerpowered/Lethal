/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

import heckerpowered.render.pipeline.multisample.SampleCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextureDescriptionTest {
    @Test
    fun colorAndDepthStencilRolesCannotHideIncompatibleAspects() {
        val usage = setOf(TextureUsage.ColorAttachment, TextureUsage.DepthStencilAttachment)
        val colorFailure = assertFailsWith<IllegalArgumentException> {
            TextureDescription("color", 1, format = TextureFormat.Rgba8UnsignedNormalized, usage = usage)
        }
        assertEquals("Depth-stencil attachment usage requires a depth or stencil aspect", colorFailure.message)

        val depthFailure = assertFailsWith<IllegalArgumentException> {
            TextureDescription("depth", 1, format = TextureFormat.Depth24UnsignedNormalized, usage = usage)
        }
        assertEquals("Color attachment usage requires a color format", depthFailure.message)
    }

    @Test
    fun singleSampledColorAttachmentsCannotDeclareResolveSource() {
        val failure = assertFailsWith<IllegalArgumentException> {
            TextureDescription("source", 1, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.ColorAttachment, TextureUsage.ResolveSource))
        }
        assertEquals("Resolve source usage requires multiple samples", failure.message)
    }

    @Test
    fun multisampledAttachmentsCannotDeclareResolveDestination() {
        val attachments = mapOf(
            TextureFormat.Rgba8UnsignedNormalized to TextureUsage.ColorAttachment,
            TextureFormat.Depth24UnsignedNormalized to TextureUsage.DepthStencilAttachment,
        )
        for ([format, attachment] in attachments) {
            val failure = assertFailsWith<IllegalArgumentException> {
                TextureDescription("destination", 1, format = format, usage = setOf(attachment, TextureUsage.ResolveDestination), sampleCount = SampleCount.Four)
            }
            assertEquals("Resolve destination usage requires one sample", failure.message)
        }
    }

    @Test
    fun resolveSourceAndDestinationCannotShareOneTexture() {
        val usage = setOf(TextureUsage.ResolveSource, TextureUsage.ResolveDestination)
        val singleSampleFailure = assertFailsWith<IllegalArgumentException> {
            TextureDescription("single", 1, format = TextureFormat.Rgba8UnsignedNormalized, usage = usage)
        }
        assertEquals("Resolve source usage requires multiple samples", singleSampleFailure.message)

        val multisampleFailure = assertFailsWith<IllegalArgumentException> {
            TextureDescription("multi", 1, format = TextureFormat.Rgba8UnsignedNormalized, usage = usage, sampleCount = SampleCount.Four)
        }
        assertEquals("Resolve destination usage requires one sample", multisampleFailure.message)
    }

    @Test
    fun attachmentsCanStillCombineWithCompatibleResolveRoles() {
        val attachments = mapOf(
            TextureFormat.Rgba8UnsignedNormalized to TextureUsage.ColorAttachment,
            TextureFormat.Depth24UnsignedNormalized to TextureUsage.DepthStencilAttachment,
        )
        for ([format, attachment] in attachments) {
            val sourceUsage = setOf(attachment, TextureUsage.ResolveSource)
            val source = TextureDescription("source", 1, format = format, usage = sourceUsage, sampleCount = SampleCount.Four)
            assertEquals(sourceUsage, source.usage)
            assertEquals(SampleCount.Four, source.sampleCount)

            val destinationUsage = setOf(attachment, TextureUsage.ResolveDestination)
            val destination = TextureDescription("destination", 1, format = format, usage = destinationUsage)
            assertEquals(destinationUsage, destination.usage)
            assertEquals(SampleCount.One, destination.sampleCount)
        }
    }
}
