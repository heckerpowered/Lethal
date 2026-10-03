/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.command

import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.TextureDataLayout
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.resource.buffer.*
import heckerpowered.render.resource.texture.*
import heckerpowered.render.vulkan.function.*
import heckerpowered.render.vulkan.resource.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider
import kotlin.test.*

class VulkanImageTransfersTest {
    @Test
    fun imageAndBufferCommandsShareOneRecordingStagingAndFence() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val bytes = imagePattern(7, 3, 43)
        val buffer = createVulkanBuffer(functions.bufferFunctions, BufferDescription("shared", bytes.size.toLong(), setOf(BufferUsage.TransferSource, BufferUsage.TransferDestination)))
        val session = VulkanTransferSession.create(functions)
        lateinit var imageRead: VulkanBufferReadback
        lateinit var bufferRead: VulkanBufferReadback
        session.record {
            writeBuffer(GpuBufferView(buffer, 0, bytes.size.toLong()), bytes)
            images.writeTexture(ImageRegion.Texture(texture), bytes)
            imageRead = images.readTexture(ImageRegion.Texture(texture))
            bufferRead = readBuffer(GpuBufferView(buffer, 0, bytes.size.toLong()))
        }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        assertContentEquals(bytes, imageRead.readBytes())
        assertContentEquals(bufferRead.readBytes(), imageRead.readBytes())
        assertEquals(1, functions.calls.count { it.startsWith("submit:") })
        assertEquals(1, functions.calls.count { it.startsWith("destroyRecording:") })
        session.close()
        texture.close()
        buffer.close()
    }

    @Test
    fun uploadSnapshotsRowsAndChannelsAndReadbackRequiresActualCompletion() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val source = imagePattern(7, 3, 11)
        val expected = source.copyOf()
        val session = VulkanTransferSession.create(functions)
        lateinit var readback: VulkanBufferReadback
        session.record {
            images.writeTexture(ImageRegion.Texture(texture), source)
            source.fill(99)
            readback = images.readTexture(ImageRegion.Texture(texture))
        }
        assertFailsWith<IllegalStateException> { readback.readBytes() }
        assertFalse(session.pollCompletion())
        assertFalse(session.awaitCompletion(0))
        assertTrue(functions.host.calls.none { it.startsWith("unmap:") || it.startsWith("invalidate:") })
        functions.completion = VulkanFenceStatus.Complete
        assertTrue(session.pollCompletion())
        assertContentEquals(expected, readback.readBytes())
        assertEquals(listOf(false, true), functions.barrierBaselines())
        assertEquals(2, functions.host.calls.count { it.startsWith("map:") })
        session.close()
        assertFailsWith<IllegalStateException> { readback.readBytes() }
        texture.close()
    }

    @Test
    fun aliasesAndSuccessiveUploadsObserveExecutionPositionContents() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val view = createVulkanTextureView(functions.imageFunctions, texture, TextureViewDescription())
        val attachment = createVulkanAttachmentView(functions.imageFunctions, view)
        val firstBytes = imagePattern(7, 3, 5)
        val secondBytes = imagePattern(7, 3, 97)
        val session = VulkanTransferSession.create(functions)
        lateinit var first: VulkanBufferReadback
        lateinit var second: VulkanBufferReadback
        session.record {
            images.writeTexture(ImageRegion.View(view, 0), firstBytes)
            first = images.readTexture(ImageRegion.Attachment(attachment))
            images.writeTexture(ImageRegion.Attachment(attachment), secondBytes)
            second = images.readTexture(ImageRegion.Texture(texture))
        }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        assertContentEquals(firstBytes, first.readBytes())
        assertContentEquals(secondBytes, second.readBytes())
        assertEquals(listOf(false, true, true, true), functions.barrierBaselines())
        session.close()
        texture.close()
    }

    @Test
    fun secondSessionIsRejectedDuringRecordingAndPendingAndTimeoutRetainsReservation() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val first = VulkanTransferSession.create(functions)
        first.record {
            images.writeTexture(ImageRegion.Texture(texture), imagePattern(7, 3, 1))
            val second = VulkanTransferSession.create(functions)
            val before = functions.imageCommandCount()
            assertFailsWith<IllegalStateException> { second.record { images.writeTexture(ImageRegion.Texture(texture), imagePattern(7, 3, 2)) } }
            assertEquals(before, functions.imageCommandCount())
            second.close()
        }
        assertFalse(first.awaitCompletion(0))
        val second = VulkanTransferSession.create(functions)
        val maps = functions.host.calls.count { it.startsWith("map:") }
        val commands = functions.imageCommandCount()
        assertFailsWith<IllegalStateException> { second.record { images.readTexture(ImageRegion.Texture(texture)) } }
        assertEquals(commands, functions.imageCommandCount())
        assertEquals(maps, functions.host.calls.count { it.startsWith("map:") })
        second.close()
        functions.completion = VulkanFenceStatus.Complete
        first.awaitCompletion(1)
        first.close()
        val third = VulkanTransferSession.create(functions)
        lateinit var read: VulkanBufferReadback
        third.record { read = images.readTexture(ImageRegion.Texture(texture)) }
        third.pollCompletion()
        assertContentEquals(imagePattern(7, 3, 1), read.readBytes())
        third.close()
        texture.close()
    }

    @Test
    fun completionCommitsGeneralOnlyAfterOneShotRecordingDestruction() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val first = VulkanTransferSession.create(functions)
        first.record { images.writeTexture(ImageRegion.Texture(texture), imagePattern(7, 3, 13)) }
        functions.onDestroyRecording = { assertFailsWith<IllegalStateException> { texture.recordUse(0) } }
        functions.completion = VulkanFenceStatus.Complete
        first.pollCompletion()
        functions.onDestroyRecording = {}
        first.close()
        val second = VulkanTransferSession.create(functions)
        lateinit var read: VulkanBufferReadback
        val expected = imagePattern(7, 3, 149)
        second.record {
            images.writeTexture(ImageRegion.Texture(texture), expected)
            read = images.readTexture(ImageRegion.Texture(texture))
        }
        second.pollCompletion()
        assertContentEquals(expected, read.readBytes())
        assertEquals(listOf(false, true, true), functions.barrierBaselines())
        second.close()
        texture.close()
    }

    @Test
    fun unsubmittedDiscardRollsBackFreshAndPreviouslyCompletedImageJournals() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val failure = AssertionError("discard after recorded image upload")
        val discarded = VulkanTransferSession.create(functions)
        assertSame(failure, assertFailsWith<AssertionError> {
            discarded.record { images.writeTexture(ImageRegion.Texture(texture), imagePattern(7, 3, 1)); throw failure }
        })
        discarded.close()
        assertTrue(functions.imageBytes(texture).all { it == 0xCC.toByte() })
        val initial = VulkanTransferSession.create(functions)
        initial.record {
            assertFailsWith<IllegalStateException> { images.readTexture(ImageRegion.Texture(texture)) }
            images.writeTexture(ImageRegion.Texture(texture), imagePattern(7, 3, 2))
        }
        functions.completion = VulkanFenceStatus.Complete
        initial.pollCompletion()
        initial.close()
        val discardedAgain = VulkanTransferSession.create(functions)
        assertFailsWith<AssertionError> {
            discardedAgain.record { images.writeTexture(ImageRegion.Texture(texture), imagePattern(7, 3, 3)); throw failure }
        }
        discardedAgain.close()
        val readSession = VulkanTransferSession.create(functions)
        lateinit var read: VulkanBufferReadback
        readSession.record { read = images.readTexture(ImageRegion.Texture(texture)) }
        readSession.pollCompletion()
        assertContentEquals(imagePattern(7, 3, 2), read.readBytes())
        assertEquals(listOf(false, false, true, true), functions.barrierBaselines())
        readSession.close()
        texture.close()
    }

    @Test
    fun firstReadRejectsUndefinedContentsWithoutImageEmissionOrStaging() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val session = VulkanTransferSession.create(functions)
        assertFailsWith<IllegalStateException> { session.record { images.readTexture(ImageRegion.Texture(texture)) } }
        assertEquals(0, functions.imageCommandCount())
        assertTrue(functions.host.calls.isEmpty())
        session.close()
        val use = texture.recordUse(0)
        assertFalse(use.contentsDefined)
        use.discardRecording()
        texture.close()
    }

    @Test
    fun partialRegionsAndPaddedRowsAreRejectedWithoutChangingNeighbors() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val session = VulkanTransferSession.create(functions)
        session.record {
            assertFailsWith<UnsupportedOperationException> { images.writeTexture(ImageRegion.Texture(texture).subRegion(relativeX = 1, width = 2, height = 2), ByteArray(16)) }
            assertFailsWith<UnsupportedOperationException> { images.readTexture(ImageRegion.Texture(texture, y = 1, height = 1)) }
            assertFailsWith<UnsupportedOperationException> { images.writeTexture(ImageRegion.Texture(texture), ByteArray(92), TextureDataLayout(rowStrideBytes = 32)) }
            assertFailsWith<UnsupportedOperationException> { images.readTexture(ImageRegion.Texture(texture), TextureDataLayout(sliceStrideBytes = 100)) }
            assertFailsWith<IllegalArgumentException> { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84), TextureDataLayout(rowStrideBytes = 27)) }
        }
        assertEquals(0, functions.imageCommandCount())
        assertTrue(functions.host.calls.isEmpty())
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        assertTrue(functions.imageBytes(texture).all { it == 0xCC.toByte() })
        texture.close()
    }

    @Test
    fun emptyAndWrongSizeInputsAndMissingRolesFailBeforeReservationOrNativeEmission() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val sampled = functions.texture(usage = setOf(TextureUsage.Sampled))
        val resolve = functions.texture(usage = setOf(TextureUsage.ResolveDestination))
        val attachmentTexture = functions.texture(usage = setOf(TextureUsage.ColorAttachment, TextureUsage.ResolveDestination))
        val attachmentView = createVulkanTextureView(functions.imageFunctions, attachmentTexture, TextureViewDescription())
        val attachment = createVulkanAttachmentView(functions.imageFunctions, attachmentView)
        val session = VulkanTransferSession.create(functions)
        session.record {
            assertFailsWith<IllegalArgumentException> { images.writeTexture(ImageRegion.Texture(texture), byteArrayOf()) }
            assertFailsWith<IllegalArgumentException> { images.writeTexture(ImageRegion.Texture(texture), ByteArray(83)) }
            assertFailsWith<IllegalArgumentException> { images.writeTexture(ImageRegion.Texture(sampled), ByteArray(84)) }
            assertFailsWith<IllegalArgumentException> { images.readTexture(ImageRegion.Texture(sampled)) }
            assertFailsWith<IllegalArgumentException> { images.writeTexture(ImageRegion.Texture(resolve), ByteArray(84)) }
            assertFailsWith<IllegalArgumentException> { images.writeTexture(ImageRegion.Attachment(attachment), ByteArray(84)) }
            assertFailsWith<IllegalArgumentException> { images.readTexture(ImageRegion.Attachment(attachment)) }
        }
        assertEquals(0, functions.imageCommandCount())
        assertTrue(functions.host.calls.isEmpty())
        val use = texture.recordUse(0)
        use.discardRecording()
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        texture.close()
        sampled.close()
        resolve.close()
        attachmentTexture.close()
    }

    @Test
    fun missingImageOrHostCapabilityAndForeignOwnersFailWithoutImageEmission() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val foreignFunctions = FakeVulkanImageQueue()
        val foreign = foreignFunctions.texture()
        val foreignView = createVulkanTextureView(foreignFunctions.imageFunctions, foreign, TextureViewDescription())
        val foreignAttachment = createVulkanAttachmentView(foreignFunctions.imageFunctions, foreignView)
        val session = VulkanTransferSession.create(functions)
        session.record {
            assertFailsWith<IllegalArgumentException> { images.writeTexture(ImageRegion.Texture(foreign), ByteArray(84)) }
            assertFailsWith<IllegalArgumentException> { images.readTexture(ImageRegion.View(foreignView, 0)) }
            assertFailsWith<IllegalArgumentException> { images.readTexture(ImageRegion.Attachment(foreignAttachment)) }
        }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        val mismatched = object : VulkanTransferFunctions by functions {
            override val imageTransferFunctions = foreignFunctions
        }
        val mismatchedSession = VulkanTransferSession.create(mismatched)
        assertFailsWith<IllegalArgumentException> { mismatchedSession.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84)) } }
        mismatchedSession.close()
        functions.provideImages = false
        val unsupported = VulkanTransferSession.create(functions)
        assertFailsWith<UnsupportedOperationException> { unsupported.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84)) } }
        unsupported.close()
        functions.provideImages = true
        functions.provideHost = false
        val noHost = VulkanTransferSession.create(functions)
        assertFailsWith<UnsupportedOperationException> { noHost.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84)) } }
        noHost.close()
        assertEquals(0, functions.imageCommandCount())
        assertTrue(functions.host.calls.isEmpty())
        val use = texture.recordUse(0)
        use.discardRecording()
        texture.close()
        foreign.close()
    }

    @Test
    fun escapedReceiverClosedSourcesAndWrongThreadFailBeforeNativeCalls() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val region = ImageRegion.Texture(texture)
        val session = VulkanTransferSession.create(functions)
        lateinit var escaped: VulkanImageTransfers
        session.record { escaped = images }
        assertFailsWith<IllegalStateException> { escaped.writeTexture(region, ByteArray(84)) }
        functions.completion = VulkanFenceStatus.Complete
        session.pollCompletion()
        session.close()
        val wrongThread = VulkanTransferSession.create(functions)
        functions.bufferState.accessAllowed = false
        assertFailsWith<IllegalStateException> { wrongThread.record { images.writeTexture(region, ByteArray(84)) } }
        functions.bufferState.accessAllowed = true
        texture.close()
        assertFailsWith<IllegalStateException> { wrongThread.record { images.writeTexture(region, ByteArray(84)) } }
        wrongThread.close()
        assertEquals(0, functions.imageCommandCount())
    }

    @Test
    fun scopedInputAndLargeOddWidthPayloadsReuseExistingHostReadbackAndCoherentRules() {
        for (coherent in listOf(false, true)) {
            val functions = FakeVulkanImageQueue().apply { if (coherent) bufferState.types = intArrayOf(6, 1) }
            val texture = functions.texture(257, 65)
            val bytes = imagePattern(257, 65, 61)
            val stack = MemoryStack(bytes.size + 64)
            val session = VulkanTransferSession.create(functions)
            lateinit var readback: VulkanBufferReadback
            session.record {
                stack.frame {
                    val source = reserve(bytes.size, 1)
                    asByteBuffer(source, bytes.size).put(bytes)
                    images.writeTexture(ImageRegion.Texture(texture), source, TextureDataLayout(1028, bytes.size.toLong()))
                }
                stack.frame { reserveBuffer(bytes.size).put(ByteArray(bytes.size) { 99 }) }
                readback = images.readTexture(ImageRegion.Texture(texture))
            }
            functions.completion = VulkanFenceStatus.Complete
            session.pollCompletion()
            assertEquals(66820L, readback.sizeBytes)
            assertContentEquals(bytes, readback.readBytes())
            stack.frame {
                val destination = reserve(bytes.size, 1)
                readback.read(destination)
                val buffer = asByteBuffer(destination, bytes.size)
                for (index in bytes.indices) assertEquals(bytes[index], buffer[index])
            }
            assertEquals(if (coherent) 0 else 1, functions.host.calls.count { it.startsWith("flush:") })
            assertEquals(if (coherent) 0 else 2, functions.host.calls.count { it.startsWith("invalidate:") })
            session.close()
            texture.close()
        }
    }

    @Test
    fun caughtNativeImageFailuresInvalidateRecordingAndPreventSubmission() {
        for (stage in listOf("before", "imageBarrier", "upload", "readback")) {
            val functions = FakeVulkanImageQueue()
            val texture = functions.texture()
            val session = VulkanTransferSession.create(functions)
            assertFailsWith<IllegalStateException> {
                session.record {
                    if (stage == "readback") images.writeTexture(ImageRegion.Texture(texture), ByteArray(84))
                    functions.failureStage = stage
                    assertSame(functions.failure, assertFailsWith<IllegalStateException> {
                        if (stage == "readback") images.readTexture(ImageRegion.Texture(texture))
                        else images.writeTexture(ImageRegion.Texture(texture), ByteArray(84))
                    })
                    assertFailsWith<IllegalStateException> { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84)) }
                }
            }
            assertFalse(functions.calls.any { it.startsWith("submit:") })
            assertTrue(functions.calls.any { it.startsWith("destroyRecording:") })
            session.close()
            val use = texture.recordUse(0)
            assertFalse(use.contentsDefined)
            use.discardRecording()
            texture.close()
        }
    }

    @Test
    fun hostFailuresAndNativeErrorsPreserveFailureAndRollBackUnsubmittedImage() {
        for (stage in listOf("map", "write", "flush")) {
            val functions = FakeVulkanImageQueue()
            val texture = functions.texture()
            val session = VulkanTransferSession.create(functions)
            functions.host.failureStage = stage
            assertSame(functions.host.failure, assertFailsWith<IllegalStateException> { session.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84)) } })
            assertEquals(0, functions.imageCommandCount())
            assertFalse(functions.calls.any { it.startsWith("submit:") })
            session.close()
            val use = texture.recordUse(0)
            assertFalse(use.contentsDefined)
            use.discardRecording()
            texture.close()
        }
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val session = VulkanTransferSession.create(functions)
        functions.failureStage = "upload"
        functions.failure = AssertionError("native image Error")
        assertSame(functions.failure, assertFailsWith<AssertionError> { session.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84)) } })
        session.close()
        texture.close()
    }

    @Test
    fun preSubmissionAndOutOfMemoryFailuresRollBackWithoutClaimingCompletion() {
        for (stage in listOf("after", "end", "createFence", "hostOOM", "deviceOOM")) {
            val functions = FakeVulkanImageQueue()
            val texture = functions.texture()
            val session = VulkanTransferSession.create(functions)
            when (stage) {
                "hostOOM" -> functions.submission = VulkanSubmissionStatus.OutOfHostMemory
                "deviceOOM" -> functions.submission = VulkanSubmissionStatus.OutOfDeviceMemory
                else -> functions.failureStage = stage
            }
            assertFailsWith<IllegalStateException> { session.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84)) } }
            assertTrue(functions.calls.any { it.startsWith("destroyRecording:") })
            session.close()
            val use = texture.recordUse(0)
            assertFalse(use.contentsDefined)
            use.discardRecording()
            texture.close()
        }
    }

    @Test
    fun uncertainSubmissionAndLostCompletionKeepImageAndStagingReserved() {
        for (mode in listOf("submitThrows", "submitLost", "pollThrows", "waitThrows", "fenceLost")) {
            val functions = FakeVulkanImageQueue()
            val texture = functions.texture()
            val session = VulkanTransferSession.create(functions)
            lateinit var read: VulkanBufferReadback
            if (mode == "submitThrows") functions.failureStage = "submit"
            if (mode == "submitLost") functions.submission = VulkanSubmissionStatus.DeviceLost
            val record = {
                session.record { images.writeTexture(ImageRegion.Texture(texture), ByteArray(84)); read = images.readTexture(ImageRegion.Texture(texture)) }
            }
            if (mode.startsWith("submit")) assertFailsWith<IllegalStateException>(block = record) else {
                record()
                if (mode == "pollThrows") functions.failureStage = "poll"
                if (mode == "waitThrows") functions.failureStage = "wait"
                if (mode == "fenceLost") functions.completion = VulkanFenceStatus.DeviceLost
                assertFailsWith<IllegalStateException> { if (mode == "waitThrows") session.awaitCompletion(0) else session.pollCompletion() }
            }
            assertFailsWith<IllegalStateException> { read.readBytes() }
            assertFailsWith<IllegalStateException> { texture.recordUse(0) }
            assertFailsWith<IllegalStateException> { session.discard() }
            assertTrue(functions.calls.none { it.startsWith("destroyRecording:") || it.startsWith("destroyImage:") })
            assertTrue(functions.host.calls.none { it.startsWith("unmap:") })
        }
    }

    @Test
    fun queueFamilyAndPrivateUseTransitionsCannotChangeCommittedStateIllegally() {
        val functions = FakeVulkanImageQueue()
        val texture = functions.texture()
        val use = texture.recordUse(0)
        assertFalse(use.contentsDefined)
        assertFailsWith<IllegalStateException> { texture.recordUse(0) }
        assertFailsWith<IllegalStateException> { use.completeSubmission() }
        functions.bufferState.accessAllowed = false
        assertFailsWith<IllegalStateException> { use.markSubmitted() }
        assertFailsWith<IllegalStateException> { use.discardRecording() }
        functions.bufferState.accessAllowed = true
        use.markFullUpload()
        use.markSubmitted()
        assertFailsWith<IllegalStateException> { use.handle }
        assertFailsWith<IllegalStateException> { use.markFullUpload() }
        assertFailsWith<IllegalStateException> { use.discardRecording() }
        functions.bufferState.accessAllowed = false
        assertFailsWith<IllegalStateException> { use.completeSubmission() }
        functions.bufferState.accessAllowed = true
        use.completeSubmission()
        assertFailsWith<IllegalStateException> { use.completeSubmission() }
        assertFailsWith<UnsupportedOperationException> { texture.recordUse(1) }
        val sameFamily = texture.recordUse(0)
        assertTrue(sameFamily.contentsDefined)
        sameFamily.discardRecording()
        texture.close()
        assertFailsWith<IllegalStateException> { texture.recordUse(0) }
    }

    @Test
    fun samePackageConsumersCannotCreateUnregisteredImageUsesOrRemoveReservations() {
        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler())
        val classpath = listOf(VulkanTexture::class.java, VulkanImageUse::class.java, GpuTexture::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        val directory = Files.createTempDirectory("vulkan-image-use-entry-").toFile()
        try {
            val cases = listOf(
                "VulkanImageUse use = texture.recordUse(0); use.discardRecording();" to true,
                "VulkanImageUse use = new VulkanImageUse(texture);" to false,
                "VulkanImageUse use = texture.recordUse(0); texture.releaseUse(use);" to false,
                "VulkanImageUse use = new VulkanTexture.Use(texture, 0);" to false,
            )
            for ([index, case] in cases.withIndex()) {
                val source = File(directory, "ImageUseEntry$index.java")
                source.writeText("package heckerpowered.render.vulkan.resource; class ImageUseEntry$index { static void run(VulkanTexture texture) { ${case.first} } }")
                val diagnostics = ByteArrayOutputStream()
                val result = compiler.run(null, null, diagnostics, "-proc:none", "-classpath", classpath, "-d", directory.path, source.path)
                if (case.second) assertEquals(0, result, diagnostics.toString()) else assertNotEquals(0, result, diagnostics.toString())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pendingImageAndSessionAndCleanupFailuresHaltBeforeUnsafeDestruction() {
        val classpath = listOf(VulkanImageTransferFatalProbe::class.java, VulkanTransferSession::class.java, GpuTexture::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (mode in listOf("recordedImage", "pendingImage", "pendingSession", "uncertainSession", "lostImage", "destroyRecording", "unmap")) {
            val output = File.createTempFile("vulkan-image-transfer-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanImageTransferFatalProbe::class.java.name, mode)
                    .redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, "Image fatal probe timed out: $mode")
                val log = output.readText()
                assertEquals(1, process.exitValue(), log)
                assertTrue("fatal-image-transfer-start" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("shutdown-hook" in log, log)
                assertFalse("destroyImage:" in log, log)
                if (mode == "recordedImage" || mode == "pendingImage" || mode == "lostImage") assertTrue("still has a recorded or pending use" in log, log)
            } finally {
                output.delete()
            }
        }
    }
}

private fun imagePattern(width: Int, height: Int, seed: Int): ByteArray = ByteArray(width * height * 4) { index ->
    val pixel = index / 4
    val x = pixel % width
    val y = pixel / width
    (seed + x * 17 + y * 37 + (index % 4) * 53).toByte()
}

/** CPU-only queued image/byte model; it never loads a Vulkan binding or driver. */
private class FakeVulkanImageQueue : VulkanTransferFunctions, VulkanImageTransferFunctions {
    val bufferState = FakeVulkanBufferFunctions()
    private var nextBuffer = 7L
    private var nextMemory = 9L
    private var nextImage = 1000L
    private var nextView = 2000L
    private var nextRecording = 1L
    private var nextFence = 100L
    private val bufferSizes = mutableMapOf<Long, Long>()
    private val bufferMemories = mutableMapOf<Long, Long>()
    private val pixels = mutableMapOf<Long, ByteArray>()
    private val generalLayouts = mutableMapOf<Long, Boolean>()
    private val commands = mutableMapOf<Long, MutableList<() -> Unit>>()
    private val submissions = mutableMapOf<Long, Long>()
    private val executed = mutableSetOf<Long>()
    val calls = mutableListOf<String>()
    var failureStage = ""
    var failure: Throwable = IllegalStateException("image transfer failure")
    var completion = VulkanFenceStatus.Pending
    var submission = VulkanSubmissionStatus.Accepted
    var provideImages = true
    var provideHost = true
    var printCalls = false
    var onDestroyRecording: () -> Unit = {}
    override var queueFamilyIndex = 0

    override val bufferFunctions = object : VulkanBufferFunctions by bufferState {
        override fun createBuffer(sizeBytes: Long, usageFlags: Int): Long {
            call("createBuffer", "createBuffer:$sizeBytes:$usageFlags")
            val buffer = nextBuffer++
            bufferSizes[buffer] = sizeBytes
            return buffer
        }
        override fun <R> withBufferMemoryRequirements(buffer: Long, consume: (Long, Long, Int, Boolean) -> R): R = consume((bufferSizes.getValue(buffer) + 127) / 128 * 128, 128, 3, false)
        override fun allocateMemory(sizeBytes: Long, memoryTypeIndex: Int, dedicatedBuffer: Long): Long {
            call("allocateBuffer", "allocateBuffer:$sizeBytes:$memoryTypeIndex")
            val memory = nextMemory++
            host.allocate(memory, sizeBytes)
            return memory
        }
        override fun bindBufferMemory(buffer: Long, memory: Long, offsetBytes: Long) {
            call("bindBuffer", "bindBuffer:$buffer:$memory")
            bufferMemories[buffer] = memory
            host.bind(buffer, memory)
        }
    }
    val host: FakeVulkanHostMemoryFunctions = FakeVulkanHostMemoryFunctions(bufferFunctions)
    override val hostMemoryFunctions get() = if (provideHost) host else null
    override val imageTransferFunctions get() = if (provideImages) this else null
    override val imageFunctions = object : VulkanImageFunctions {
        override val bufferFunctions get() = this@FakeVulkanImageQueue.bufferFunctions
        override fun checkTextureSupport(description: TextureDescription, usageFlags: Int) = Unit
        override fun createImage(description: TextureDescription, usageFlags: Int): Long {
            val image = nextImage++
            pixels[image] = ByteArray(description.width * description.height * 4) { 0xCC.toByte() }
            generalLayouts[image] = false
            return image
        }
        override fun <R> withImageMemoryRequirements(image: Long, consume: (Long, Long, Int, Boolean) -> R): R = consume((pixels.getValue(image).size.toLong() + 127) / 128 * 128, 128, 3, false)
        override fun allocateImageMemory(sizeBytes: Long, memoryTypeIndex: Int, dedicatedImage: Long, label: String): Long = nextMemory++
        override fun bindImageMemory(image: Long, memory: Long, label: String) = Unit
        override fun createImageView(image: Long, description: TextureViewDescription): Long = nextView++
        override fun destroyImageView(view: Long) = call("destroyView", "destroyView:$view")
        override fun destroyImage(image: Long) = call("destroyImage", "destroyImage:$image")
    }

    fun texture(width: Int = 7, height: Int = 3, usage: Set<TextureUsage> = setOf(TextureUsage.Sampled, TextureUsage.ColorAttachment, TextureUsage.TransferSource, TextureUsage.TransferDestination)): VulkanTexture =
        createVulkanTexture(imageFunctions, TextureDescription("image", width, height, format = TextureFormat.Rgba8UnsignedNormalized, usage = usage)) as VulkanTexture

    fun imageBytes(texture: VulkanTexture): ByteArray = pixels.getValue(texture.handle).copyOf()
    fun imageCommandCount(): Int = calls.count { it.startsWith("imageBarrier:") || it.startsWith("upload:") || it.startsWith("readback:") }
    fun barrierBaselines(): List<Boolean> = calls.filter { it.startsWith("imageBarrier:") }.map { it.endsWith(":true") }

    private fun call(stage: String, details: String) {
        calls.add(details)
        if (printCalls) println(details)
        if (failureStage == stage) throw failure
    }

    override fun createRecording(): VulkanTransferRecording {
        val recording = nextRecording++
        commands[recording] = mutableListOf()
        call("createRecording", "createRecording:$recording")
        return VulkanTransferRecording(recording, recording)
    }
    override fun beforeBufferCopy(recording: VulkanTransferRecording) = call("before", "before:${recording.commandBuffer}")
    override fun afterBufferCopies(recording: VulkanTransferRecording) = call("after", "after:${recording.commandBuffer}")
    override fun endRecording(recording: VulkanTransferRecording) = call("end", "end:${recording.commandBuffer}")
    override fun copyBuffer(recording: VulkanTransferRecording, source: Long, sourceOffsetBytes: Long, destination: Long, destinationOffsetBytes: Long, sizeBytes: Long) {
        call("copyBuffer", "copyBuffer:${recording.commandBuffer}")
        commands.getValue(recording.commandBuffer).add { host.copy(source, sourceOffsetBytes, destination, destinationOffsetBytes, sizeBytes) }
    }
    override fun beforeImageTransfer(recording: VulkanTransferRecording, image: Long, contentsDefined: Boolean) {
        call("imageBarrier", "imageBarrier:$image:$contentsDefined")
        commands.getValue(recording.commandBuffer).add {
            check(generalLayouts.getValue(image) == contentsDefined) { "Recorded old layout disagrees with simulated GPU layout" }
            generalLayouts[image] = true
        }
    }
    override fun copyBufferToImage(recording: VulkanTransferRecording, buffer: Long, image: Long, width: Int, height: Int) {
        call("upload", "upload:$image:$width:$height")
        commands.getValue(recording.commandBuffer).add {
            val bytes = ByteArray(width * height * 4)
            host.readMappedBytes(bufferMemories.getValue(buffer), bytes)
            bytes.copyInto(pixels.getValue(image))
        }
    }
    override fun copyImageToBuffer(recording: VulkanTransferRecording, image: Long, buffer: Long, width: Int, height: Int) {
        call("readback", "readback:$image:$width:$height")
        commands.getValue(recording.commandBuffer).add { host.writeMappedBytes(bufferMemories.getValue(buffer), pixels.getValue(image)) }
    }
    override fun createFence(): Long { call("createFence", "createFence"); return nextFence++ }
    override fun submit(recording: VulkanTransferRecording, fence: Long): VulkanSubmissionStatus {
        call("submit", "submit:${recording.commandBuffer}")
        if (submission == VulkanSubmissionStatus.Accepted) submissions[fence] = recording.commandBuffer
        return submission
    }
    private fun completion(fence: Long): VulkanFenceStatus {
        if (completion == VulkanFenceStatus.Complete && executed.add(fence)) for (command in commands.getValue(submissions.getValue(fence))) command()
        return completion
    }
    override fun fenceStatus(fence: Long): VulkanFenceStatus { call("poll", "poll:$fence"); return completion(fence) }
    override fun waitForFence(fence: Long, timeoutNanoseconds: Long): VulkanFenceStatus { call("wait", "wait:$timeoutNanoseconds"); return completion(fence) }
    override fun destroyRecording(recording: VulkanTransferRecording) {
        call("destroyRecording", "destroyRecording:${recording.commandBuffer}")
        onDestroyRecording()
        check(commands.remove(recording.commandBuffer) != null)
    }
    override fun destroyFence(fence: Long) = call("destroyFence", "destroyFence:$fence")
}

object VulkanImageTransferFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        println("fatal-image-transfer-start")
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val mode = arguments.single()
        val functions = FakeVulkanImageQueue().apply {
            printCalls = true
            host.printCalls = true
            if (mode == "uncertainSession") failureStage = "submit"
            if (mode == "lostImage") submission = VulkanSubmissionStatus.DeviceLost
        }
        val texture = functions.texture()
        val session = VulkanTransferSession.create(functions)
        try {
            session.record {
                images.writeTexture(ImageRegion.Texture(texture), ByteArray(84))
                if (mode == "recordedImage") texture.close()
            }
        } catch (failure: IllegalStateException) {
            if (mode != "uncertainSession" && mode != "lostImage") throw failure
        }
        when (mode) {
            "pendingImage", "lostImage" -> texture.close()
            "pendingSession", "uncertainSession" -> session.close()
            "destroyRecording" -> {
                functions.failureStage = "destroyRecording"
                functions.completion = VulkanFenceStatus.Complete
                session.pollCompletion()
            }
            "unmap" -> {
                functions.completion = VulkanFenceStatus.Complete
                session.pollCompletion()
                functions.host.failureStage = "unmap"
                session.close()
            }
        }
        println("after-close")
    }
}
