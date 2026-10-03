/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.color.Color
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.pass.*
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.TextureAspect
import heckerpowered.render.resource.texture.TextureFormat
import kotlin.test.*

class RenderPassContinuationTest {
    @Test
    fun firstPassRetainsInitialLoadsAndStoresEveryAttachmentWithoutResolving() {
        val original = description()
        val first = firstContinuationPass(original, "first")

        assertEquals("first", first.label)
        assertAttachmentSelections(original, first)
        original.colorAttachments.zip(first.colorAttachments).forEach { [before, after] ->
            if (before != null) {
                assertSame(before.operation.load, after!!.operation.load)
                assertEquals(AttachmentStoreOperation.Store, after.operation.store)
            }
        }
        val depth = assertNotNull(first.depthAttachment)
        val stencil = assertNotNull(first.stencilAttachment)
        assertSame(original.depthAttachment!!.operation.load, depth.operation.load)
        assertSame(original.stencilAttachment!!.operation.load, stencil.operation.load)
        assertEquals(AttachmentStoreOperation.Store, depth.operation.store)
        assertEquals(AttachmentStoreOperation.Store, stencil.operation.store)
        assertTrue(first.colorResolves.isEmpty())
    }

    @Test
    fun intermediatePassLoadsAndStoresEveryAttachmentWithoutResolving() {
        val original = description()
        val intermediate = intermediateContinuationPass(original, "intermediate")

        assertEquals("intermediate", intermediate.label)
        assertAttachmentSelections(original, intermediate)
        intermediate.colorAttachments.filterNotNull().forEach {
            assertEquals(AttachmentOperations.Default, it.operation)
        }
        assertEquals(AttachmentOperations.Default, intermediate.depthAttachment!!.operation)
        assertEquals(AttachmentOperations.Default, intermediate.stencilAttachment!!.operation)
        assertTrue(intermediate.colorResolves.isEmpty())
    }

    @Test
    fun finalPassLoadsAttachmentsAndRetainsOriginalStoresAndResolves() {
        val original = description()
        val colors = original.colorAttachments.toList()
        val depth = original.depthAttachment
        val stencil = original.stencilAttachment
        val resolves = original.colorResolves.toList()

        firstContinuationPass(original, "first")
        intermediateContinuationPass(original, "intermediate")
        val final = finalContinuationPass(original, "final")

        assertEquals("final", final.label)
        assertAttachmentSelections(original, final)
        original.colorAttachments.zip(final.colorAttachments).forEach { [before, after] ->
            if (before != null) {
                assertEquals(AttachmentLoadOperation.Load, after!!.operation.load)
                assertEquals(before.operation.store, after.operation.store)
            }
        }
        val finalDepth = assertNotNull(final.depthAttachment)
        val finalStencil = assertNotNull(final.stencilAttachment)
        assertEquals(AttachmentLoadOperation.Load, finalDepth.operation.load)
        assertEquals(AttachmentLoadOperation.Load, finalStencil.operation.load)
        assertEquals(depth!!.operation.store, finalDepth.operation.store)
        assertEquals(stencil!!.operation.store, finalStencil.operation.store)
        assertEquals(resolves, final.colorResolves)
        assertSame(resolves.single(), final.colorResolves.single())

        assertEquals("original", original.label)
        colors.zip(original.colorAttachments).forEach { [before, after] -> assertSame(before, after) }
        assertSame(depth, original.depthAttachment)
        assertSame(stencil, original.stencilAttachment)
        assertSame(resolves.single(), original.colorResolves.single())
        assertEquals(AttachmentLoadOperation.Clear(Color(0.1f, 0.2f, 0.3f, 1f)), colors[0]!!.operation.load)
        assertEquals(AttachmentStoreOperation.Discard, colors[0]!!.operation.store)
        assertEquals(AttachmentLoadOperation.Clear(7.toUByte()), stencil.operation.load)
    }

    @Test
    fun absentAttachmentsAndNullColorSlotsRemainAbsentInEveryPass() {
        val original = RenderPassDescription("empty", RenderArea(1, 2, 8, 4), listOf(null, null))
        val passes = listOf(
            firstContinuationPass(original, "first"),
            intermediateContinuationPass(original, "intermediate"),
            finalContinuationPass(original, "final"),
        )

        passes.forEach {
            assertEquals(listOf(null, null), it.colorAttachments)
            assertNull(it.depthAttachment)
            assertNull(it.stencilAttachment)
            assertTrue(it.colorResolves.isEmpty())
            assertEquals(original.renderArea, it.renderArea)
        }
    }

    private fun assertAttachmentSelections(original: RenderPassDescription, derived: RenderPassDescription) {
        assertNotSame(original, derived)
        assertEquals(original.renderArea, derived.renderArea)
        assertEquals(original.layerCount, derived.layerCount)
        assertEquals(original.attachmentSampleCount, derived.attachmentSampleCount)
        assertEquals(original.colorAttachments.size, derived.colorAttachments.size)
        original.colorAttachments.zip(derived.colorAttachments).forEach { [before, after] ->
            if (before == null) assertNull(after) else assertSame(before.attachment, after!!.attachment)
        }
        assertSame(original.depthAttachment!!.attachment, derived.depthAttachment!!.attachment)
        assertSame(original.stencilAttachment!!.attachment, derived.stencilAttachment!!.attachment)
    }

    private fun description(): RenderPassDescription {
        val color = Attachment(TextureFormat.Rgba8UnsignedNormalized, SampleCount.Four, setOf(TextureAspect.Color))
        val otherColor = Attachment(TextureFormat.Rgba8UnsignedNormalized, SampleCount.Four, setOf(TextureAspect.Color))
        val depthStencil = Attachment(TextureFormat.Depth24UnsignedNormalizedStencil8, SampleCount.Four, setOf(TextureAspect.Depth, TextureAspect.Stencil))
        val destination = Attachment(TextureFormat.Rgba8UnsignedNormalized, SampleCount.One, setOf(TextureAspect.Color))
        val area = RenderArea(1, 2, 8, 4)
        val resolve = ColorAttachmentResolve(0, ImageRegion.Attachment(destination, arrayLayerCount = 2, x = area.x, y = area.y, width = area.width, height = area.height))
        return RenderPassDescription(
            label = "original",
            renderArea = area,
            colorAttachments = listOf(
                RenderPassAttachment(color, AttachmentOperations(AttachmentLoadOperation.Clear(Color(0.1f, 0.2f, 0.3f, 1f)), AttachmentStoreOperation.Discard)),
                null,
                RenderPassAttachment(otherColor, AttachmentOperations(AttachmentLoadOperation.Discard, AttachmentStoreOperation.Store)),
            ),
            depthAttachment = RenderPassAttachment(depthStencil, AttachmentOperations(AttachmentLoadOperation.Load, AttachmentStoreOperation.Discard)),
            stencilAttachment = RenderPassAttachment(depthStencil, AttachmentOperations(AttachmentLoadOperation.Clear(7.toUByte()), AttachmentStoreOperation.Store)),
            layerCount = 2,
            colorResolves = listOf(resolve),
        )
    }

    private class Attachment(
        override val format: TextureFormat,
        override val sampleCount: SampleCount,
        override val aspects: Set<TextureAspect>,
    ) : RenderAttachment {
        override val width = 16
        override val height = 16
        override val arrayLayerCount = 2
    }
}
