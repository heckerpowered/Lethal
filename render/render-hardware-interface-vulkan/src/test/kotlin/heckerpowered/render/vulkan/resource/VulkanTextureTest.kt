/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.resource

import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.texture.*
import heckerpowered.render.vulkan.function.VulkanBufferFunctions
import heckerpowered.render.vulkan.function.VulkanImageFunctions
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*

class VulkanTextureTest {
    @Test
    fun createsEngineColorImageAndOwnsAllNativeViewsUntilFinalRelease() {
        val functions = FakeVulkanImageFunctions()
        val texture = createVulkanTexture(functions, description()) as VulkanTexture
        assertEquals(listOf("support:20", "create:7:3:20", "query:7", "properties", "allocate:128:1:0:color", "bind:7:9:color"), functions.calls)
        assertEquals(128L, texture.allocationSizeBytes)
        assertEquals(1, texture.memoryPropertyFlags)
        assertEquals(7L, texture.handle)
        assertEquals(TextureDimension.TwoDimensional, texture.dimension)
        assertEquals(TextureStorage.Backed, texture.storage)
        assertEquals(TextureFormat.Rgba8UnsignedNormalized, texture.format)
        assertEquals(SampleCount.One, texture.sampleCount)
        assertEquals(7, texture.width)
        assertEquals(3, texture.height)
        assertEquals(1, texture.depth)
        assertEquals(1, texture.mipLevelCount)
        assertEquals(1, texture.arrayLayerCount)
        assertFalse(texture.cubeCompatible)
        val first = createVulkanTextureView(functions, texture, TextureViewDescription("first")) as VulkanTextureView
        val second = createVulkanTextureView(functions, texture, TextureViewDescription("second", dimension = TextureViewDimension.TwoDimensionalArray)) as VulkanTextureView
        assertEquals(17L, first.requireNativeView())
        assertEquals(18L, second.requireNativeView())
        val attachment = createVulkanAttachmentView(functions, second) as VulkanTextureAttachment
        assertSame(second, attachment.requireView(functions))
        assertEquals(7, attachment.width)
        assertEquals(3, attachment.height)
        assertEquals(1, attachment.arrayLayerCount)
        assertEquals(setOf(TextureAspect.Color), attachment.aspects)
        texture.close()
        texture.close()
        assertEquals(listOf("destroyView:18", "destroyView:17", "destroyImage:7", "free:9"), functions.calls.takeLast(4))
        assertFailsWith<IllegalStateException> { first.requireNativeView() }
        assertFailsWith<IllegalStateException> { attachment.requireView(functions) }
    }

    @Test
    fun viewsPreserveExactRolesAndImmutableMetadata() {
        val roles = mutableSetOf(TextureUsage.ColorAttachment)
        val aspects = mutableSetOf(TextureAspect.Color)
        val functions = FakeVulkanImageFunctions()
        val texture = createVulkanTexture(functions, description(usage = roles))
        roles.add(TextureUsage.TransferDestination)
        val selection = TextureViewDescription("view", aspects = aspects)
        aspects.clear()
        val view = createVulkanTextureView(functions, texture, selection)
        assertEquals(setOf(TextureUsage.ColorAttachment), texture.usage)
        assertEquals(setOf(TextureAspect.Color), view.aspects)
        assertEquals(0, view.baseMipLevel)
        assertEquals(1, view.mipLevelCount)
        assertEquals(0, view.baseArrayLayer)
        assertEquals(1, view.arrayLayerCount)
        assertEquals(TextureViewDimension.TwoDimensional, view.dimension)
        assertSame(texture, view.texture)
        assertEquals(texture.format, view.format)
        assertFailsWith<UnsupportedOperationException> { (texture.usage as MutableSet<TextureUsage>).add(TextureUsage.Sampled) }
        assertFailsWith<UnsupportedOperationException> { (view.aspects as MutableSet<TextureAspect>).clear() }
        createVulkanAttachmentView(functions, view)
        assertEquals("support:16", functions.calls.first())
        texture.close()
    }

    @Test
    fun transferOnlyViewsDoNotAllocateIllegalNativeBindingViews() {
        val functions = FakeVulkanImageFunctions()
        val texture = createVulkanTexture(functions, description(usage = setOf(TextureUsage.TransferSource, TextureUsage.TransferDestination)))
        val view = createVulkanTextureView(functions, texture, TextureViewDescription()) as VulkanTextureView
        assertSame(texture, view.texture)
        assertEquals(7, view.width)
        assertEquals(3, view.height)
        assertFailsWith<UnsupportedOperationException> { view.requireNativeView() }
        assertFailsWith<IllegalArgumentException> { createVulkanAttachmentView(functions, view) }
        assertFalse(functions.calls.any { it.startsWith("view:") })
        texture.close()
        assertEquals(listOf("destroyImage:7", "free:9"), functions.calls.takeLast(2))
    }

    @Test
    fun eachRepresentableRoleMapsWithoutGrantingAdditionalRhiRoles() {
        val flags = mapOf(
            TextureUsage.Sampled to 4,
            TextureUsage.Storage to 8,
            TextureUsage.ColorAttachment to 16,
            TextureUsage.InputAttachment to 128,
            TextureUsage.TransferSource to 1,
            TextureUsage.TransferDestination to 2,
            TextureUsage.ResolveDestination to 2,
        )
        for ([role, flag] in flags) {
            val functions = FakeVulkanImageFunctions()
            val texture = createVulkanTexture(functions, description(usage = setOf(role)))
            assertEquals("support:$flag", functions.calls.first())
            assertEquals(setOf(role), texture.usage)
            if (role != TextureUsage.TransferDestination) assertFalse(TextureUsage.TransferDestination in texture.usage)
            texture.close()
        }
    }

    @Test
    fun unsupportedValidShapesAndFormatsFailBeforeNativeQueries() {
        val requests = listOf(
            description(format = TextureFormat.Rgba16Float),
            description(format = TextureFormat.Rgba8UnsignedNormalizedSrgb),
            TextureDescription("mips", 4, 4, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.Sampled), mipLevelCount = 2),
            TextureDescription("array", 4, 4, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.Sampled), arrayLayerCount = 2),
            TextureDescription("msaa", 4, 4, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.ColorAttachment), sampleCount = SampleCount.Four),
            TextureDescription("memoryless", 4, 4, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.ColorAttachment), storage = TextureStorage.Memoryless),
            TextureDescription("row", 4, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.Sampled), dimension = TextureDimension.OneDimensional),
            TextureDescription("volume", 4, 4, 4, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.Sampled), dimension = TextureDimension.ThreeDimensional),
            TextureDescription("cube", 4, 4, format = TextureFormat.Rgba8UnsignedNormalized, usage = setOf(TextureUsage.Sampled), arrayLayerCount = 6, cubeCompatible = true),
        )
        for (request in requests) {
            val functions = FakeVulkanImageFunctions()
            assertFailsWith<UnsupportedOperationException> { createVulkanTexture(functions, request) }
            assertTrue(functions.calls.isEmpty())
        }
        val functions = FakeVulkanImageFunctions().apply {
            failureStage = "support"
            failure = UnsupportedOperationException("Exact native image combination is unsupported")
        }
        assertSame(functions.failure, assertFailsWith<UnsupportedOperationException> { createVulkanTexture(functions, description()) })
        assertEquals(listOf("support:20"), functions.calls)
    }

    @Test
    fun inconsistentMultipleRolesAreRejectedRegardlessOfCommonConstructorChecks() {
        for (role in listOf(TextureUsage.DepthStencilAttachment, TextureUsage.ResolveSource)) {
            val functions = FakeVulkanImageFunctions()
            assertFailsWith<IllegalArgumentException> {
                createVulkanTexture(functions, description(usage = setOf(TextureUsage.ColorAttachment, role)))
            }
            assertTrue(functions.calls.isEmpty())
        }
    }

    @Test
    fun allocationUsesNativeImageRequirementsRatherThanLinearTexelSize() {
        val functions = FakeVulkanImageFunctions().apply {
            size = 32
            alignment = 16
            dedicated = true
        }
        val texture = createVulkanTexture(functions, description()) as VulkanTexture
        assertEquals(32L, texture.allocationSizeBytes)
        assertTrue("allocate:32:1:7:color" in functions.calls)
        assertTrue("bind:7:9:color" in functions.calls)
        texture.close()
    }

    @Test
    fun nativeMemoryTypeBitsAndStablePreferenceArePreservedBySharedSelector() {
        val cases = listOf(
            Triple(intArrayOf(2, 1, 1), 7, 1),
            Triple(intArrayOf(2, 1), 1, 0),
            Triple(intArrayOf(0x11, 1), 3, 1),
            Triple(IntArray(32).apply { this[31] = 1 }, Int.MIN_VALUE, 31),
        )
        for ([properties, bits, index] in cases) {
            val functions = FakeVulkanImageFunctions().apply { memoryTypes = properties; typeBits = bits }
            val texture = createVulkanTexture(functions, description()) as VulkanTexture
            assertTrue("allocate:128:$index:0:color" in functions.calls)
            assertEquals(properties[index], texture.memoryPropertyFlags)
            texture.close()
        }
    }

    @Test
    fun selectedMemoryPropertiesSurviveNativeScratchReuse() {
        val functions = FakeVulkanImageFunctions().apply { duringAllocation = { memoryTypes.fill(0) } }
        val texture = createVulkanTexture(functions, description()) as VulkanTexture
        assertEquals(1, texture.memoryPropertyFlags)
        texture.close()
    }

    @Test
    fun malformedRequirementsAndUnpermittedMemoryReleaseTheCreatedImage() {
        val cases = listOf<(FakeVulkanImageFunctions) -> Unit>(
            { it.size = 0 }, { it.alignment = 0 }, { it.alignment = 3 }, { it.typeBits = 0 },
            { it.memoryTypes = intArrayOf() }, { it.memoryTypes = IntArray(33) },
        )
        for (setup in cases) {
            val functions = FakeVulkanImageFunctions().apply { setup(this) }
            assertFailsWith<IllegalArgumentException> { createVulkanTexture(functions, description()) }
            assertEquals(listOf("destroyImage:7"), functions.cleanupCalls())
        }
        val functions = FakeVulkanImageFunctions().apply { memoryTypes = intArrayOf(0x11, 0x20, 0x40) }
        assertFailsWith<UnsupportedOperationException> { createVulkanTexture(functions, description()) }
        assertEquals(listOf("destroyImage:7"), functions.cleanupCalls())
    }

    @Test
    fun operationalFailuresReleaseOnlyAcquiredResourcesAndPreserveFailure() {
        val cases = mapOf(
            "support" to emptyList(), "create" to emptyList(),
            "query" to listOf("destroyImage:7"), "properties" to listOf("destroyImage:7"),
            "allocate" to listOf("destroyImage:7"), "bind" to listOf("destroyImage:7", "free:9"),
        )
        for ([stage, cleanup] in cases) {
            val functions = FakeVulkanImageFunctions().apply { failureStage = stage }
            assertSame(functions.failure, assertFailsWith<TextureCreationException> { createVulkanTexture(functions, description()) })
            assertEquals(cleanup, functions.cleanupCalls())
        }
        val functions = FakeVulkanImageFunctions().apply { failureStage = "query"; failure = AssertionError("native query Error") }
        assertSame(functions.failure, assertFailsWith<AssertionError> { createVulkanTexture(functions, description()) })
        assertEquals(listOf("destroyImage:7"), functions.cleanupCalls())
    }

    @Test
    fun nullCreationHandlesNeverPublishAnInvalidResource() {
        val functions = FakeVulkanImageFunctions().apply { imageHandle = 0 }
        assertFailsWith<TextureCreationException> { createVulkanTexture(functions, description()) }
        assertTrue(functions.cleanupCalls().isEmpty())
        functions.imageHandle = 7
        functions.memoryHandle = 0
        assertFailsWith<TextureCreationException> { createVulkanTexture(functions, description()) }
        assertEquals(listOf("destroyImage:7"), functions.cleanupCalls())
    }

    @Test
    fun failedViewCreationDoesNotReleaseTheImageOrRegisterAnInvalidView() {
        val functions = FakeVulkanImageFunctions()
        val texture = createVulkanTexture(functions, description())
        functions.failureStage = "view"
        functions.failure = TextureViewCreationException("view failure")
        assertSame(functions.failure, assertFailsWith<TextureViewCreationException> { createVulkanTextureView(functions, texture, TextureViewDescription()) })
        assertTrue(functions.cleanupCalls().isEmpty())
        functions.failureStage = ""
        functions.nextViewHandle = 0
        assertFailsWith<TextureViewCreationException> { createVulkanTextureView(functions, texture, TextureViewDescription()) }
        functions.nextViewHandle = 17
        createVulkanTextureView(functions, texture, TextureViewDescription())
        texture.close()
        assertEquals(listOf("destroyView:17", "destroyImage:7", "free:9"), functions.cleanupCalls())
    }

    @Test
    fun viewSelectionOwnerAndThreadFailuresPrecedeNativeCalls() {
        val functions = FakeVulkanImageFunctions()
        val texture = createVulkanTexture(functions, description())
        val invalid = listOf(
            TextureViewDescription(baseMipLevel = 1), TextureViewDescription(mipLevelCount = 2),
            TextureViewDescription(baseArrayLayer = 1), TextureViewDescription(dimension = TextureViewDimension.TwoDimensionalArray, arrayLayerCount = 2),
            TextureViewDescription(aspects = setOf(TextureAspect.Depth)), TextureViewDescription(dimension = TextureViewDimension.OneDimensional),
        )
        for (selection in invalid) assertFailsWith<IllegalArgumentException> { createVulkanTextureView(functions, texture, selection) }
        val foreign = FakeVulkanImageFunctions()
        assertFailsWith<IllegalArgumentException> { createVulkanTextureView(foreign, texture, TextureViewDescription()) }
        assertTrue(foreign.calls.isEmpty())
        assertFalse(functions.calls.any { it.startsWith("view:") })
        val view = createVulkanTextureView(functions, texture, TextureViewDescription())
        assertFailsWith<IllegalArgumentException> { createVulkanAttachmentView(foreign, view) }
        functions.accessAllowed = false
        val before = functions.calls.toList()
        assertFailsWith<IllegalStateException> { createVulkanTexture(functions, description()) }
        assertFailsWith<IllegalStateException> { createVulkanTextureView(functions, texture, TextureViewDescription()) }
        assertEquals(before, functions.calls)
        functions.accessAllowed = true
        texture.close()
        assertFailsWith<IllegalStateException> { createVulkanTextureView(functions, texture, TextureViewDescription()) }
    }

    @Test
    fun sampledViewDoesNotGrantAttachmentAccess() {
        val functions = FakeVulkanImageFunctions()
        val texture = createVulkanTexture(functions, description(usage = setOf(TextureUsage.Sampled)))
        val view = createVulkanTextureView(functions, texture, TextureViewDescription())
        assertFailsWith<IllegalArgumentException> { createVulkanAttachmentView(functions, view) }
        texture.close()
    }

    @Test
    fun cleanupFailuresHaltBeforeReleasingDependentObjectsOrRunningShutdownHooks() {
        val classpath = listOf(VulkanTextureFatalProbe::class.java, VulkanImageFunctions::class.java, GpuTexture::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (stage in listOf("destroyView", "destroyImage", "free", "access", "failedCreationCleanup")) {
            val output = File.createTempFile("vulkan-image-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanTextureFatalProbe::class.java.name, stage)
                    .redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, "Fatal probe timed out: $stage")
                assertEquals(1, process.exitValue(), output.readText())
                val log = output.readText()
                assertTrue("fatal-probe-start" in log, log)
                assertTrue("fatal-probe-failure" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("shutdown-hook" in log, log)
                if (stage == "destroyView" || stage == "access") assertFalse("destroyImage:7" in log, log)
                if (stage != "free") assertFalse("free:9" in log, log)
            } finally {
                output.delete()
            }
        }
    }
}

private fun description(format: TextureFormat = TextureFormat.Rgba8UnsignedNormalized, usage: Set<TextureUsage> = setOf(TextureUsage.Sampled, TextureUsage.ColorAttachment)): TextureDescription =
    TextureDescription("color", 7, 3, format = format, usage = usage)

private class FakeVulkanImageFunctions : VulkanImageFunctions {
    val calls = mutableListOf<String>()
    var failure: Throwable = TextureCreationException("fatal-probe-failure")
    var failureStage = ""
    var accessAllowed = true
    var printCalls = false
    var imageHandle = 7L
    var memoryHandle = 9L
    var nextViewHandle = 17L
    var size = 128L
    var alignment = 64L
    var typeBits = 3
    var dedicated = false
    var memoryTypes = intArrayOf(2, 1)
    var duringAllocation: () -> Unit = {}

    override val bufferFunctions = object : VulkanBufferFunctions {
        override fun checkAccess() { check(accessAllowed) { "fatal-probe-failure: access" } }
        override fun memoryTypePropertyFlags(): IntArray { record("properties", "properties"); return memoryTypes }
        override fun createBuffer(sizeBytes: Long, usageFlags: Int): Long = error("Image creation must not allocate a buffer")
        override fun <R> withBufferMemoryRequirements(buffer: Long, consume: (Long, Long, Int, Boolean) -> R): R = error("Image creation must not query buffer requirements")
        override fun allocateMemory(sizeBytes: Long, memoryTypeIndex: Int, dedicatedBuffer: Long): Long = error("Dedicated image identity must not reach buffer allocation")
        override fun bindBufferMemory(buffer: Long, memory: Long, offsetBytes: Long): Unit = error("Image creation must not bind a buffer")
        override fun destroyBuffer(buffer: Long): Unit = error("Image cleanup must not destroy a buffer")
        override fun freeMemory(memory: Long) = record("free", "free:$memory")
    }

    override fun checkTextureSupport(description: TextureDescription, usageFlags: Int) = record("support", "support:$usageFlags")
    override fun createImage(description: TextureDescription, usageFlags: Int): Long { record("create", "create:${description.width}:${description.height}:$usageFlags"); return imageHandle }
    override fun <R> withImageMemoryRequirements(image: Long, consume: (Long, Long, Int, Boolean) -> R): R {
        record("query", "query:$image")
        return consume(size, alignment, typeBits, dedicated)
    }
    override fun allocateImageMemory(sizeBytes: Long, memoryTypeIndex: Int, dedicatedImage: Long, label: String): Long {
        record("allocate", "allocate:$sizeBytes:$memoryTypeIndex:$dedicatedImage:$label")
        duringAllocation()
        return memoryHandle
    }
    override fun bindImageMemory(image: Long, memory: Long, label: String) = record("bind", "bind:$image:$memory:$label")
    override fun createImageView(image: Long, description: TextureViewDescription): Long {
        record("view", "view:$image:${description.dimension}")
        return nextViewHandle++
    }
    override fun destroyImageView(view: Long) = record("destroyView", "destroyView:$view")
    override fun destroyImage(image: Long) = record("destroyImage", "destroyImage:$image")

    fun cleanupCalls(): List<String> = calls.filter { it.startsWith("destroy") || it.startsWith("free:") }

    private fun record(stage: String, call: String) {
        calls.add(call)
        if (printCalls) println(call)
        if (failureStage == stage) throw failure
    }
}

object VulkanTextureFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        println("fatal-probe-start")
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val functions = FakeVulkanImageFunctions().apply { printCalls = true }
        if (arguments.single() == "failedCreationCleanup") {
            functions.size = 0
            functions.failureStage = "destroyImage"
            createVulkanTexture(functions, description())
        } else {
            val texture = createVulkanTexture(functions, description())
            createVulkanTextureView(functions, texture, TextureViewDescription())
            if (arguments.single() == "access") functions.accessAllowed = false
            else functions.failureStage = arguments.single()
            texture.close()
        }
        println("after-close")
    }
}
