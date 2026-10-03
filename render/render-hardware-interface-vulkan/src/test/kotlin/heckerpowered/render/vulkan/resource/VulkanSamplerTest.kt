/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.resource

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.resource.sampler.*
import heckerpowered.render.shader.CanonicalShaderCompiler
import heckerpowered.render.vulkan.FakeVulkanCanonicalShaderCompiler
import heckerpowered.render.vulkan.VulkanGraphicsDevice
import heckerpowered.render.vulkan.testVulkanGraphicsDevice
import heckerpowered.render.vulkan.command.RasterTestQueue
import heckerpowered.render.vulkan.function.VulkanSamplerFunctions
import heckerpowered.render.vulkan.function.VulkanTransferFunctions
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*

class VulkanSamplerTest {
    @Test
    fun creationRetainsExactDescriptionAndOwnsOneFinalNativeRelease() {
        val functions = FakeVulkanSamplerFunctions(Any())
        val sampler = createVulkanSampler(functions, defaultVulkanSamplerDescription()) as VulkanSampler
        assertEquals(listOf(defaultVulkanSamplerDescription()), functions.descriptions)
        assertEquals(defaultVulkanSamplerDescription(), sampler.description)
        assertEquals(10000L, sampler.requireBinding(functions))
        sampler.close()
        sampler.close()
        assertEquals(listOf("create", "destroy:10000"), functions.calls)
        assertTrue(functions.live.isEmpty())
        assertFailsWith<IllegalStateException> { sampler.handle }
    }

    @Test
    fun everyUnsupportedFilterAddressAndMipmapFieldRejectsBeforeNativeCreation() {
        val functions = FakeVulkanSamplerFunctions(Any())
        val description = defaultVulkanSamplerDescription()
        val unsupported = listOf(
            description.copy(minificationFilter = TextureFilter.Nearest),
            description.copy(magnificationFilter = TextureFilter.Nearest),
            description.copy(addressModeU = SamplerAddressMode.Repeat),
            description.copy(addressModeV = SamplerAddressMode.Repeat),
            description.copy(addressModeW = SamplerAddressMode.Repeat),
            description.copy(mipmapMode = SamplerMipmapMode.Nearest),
            description.copy(mipmapMode = SamplerMipmapMode.Linear),
        )
        for (requested in unsupported) assertFailsWith<UnsupportedOperationException> { createVulkanSampler(functions, requested) }
        assertTrue(functions.calls.isEmpty())
    }

    @Test
    fun failedNativeCreationPropagatesExceptionsAndErrorsWithoutDestroyingAnAbsentHandle() {
        for (failure in listOf(IllegalStateException("allocation failure"), AssertionError("creation Error"))) {
            val functions = FakeVulkanSamplerFunctions(Any()).apply { creationFailure = failure }
            if (failure is AssertionError) assertSame(failure, assertFailsWith<AssertionError> { createVulkanSampler(functions, defaultVulkanSamplerDescription()) })
            else assertSame(failure, assertFailsWith<IllegalStateException> { createVulkanSampler(functions, defaultVulkanSamplerDescription()) })
            assertEquals(listOf("create"), functions.calls)
            assertTrue(functions.live.isEmpty())
        }
    }

    @Test
    fun zeroNativeHandleCannotBecomeAResource() {
        val functions = FakeVulkanSamplerFunctions(Any()).apply { returnZero = true }
        assertFailsWith<IllegalStateException> { createVulkanSampler(functions, defaultVulkanSamplerDescription()) }
        assertEquals(listOf("create"), functions.calls)
        assertTrue(functions.live.isEmpty())
    }

    @Test
    fun creationAndHandleAccessEnforceTheRecordingThreadAndAccessGuard() {
        val wrongThread = FakeVulkanSamplerFunctions(Any(), Thread("other recording thread"))
        assertFailsWith<IllegalStateException> { createVulkanSampler(wrongThread, defaultVulkanSamplerDescription()) }
        assertTrue(wrongThread.calls.isEmpty())
        val functions = FakeVulkanSamplerFunctions(Any())
        val sampler = createVulkanSampler(functions, defaultVulkanSamplerDescription()) as VulkanSampler
        functions.accessAllowed = false
        assertFailsWith<IllegalStateException> { sampler.handle }
        assertFailsWith<IllegalStateException> { createVulkanSampler(functions, defaultVulkanSamplerDescription()) }
        assertEquals(listOf("create"), functions.calls)
        functions.accessAllowed = true
        sampler.close()
    }

    @Test
    fun assemblyRejectsAnotherNativeDeviceBeforeCreatingAnySampler() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(Any())
        assertFailsWith<IllegalArgumentException> { device(queue, functions) }
        assertTrue(functions.calls.isEmpty())
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun assemblyRejectsAFunctionGroupBoundToAnotherRecordingThread() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity, Thread("other recording thread"))
        assertFailsWith<IllegalStateException> { device(queue, functions) }
        assertTrue(functions.calls.isEmpty())
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun mandatoryCompilerJvmConstructorKeepsSamplerCapabilityOptional() {
        val constructor = VulkanGraphicsDevice::class.java.getConstructor(VulkanTransferFunctions::class.java, CanonicalShaderCompiler::class.java, Collection::class.java, MemoryStack::class.java, java.lang.Boolean.TYPE, VulkanSamplerFunctions::class.java)
        assertFailsWith<NoSuchMethodException> { VulkanGraphicsDevice::class.java.getConstructor(VulkanTransferFunctions::class.java, Collection::class.java, MemoryStack::class.java, java.lang.Boolean.TYPE) }
        assertFailsWith<NoSuchMethodException> { VulkanGraphicsDevice::class.java.getConstructor(VulkanTransferFunctions::class.java, Collection::class.java, MemoryStack::class.java, java.lang.Boolean.TYPE, VulkanSamplerFunctions::class.java) }
        val queue = RasterTestQueue()
        val compiler = FakeVulkanCanonicalShaderCompiler()
        val device = constructor.newInstance(queue, compiler, emptyList<Any>(), MemoryStack(), false, null)
        try {
            assertFailsWith<UnsupportedOperationException> { device.createSampler(defaultVulkanSamplerDescription()) }
            assertFailsWith<UnsupportedOperationException> { device.resolveSampler(defaultVulkanSamplerDescription()) }
            assertTrue(queue.calls.isEmpty())
        } finally { device.close() }
    }

    @Test
    fun independentlyCreatedSamplersBelongToTheCallerWhileResolvedSamplerBelongsToTheDevice() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val device = device(queue, functions)
        val first = device.createSampler(defaultVulkanSamplerDescription()) as VulkanSampler
        val second = device.createSampler(defaultVulkanSamplerDescription()) as VulkanSampler
        val cached = device.resolveSampler(defaultVulkanSamplerDescription()) as VulkanSampler
        assertNotSame(first, second)
        assertNotSame(first, cached)
        first.close()
        second.close()
        assertEquals(setOf(cached.handle), functions.live)
        device.close()
        device.close()
        assertTrue(functions.live.isEmpty())
        assertEquals(3, functions.calls.count { it == "create" })
        assertEquals(3, functions.calls.count { it.startsWith("destroy:") })
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun equalDescriptionsReuseTheLiveBorrowedSamplerWithoutNativeCreation() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val device = device(queue, functions)
        try {
            val first = device.resolveSampler(defaultVulkanSamplerDescription())
            assertSame(first, device.resolveSampler(defaultVulkanSamplerDescription().copy()))
            assertSame(first, device.requireSampler(first))
            assertEquals(listOf("create"), functions.calls)
        } finally { device.close() }
        assertEquals(listOf("create", "destroy:10000"), functions.calls)
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun failedResolveDoesNotPublishACacheEntryAndCanRetry() {
        val queue = RasterTestQueue()
        val failure = IllegalStateException("sampler allocation failure")
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity).apply { creationFailure = failure }
        val device = device(queue, functions)
        try {
            assertSame(failure, assertFailsWith<IllegalStateException> { device.resolveSampler(defaultVulkanSamplerDescription()) })
            functions.creationFailure = null
            val sampler = device.resolveSampler(defaultVulkanSamplerDescription())
            assertSame(sampler, device.resolveSampler(defaultVulkanSamplerDescription()))
            assertEquals(listOf("create", "create"), functions.calls)
        } finally { device.close() }
        assertEquals(listOf("create", "create", "destroy:10000"), functions.calls)
    }

    @Test
    fun closedCacheHitIsRejectedRatherThanPublishedAgain() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val device = device(queue, functions)
        device.resolveSampler(defaultVulkanSamplerDescription()).close()
        assertFailsWith<IllegalStateException> { device.resolveSampler(defaultVulkanSamplerDescription()) }
        assertEquals(listOf("create", "destroy:10000"), functions.calls)
        device.close()
        assertEquals(listOf("create", "destroy:10000"), functions.calls)
    }

    @Test
    fun logicalDeviceBindingIsDistinctEvenWhenNativeDeviceAndSamplerFunctionsAreShared() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val first = device(queue, functions)
        val second = device(queue, functions)
        val sampler = first.createSampler(defaultVulkanSamplerDescription())
        val standalone = createVulkanSampler(functions, defaultVulkanSamplerDescription())
        try {
            assertSame(sampler, first.requireSampler(sampler))
            assertFailsWith<IllegalArgumentException> { second.requireSampler(sampler) }
            assertFailsWith<IllegalArgumentException> { first.requireSampler(standalone) }
            val foreign = object : GpuSampler { override fun close() {} }
            assertFailsWith<IllegalArgumentException> { first.requireSampler(foreign) }
        } finally {
            standalone.close()
            sampler.close()
            second.close()
            first.close()
        }
        assertTrue(functions.live.isEmpty())
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun closedDeviceRejectsCreateResolveAndUseBeforeNativeCalls() {
        val queue = RasterTestQueue()
        val functions = FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val device = device(queue, functions)
        val sampler = device.createSampler(defaultVulkanSamplerDescription())
        sampler.close()
        device.close()
        assertFailsWith<IllegalStateException> { device.createSampler(defaultVulkanSamplerDescription()) }
        assertFailsWith<IllegalStateException> { device.resolveSampler(defaultVulkanSamplerDescription()) }
        assertFailsWith<IllegalStateException> { device.requireSampler(sampler) }
        assertEquals(listOf("create", "destroy:10000"), functions.calls)
    }

    @Test
    fun destructionFailuresHaltWithoutReturningOrRunningShutdownHooks() {
        val classpath = listOf(VulkanSamplerFatalProbe::class.java, VulkanSamplerFunctions::class.java, GpuSampler::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (stage in listOf("destroy", "access")) {
            val output = File.createTempFile("vulkan-sampler-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanSamplerFatalProbe::class.java.name, stage)
                    .redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, "Sampler fatal probe timed out: $stage")
                assertEquals(1, process.exitValue(), output.readText())
                val log = output.readText()
                assertTrue("sampler-fatal-start" in log, log)
                assertTrue("sampler-fatal-failure" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("shutdown-hook" in log, log)
            } finally { output.delete() }
        }
    }

    private fun device(queue: RasterTestQueue, functions: VulkanSamplerFunctions): VulkanGraphicsDevice =
        testVulkanGraphicsDevice(queue, emptyList(), MemoryStack(), false, functions)
}

internal fun defaultVulkanSamplerDescription(): SamplerDescription = SamplerDescription(
    minificationFilter = TextureFilter.Linear,
    magnificationFilter = TextureFilter.Linear,
    addressModeU = SamplerAddressMode.ClampToEdge,
    addressModeV = SamplerAddressMode.ClampToEdge,
    addressModeW = SamplerAddressMode.ClampToEdge,
    mipmapMode = SamplerMipmapMode.Disabled,
)

internal class FakeVulkanSamplerFunctions(
    override val deviceIdentity: Any,
    private val recordingThread: Thread = Thread.currentThread(),
) : VulkanSamplerFunctions {
    val descriptions = mutableListOf<SamplerDescription>()
    val calls = mutableListOf<String>()
    val live = mutableSetOf<Long>()
    var accessAllowed = true
    var creationFailure: Throwable? = null
    var returnZero = false
    var failDestroy = false
    private var nextSampler = 10000L

    override fun checkAccess() {
        check(accessAllowed && Thread.currentThread() === recordingThread) { "sampler-fatal-failure: recording thread access" }
    }

    override fun createSampler(description: SamplerDescription): Long {
        checkAccess()
        calls.add("create")
        descriptions.add(description)
        creationFailure?.let { throw it }
        if (returnZero) return 0
        return nextSampler++.also { check(live.add(it)) }
    }

    override fun destroySampler(sampler: Long) {
        checkAccess()
        calls.add("destroy:$sampler")
        check(!failDestroy) { "sampler-fatal-failure: native destruction" }
        check(live.remove(sampler)) { "Sampler is not live" }
    }
}

object VulkanSamplerFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        println("sampler-fatal-start")
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val functions = FakeVulkanSamplerFunctions(Any())
        val sampler = createVulkanSampler(functions, defaultVulkanSamplerDescription())
        if (arguments.single() == "destroy") functions.failDestroy = true else functions.accessAllowed = false
        sampler.close()
        println("after-close")
    }
}
