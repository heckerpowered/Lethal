/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.shader

import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.ShaderInterfaceArtifact
import heckerpowered.render.shader.reflection.ShaderInterfaceDescription
import heckerpowered.render.vulkan.function.VulkanShaderModuleFunctions
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.ReadOnlyBufferException
import java.util.concurrent.TimeUnit
import kotlin.test.*

class VulkanShaderModuleTest {
    @Test
    fun acceptsIndependentFunctionGroupsOnOneBorrowedDevice() {
        val device = Any()
        val creator = FakeVulkanShaderModuleFunctions(device)
        val consumer = FakeVulkanShaderModuleFunctions(device)
        val module = createVulkanShaderModule(creator, shaderDescription()) as VulkanShaderModule
        module.requireDevice(consumer)
        consumer.accessAllowed = false
        assertFailsWith<IllegalStateException> { module.requireDevice(consumer) }
        assertEquals(listOf("create"), creator.calls)
        assertTrue(consumer.calls.isEmpty())
        module.close()
    }

    @Test
    fun matchesProducerFactsToTheCopiedCodeConsumedByCreation() {
        val words = shaderWords()
        val storage = ByteBuffer.allocate(words.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        storage.asIntBuffer().put(words)
        val description = ShaderModuleDescription(ShaderStage.Vertex, ShaderBinary.viewOf(storage, ShaderBinaryFormat.SpirV, "canonical"))
        val binary = description.code as ShaderBinary
        assertTrue(binary.storage.isReadOnly)
        val artifact = ShaderInterfaceArtifact(description.stage, description.entryPoint,
            ShaderInterfaceArtifact.fingerprint(binary.bytes), ShaderInterfaceDescription(emptyList(), emptyList(), emptyList()))
        val functions = FakeVulkanShaderModuleFunctions().apply { duringCreate = { storage.putInt(0, 0) } }
        val module = createVulkanShaderModule(functions, description, artifact) as VulkanShaderModule
        assertEquals(0, storage.getInt(0))
        assertFalse(artifact.matchesSpirV(binary.bytes))
        assertContentEquals(words, functions.receivedWords)
        assertNotSame(artifact, module.interfaceArtifact)
        assertEquals(artifact.spirVSha256, module.interfaceArtifact?.spirVSha256)
        module.close()
    }

    @Test
    fun rejectsMismatchedProducerCodeStageAndEntryBeforeNativeCreation() {
        val description = shaderDescription()
        val digest = ShaderInterfaceArtifact.fingerprint((description.code as ShaderBinary).bytes)
        val facts = ShaderInterfaceDescription(emptyList(), emptyList(), emptyList())
        val functions = FakeVulkanShaderModuleFunctions()
        for (artifact in listOf(
            ShaderInterfaceArtifact(ShaderStage.Fragment, "main", digest, facts),
            ShaderInterfaceArtifact(ShaderStage.Vertex, "other", digest, facts),
            ShaderInterfaceArtifact(ShaderStage.Vertex, "main", "0".repeat(64), facts),
        )) {
            assertFailsWith<IllegalArgumentException> { createVulkanShaderModule(functions, description, artifact) }
        }
        assertTrue(functions.calls.isEmpty())
    }

    @Test
    fun createsSelectedVertexAndFragmentWithoutRetainingCode() {
        for (stage in ShaderStage.entries) {
            val functions = FakeVulkanShaderModuleFunctions()
            val description = shaderDescription(shaderWords(stage), stage)
            val module = createVulkanShaderModule(functions, description) as VulkanShaderModule
            assertEquals(stage, module.stage)
            assertEquals("main", module.entryPoint)
            assertEquals(description.label, module.label)
            assertEquals(7L, module.handle)
            module.requireDevice(functions)
            module.close()
            module.close()
            assertEquals(listOf("create", "destroy:7"), functions.calls)
            assertFailsWith<IllegalStateException> { module.handle }
        }
    }

    @Test
    fun rejectsSourceAndOtherBinaryFormatsBeforeNativeCreation() {
        val functions = FakeVulkanShaderModuleFunctions()
        val source = ShaderModuleDescription(ShaderStage.Vertex, ShaderSource(ShaderLanguage.Glsl, "void main() {}", "source"))
        assertFailsWith<IllegalArgumentException> { createVulkanShaderModule(functions, source) }
        val code = ShaderBinary.viewOf(ByteBuffer.allocate(4), ShaderBinaryFormat.OpenGLGlsl, "opengl")
        assertFailsWith<IllegalArgumentException> { createVulkanShaderModule(functions, source.copy(code = code)) }
        assertTrue(functions.calls.isEmpty())
    }

    @Test
    fun rejectsMalformedHeadersAndByteSizesBeforeNativeCreation() {
        val valid = shaderWords()
        val cases = listOf(
            intArrayOf(), valid.copyOf(4), valid.copyOf().apply { this[0] = 0x03022307 },
            valid.copyOf().apply { this[1] = 0x00010100 }, valid.copyOf().apply { this[3] = 0 },
            valid.copyOf().apply { this[4] = 1 },
        )
        for (words in cases) reject(words)
        val functions = FakeVulkanShaderModuleFunctions()
        val bytes = ByteBuffer.allocate(21)
        val description = ShaderModuleDescription(ShaderStage.Vertex, ShaderBinary.viewOf(bytes, ShaderBinaryFormat.SpirV, "unaligned size"))
        assertFailsWith<IllegalArgumentException> { createVulkanShaderModule(functions, description) }
        assertTrue(functions.calls.isEmpty())
    }

    @Test
    fun rejectsTruncatedAndZeroLengthInstructions() {
        val valid = shaderWords()
        reject(valid.copyOf().apply { this[5] = 17 })
        reject(valid.copyOf().apply { this[5] = (65535 shl 16) or 17 })
        reject(valid.copyOf(valid.size - 1))
        reject(valid + intArrayOf((2 shl 16) or 0))
        reject(valid.copyOf().apply { this[5] = (1 shl 16) or 17 })
        reject(valid.copyOf().apply { this[7] = (2 shl 16) or 14 })
    }

    @Test
    fun rejectsProfilesOutsideTheCurrentCanonicalProducerContract() {
        val valid = shaderWords()
        reject(valid.copyOf().apply { this[6] = 10 })
        reject(valid + intArrayOf((2 shl 16) or 10, 0))
        reject(valid.copyOf().apply { this[8] = 1 })
        reject(valid.copyOf().apply { this[9] = 2 })
        reject(valid.copyOfRange(0, 5) + valid.copyOfRange(7, valid.size))
        reject(valid.copyOfRange(0, 7) + valid.copyOfRange(10, valid.size))
        reject(valid + intArrayOf((3 shl 16) or 14, 0, 1))
    }

    @Test
    fun rejectsMissingMismatchedOrUnterminatedEntryPoints() {
        val valid = shaderWords()
        reject(valid.copyOf(10))
        reject(valid.copyOf().apply { this[11] = 4 })
        reject(valid.copyOf().apply { this[12] = 0 })
        reject(valid.copyOf().apply { this[12] = this[3] })
        reject(valid.copyOf().apply { this[13] = 0x6e69616e })
        reject(valid.copyOf().apply { this[14] = 1 })
        reject(valid + valid.copyOfRange(10, valid.size))
        val functions = FakeVulkanShaderModuleFunctions()
        assertFailsWith<IllegalArgumentException> { createVulkanShaderModule(functions, shaderDescription(valid).copy(entryPoint = "other")) }
        assertTrue(functions.calls.isEmpty())
    }

    @Test
    fun copiesHeapAndUnalignedDirectSlicesWithCanonicalEndianness() {
        for (direct in listOf(false, true)) {
            val words = shaderWords()
            val bytes = if (direct) ByteBuffer.allocateDirect(words.size * 4 + 2) else ByteBuffer.allocate(words.size * 4 + 2)
            bytes.order(ByteOrder.LITTLE_ENDIAN)
            bytes.position(1)
            for (word in words) bytes.putInt(word)
            bytes.limit(bytes.position())
            bytes.position(1)
            bytes.order(ByteOrder.BIG_ENDIAN)
            val position = bytes.position()
            val limit = bytes.limit()
            val functions = FakeVulkanShaderModuleFunctions().apply { duringCreate = { bytes.putInt(1, 0) } }
            val description = ShaderModuleDescription(ShaderStage.Vertex, ShaderBinary.viewOf(bytes, ShaderBinaryFormat.SpirV, "slice"))
            val module = createVulkanShaderModule(functions, description)
            assertContentEquals(words, functions.receivedWords)
            assertEquals(position, bytes.position())
            assertEquals(limit, bytes.limit())
            assertEquals(ByteOrder.BIG_ENDIAN, bytes.order())
            module.close()
        }
    }

    @Test
    fun passesReadonlyWordSnapshotAndPreservesLargeCode() {
        val words = shaderWords() + IntArray(20000) { 1 shl 16 }
        val functions = FakeVulkanShaderModuleFunctions().apply {
            duringCreate = { assertFailsWith<ReadOnlyBufferException> { activeWords!!.put(0, 0) } }
        }
        val module = createVulkanShaderModule(functions, shaderDescription(words))
        assertContentEquals(words, functions.receivedWords)
        module.close()
    }

    @Test
    fun propagatesOperationalFailureAndNeverPublishesNullHandle() {
        val functions = FakeVulkanShaderModuleFunctions().apply { failureStage = "create" }
        assertSame(functions.failure, assertFailsWith<IllegalStateException> { createVulkanShaderModule(functions, shaderDescription()) })
        assertEquals(listOf("create"), functions.calls)
        functions.failureStage = ""
        functions.createdHandle = 0
        assertFailsWith<IllegalStateException> { createVulkanShaderModule(functions, shaderDescription()) }
        assertEquals(listOf("create", "create"), functions.calls)
    }

    @Test
    fun checksBindingAndThreadBeforeHandleAccessOrCreation() {
        val functions = FakeVulkanShaderModuleFunctions()
        val module = createVulkanShaderModule(functions, shaderDescription()) as VulkanShaderModule
        assertFailsWith<IllegalArgumentException> { module.requireDevice(FakeVulkanShaderModuleFunctions()) }
        functions.accessAllowed = false
        assertFailsWith<IllegalStateException> { module.handle }
        assertFailsWith<IllegalStateException> { createVulkanShaderModule(functions, shaderDescription()) }
        assertEquals(listOf("create"), functions.calls)
        functions.accessAllowed = true
        module.close()
    }

    @Test
    fun destructionFailuresHaltInsteadOfEscapingOrRunningShutdownHooks() {
        val classpath = listOf(VulkanShaderModuleFatalProbe::class.java, VulkanShaderModuleFunctions::class.java, ShaderModule::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (failureStage in listOf("destroy", "access")) {
            val output = File.createTempFile("vulkan-shader-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanShaderModuleFatalProbe::class.java.name, failureStage)
                    .redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, "Fatal probe timed out: $failureStage")
                assertEquals(1, process.exitValue(), output.readText())
                val log = output.readText()
                assertTrue("fatal-probe-start" in log, log)
                assertTrue("fatal-probe-failure" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("shutdown-hook" in log, log)
                if (failureStage == "access") assertFalse("destroy:7" in log, log)
            } finally {
                output.delete()
            }
        }
    }

    private fun reject(words: IntArray) {
        val functions = FakeVulkanShaderModuleFunctions()
        assertFailsWith<IllegalArgumentException> { createVulkanShaderModule(functions, shaderDescription(words)) }
        assertTrue(functions.calls.isEmpty())
    }
}

// Framing fixtures for CPU/fake checks only; these are not executable, semantically valid shaders.
private fun shaderWords(stage: ShaderStage = ShaderStage.Vertex): IntArray = intArrayOf(
    0x07230203, 0x00010000, 0, 20, 0,
    (2 shl 16) or 17, 1,
    (3 shl 16) or 14, 0, 1,
    (5 shl 16) or 15, if (stage == ShaderStage.Vertex) 0 else 4, 1, 0x6e69616d, 0,
)

private fun shaderDescription(words: IntArray = shaderWords(), stage: ShaderStage = ShaderStage.Vertex): ShaderModuleDescription {
    val bytes = ByteBuffer.allocate(words.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (word in words) bytes.putInt(word)
    bytes.position(0)
    return ShaderModuleDescription(stage, ShaderBinary.viewOf(bytes, ShaderBinaryFormat.SpirV, "canonical"))
}

private class FakeVulkanShaderModuleFunctions(
    override val deviceIdentity: Any = Any(),
) : VulkanShaderModuleFunctions {
    val calls = mutableListOf<String>()
    val failure = IllegalStateException("fatal-probe-failure")
    var failureStage = ""
    var accessAllowed = true
    var createdHandle = 7L
    var receivedWords = intArrayOf()
    var activeWords: IntBuffer? = null
    var duringCreate: () -> Unit = {}
    var printCalls = false

    override fun checkAccess() {
        check(accessAllowed) { "fatal-probe-failure: access" }
    }

    override fun createShaderModule(words: IntBuffer): Long {
        calls.add("create")
        if (failureStage == "create") throw failure
        activeWords = words
        try {
            duringCreate()
            receivedWords = IntArray(words.remaining())
            words.duplicate().get(receivedWords)
        } finally {
            activeWords = null
        }
        return createdHandle
    }

    override fun destroyShaderModule(module: Long) {
        calls.add("destroy:$module")
        if (printCalls) println("destroy:$module")
        if (failureStage == "destroy") throw failure
    }
}

object VulkanShaderModuleFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        println("fatal-probe-start")
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val functions = FakeVulkanShaderModuleFunctions().apply { printCalls = true }
        val module = createVulkanShaderModule(functions, shaderDescription())
        if (arguments.single() == "access") functions.accessAllowed = false
        else functions.failureStage = "destroy"
        module.close()
        println("after-close")
    }
}
