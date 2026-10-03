/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.command

import heckerpowered.render.command.ImageRegion
import heckerpowered.render.vulkan.function.VulkanFenceStatus
import kotlin.test.*

class VulkanOrderedEncodeTest {
    @Test
    fun recordedPredecessorAndForeignOrStandaloneStreamsCannotBorrowPendingImageProof() {
        val queue = RasterTestQueue()
        val texture = queue.texture()
        val stream = Any()
        val recorded = texture.recordUse(0, stream)
        assertFailsWith<IllegalStateException> { texture.recordUse(0, stream) }
        recorded.markFullWrite(); recorded.markSubmitted()
        assertFailsWith<IllegalStateException> { texture.recordUse(0, Any()) }
        assertFailsWith<IllegalStateException> { texture.recordUse(0) }
        assertFailsWith<IllegalStateException> { texture.recordUse(1, stream) }
        val next = texture.recordUse(0, stream)
        assertTrue(next.contentsDefined)
        next.discardRecording(); recorded.completeSubmission(); texture.close()
    }

    @Test
    fun olderReadCompletionCannotEraseLaterFullWriteValidity() {
        val queue = RasterTestQueue()
        val texture = queue.texture()
        val stream = Any()
        val earlier = texture.recordUse(0, stream)
        earlier.markSubmitted()
        val later = texture.recordUse(0, stream)
        assertFalse(later.contentsDefined)
        later.markFullWrite(); later.markSubmitted(); later.completeSubmission()
        earlier.completeSubmission()
        val next = texture.recordUse(0)
        assertTrue(next.contentsDefined)
        next.discardRecording(); texture.close()
    }

    @Test
    fun uncertainFullWriteDoesNotPublishValidityButPreservesEarlierCompletedProof() {
        val queue = RasterTestQueue()
        val texture = queue.texture()
        val stream = Any()
        val unknown = texture.recordUse(0, stream)
        unknown.markFullWrite(); unknown.retireCompletedQueue(false)
        val fresh = texture.recordUse(0)
        assertFalse(fresh.contentsDefined)
        fresh.markFullWrite(); fresh.markSubmitted(); fresh.completeSubmission()
        val laterUnknown = texture.recordUse(0, stream)
        laterUnknown.retireCompletedQueue(false)
        val next = texture.recordUse(0)
        assertTrue(next.contentsDefined)
        next.discardRecording(); texture.close()
    }

    @Test
    fun acceptedDeviceSessionsShareExecutionPositionWhileStandaloneSessionRemainsExclusive() {
        val queue = RasterTestQueue()
        val texture = queue.texture()
        val first = VulkanTransferSession.createForDevice(queue)
        first.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84) { 23 }) }
        val standalone = VulkanTransferSession.create(queue)
        assertFailsWith<IllegalStateException> { standalone.record { images.readTexture(ImageRegion.Texture(texture)) } }
        standalone.close()
        val second = VulkanTransferSession.createForDevice(queue)
        lateinit var readback: VulkanBufferReadback
        second.record { readback = images.readTexture(ImageRegion.Texture(texture)) }
        assertEquals(listOf(false, true), queue.barrierBaselines())
        assertFailsWith<IllegalStateException> { readback.readBytes() }
        VulkanTransferSession.awaitQueueCompletion(queue, listOf(first, second))
        assertContentEquals(ByteArray(84) { 23 }, readback.readBytes())
        first.close(); second.close(); texture.close()
    }

    @Test
    fun retiringEarlierFenceLeavesLaterImagePinAndProofAvailableToSameStream() {
        val queue = RasterTestQueue()
        val texture = queue.texture()
        val first = VulkanTransferSession.createForDevice(queue)
        first.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84) { 31 }) }
        val second = VulkanTransferSession.createForDevice(queue)
        second.record { images.readTexture(ImageRegion.Texture(texture)) }
        queue.completion = VulkanFenceStatus.Complete
        assertTrue(first.pollCompletion())
        assertFailsWith<IllegalStateException> { texture.recordUse(0) }
        val third = VulkanTransferSession.createForDevice(queue)
        third.record { images.readTexture(ImageRegion.Texture(texture)) }
        VulkanTransferSession.awaitQueueCompletion(queue, listOf(second, third))
        first.close(); second.close(); third.close(); texture.close()
    }
}
