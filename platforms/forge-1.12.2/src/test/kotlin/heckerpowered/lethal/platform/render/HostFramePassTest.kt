/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.render.command.pass.AttachmentLoadOperation
import heckerpowered.render.command.pass.AttachmentStoreOperation
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.target.RenderTarget
import heckerpowered.render.resource.texture.TextureAspect
import heckerpowered.render.resource.texture.TextureFormat
import kotlin.test.*

class HostFramePassTest {
    @Test
    fun worldAndPostProcessDefaultsKeepTheActualSceneColorAndPackedDepth() {
        val color = attachment(TextureFormat.Rgba8UnsignedNormalized, TextureAspect.Color)
        val depth = attachment(TextureFormat.Depth24UnsignedNormalizedStencil8, TextureAspect.Depth)
        val target = object : RenderTarget {
            override val colorAttachments = listOf(color)
            override val depthAttachment = depth
            override val stencilAttachment = null
        }
        for (label in listOf("Minecraft world effects", "Minecraft post-processing")) {
            val pass = preservingFramePass(label, target)
            assertSame(color, pass.colorAttachments.single()!!.attachment)
            assertSame(depth, pass.depthAttachment!!.attachment)
            assertEquals(TextureFormat.Depth24UnsignedNormalizedStencil8, pass.depthAttachment!!.attachment.format)
            assertEquals(AttachmentLoadOperation.Load, pass.colorAttachments.single()!!.operation.load)
            assertEquals(AttachmentLoadOperation.Load, pass.depthAttachment!!.operation.load)
            assertEquals(AttachmentStoreOperation.Store, pass.colorAttachments.single()!!.operation.store)
            assertEquals(AttachmentStoreOperation.Store, pass.depthAttachment!!.operation.store)
            assertNull(pass.stencilAttachment)
            assertEquals(listOf(0, 0, 1280, 720), listOf(pass.renderArea.x, pass.renderArea.y, pass.renderArea.width, pass.renderArea.height))
        }
    }

    private fun attachment(textureFormat: TextureFormat, aspect: TextureAspect): RenderAttachment = object : RenderAttachment {
        override val width = 1280
        override val height = 720
        override val format = textureFormat
        override val sampleCount = SampleCount.One
        override val arrayLayerCount = 1
        override val aspects = setOf(aspect)
    }
}
