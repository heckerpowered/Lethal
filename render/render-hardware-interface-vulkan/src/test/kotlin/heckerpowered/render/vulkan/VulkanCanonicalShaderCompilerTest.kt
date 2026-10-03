/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.vulkan

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.pipeline.*
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.*
import heckerpowered.render.terminateOnFailure
import heckerpowered.render.withFailureCleanup
import heckerpowered.render.vulkan.command.RasterTestQueue
import heckerpowered.render.vulkan.command.VertexTestFixtures
import heckerpowered.render.vulkan.function.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.concurrent.TimeUnit
import kotlin.test.*

class VulkanCanonicalShaderCompilerTest {
    @Test
    fun compilationRegistersSameOutputFactsForTheOrdinaryModuleAndPipelineRouteWithoutGpuWork() {
        val queue = RasterTestQueue()
        var nativeModules = 0
        val shaders = object : VulkanShaderModuleFunctions by queue.pipelineFunctions.shaderFunctions {
            override fun createShaderModule(words: IntBuffer): Long { nativeModules++; return queue.pipelineFunctions.shaderFunctions.createShaderModule(words) }
        }
        val pipelines = object : VulkanPipelineFunctions by queue.pipelineFunctions { override val shaderFunctions = shaders }
        val raster = object : VulkanRasterFunctions by queue { override val pipelineFunctions = pipelines }
        val functions = object : VulkanTransferFunctions by queue { override val rasterFunctions = raster }
        val compiler = FakeVulkanCanonicalShaderCompiler()
        val device = testVulkanGraphicsDevice(functions, canonicalShaderCompiler = compiler)
        val consumer: GraphicsDevice = device
        val compiled = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { consumer.compileCanonicalShader(canonicalTestDescription(it), "surface/${it.name}.glsl") }
        assertTrue(queue.calls.isEmpty())
        assertEquals(2, compiler.calls.size)
        assertEquals(0, nativeModules)
        val modules = compiled.map { consumer.createShaderModule(it.module) }
        assertEquals(2, nativeModules)
        val stages = consumer.createShaderStages(ShaderStagesDescription(modules, "canonical consumer"))
        val layout = consumer.createPipelineLayout(VertexTestFixtures.layoutDescription())
        val pipeline = consumer.createRenderPipeline(RenderPipelineDescription("canonical pipeline", stages, layout, vertex = VertexTestFixtures.vertexState(), colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized))))
        assertTrue("createPass" in queue.calls)
        pipeline.close(); layout.close(); stages.close(); modules.asReversed().forEach { it.close() }; device.close()
        assertEquals(1, compiler.closeCount)
        assertFalse(queue.calls.any { it.startsWith("submit:") || it.startsWith("idle") })
    }

    @Test
    fun copiedCompilerOutputAndInputsCannotChangeTheRegisteredShader() {
        val queue = RasterTestQueue()
        val inputIncludes = mutableMapOf("shared.glsl" to "original")
        val sourceCode = VertexTestFixtures.description(ShaderStage.Vertex).code as ShaderBinary
        val storage = ByteBuffer.allocate(sourceCode.sizeInBytes).order(sourceCode.format.byteOrder).apply { put(sourceCode.bytes); flip() }
        val compiler = FakeVulkanCanonicalShaderCompiler { description ->
            ShaderCompilation(description.copy(code = ShaderBinary.viewOf(storage, ShaderBinaryFormat.SpirV, "selected")), VertexTestFixtures.artifact(description.stage).description)
        }
        val device = testVulkanGraphicsDevice(queue, canonicalShaderCompiler = compiler)
        val requested = canonicalTestDescription(ShaderStage.Vertex)
        val compiled = device.compileCanonicalShader(requested, "virtual/source.vert", inputIncludes)
        inputIncludes.clear()
        storage.putInt(0, 0)
        val captured = compiler.calls.single()
        assertEquals(requested, captured.first)
        assertEquals("virtual/source.vert", captured.second)
        assertEquals(mapOf("shared.glsl" to "original"), captured.third)
        assertEquals(0x07230203, (compiled.module.code as ShaderBinary).bytes.getInt(0))
        device.createShaderModule(compiled.module).close()
        assertEquals(listOf(0, 64), compiled.reflection.resources.single().members.map { it.offsetBytes })
        device.close()
    }

    @Test
    fun wrongCodeStageEntryAndMalformedBinaryAreRejectedBeforePublicationOrGpuWork() {
        val queue = RasterTestQueue()
        val good = FakeVulkanCanonicalShaderCompiler.output(canonicalTestDescription(ShaderStage.Vertex))
        val invalid = listOf(
            ShaderCompilation(good.module.copy(stage = ShaderStage.Fragment), good.reflection),
            ShaderCompilation(good.module.copy(entryPoint = "other"), good.reflection),
            ShaderCompilation(good.module.copy(code = ShaderSource(ShaderLanguage.Glsl, "void main(){}", "wrong")), good.reflection),
            ShaderCompilation(good.module.copy(code = ShaderBinary.copyOf(ByteBuffer.allocate(3), ShaderBinaryFormat.SpirV, "truncated")), good.reflection),
            ShaderCompilation(good.module.copy(code = VertexTestFixtures.description(ShaderStage.Fragment).code), good.reflection),
        )
        for (result in invalid) {
            val compiler = FakeVulkanCanonicalShaderCompiler { result }
            val device = testVulkanGraphicsDevice(queue, canonicalShaderCompiler = compiler)
            assertFailsWith<IllegalArgumentException> { device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex), "invalid", emptyMap()) }
            assertTrue(queue.calls.isEmpty())
            device.close()
        }
    }

    @Test
    fun invalidReflectionCannotPublishAnEntryAndReentrantClosureCannotReturnACompilation() {
        val queue = RasterTestQueue()
        val good = FakeVulkanCanonicalShaderCompiler.output(canonicalTestDescription(ShaderStage.Vertex))
        val block = good.reflection.resources.single()
        val malformed = ShaderInterfaceResource(block.kind, block.name, block.set, block.binding, block.arrayDimensions, block.sizeBytes, block.blockName,
            block.members.map { it.copy(sizeBytes = 1000) }, block.image)
        val compiler = FakeVulkanCanonicalShaderCompiler { ShaderCompilation(good.module, ShaderInterfaceDescription(good.reflection.inputs, good.reflection.outputs, listOf(malformed))) }
        val device = testVulkanGraphicsDevice(queue, canonicalShaderCompiler = compiler)
        assertFailsWith<IllegalArgumentException> { device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex), "invalid facts", emptyMap()) }
        assertTrue(queue.calls.isEmpty())
        val module = device.createShaderModule(good.module) as heckerpowered.render.vulkan.shader.VulkanShaderModule
        assertNull(module.interfaceArtifact)
        module.close()
        compiler.produce = { device.close(); good }
        assertFailsWith<IllegalStateException> { device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex), "reentrant close", emptyMap()) }
        assertEquals(1, compiler.closeCount)
        device.close()
        assertEquals(1, compiler.closeCount)
    }

    @Test
    fun conflictingSameBinaryFactsCannotReplacePreviouslyPublishedFacts() {
        val queue = RasterTestQueue()
        val compiler = FakeVulkanCanonicalShaderCompiler()
        val device = testVulkanGraphicsDevice(queue, canonicalShaderCompiler = compiler)
        val request = canonicalTestDescription(ShaderStage.Vertex)
        val compiled = device.compileCanonicalShader(request, "good", emptyMap())
        compiler.produce = { ShaderCompilation(compiled.module, ShaderInterfaceDescription(emptyList(), emptyList(), emptyList())) }
        assertFailsWith<IllegalArgumentException> { device.compileCanonicalShader(request, "conflict", emptyMap()) }
        assertTrue(queue.calls.isEmpty())
        val module = device.createShaderModule(compiled.module) as heckerpowered.render.vulkan.shader.VulkanShaderModule
        assertEquals(listOf(0), module.interfaceArtifact!!.description.inputs.map { it.location })
        module.close(); device.close()
    }

    @Test
    fun compilationFailurePreservesTheDeviceAndPropagatesTheOriginalExceptionOrError() {
        for (failure in listOf(IllegalStateException("CPU compile failed"), AssertionError("CPU compile Error"))) {
            val queue = RasterTestQueue()
            val compiler = FakeVulkanCanonicalShaderCompiler { throw failure }
            val device = testVulkanGraphicsDevice(queue, canonicalShaderCompiler = compiler)
            val captured = if (failure is AssertionError) assertFailsWith<AssertionError> { device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex), "failure", emptyMap()) }
            else assertFailsWith<IllegalStateException> { device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex), "failure", emptyMap()) }
            assertSame(failure, captured)
            assertTrue(queue.calls.isEmpty())
            assertEquals(0, compiler.closeCount)
            compiler.produce = FakeVulkanCanonicalShaderCompiler::output
            assertEquals(ShaderStage.Vertex, device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex), "retry", emptyMap()).module.stage)
            device.close()
        }
    }

    @Test
    fun nonCanonicalInputNeverInvokesTheCompiler() {
        val compiler = FakeVulkanCanonicalShaderCompiler()
        val device = testVulkanGraphicsDevice(RasterTestQueue(), canonicalShaderCompiler = compiler)
        assertFailsWith<IllegalArgumentException> { device.compileCanonicalShader(VertexTestFixtures.description(ShaderStage.Vertex), "binary", emptyMap()) }
        assertFailsWith<IllegalArgumentException> { device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex).copy(entryPoint = "other"), "entry", emptyMap()) }
        assertTrue(compiler.calls.isEmpty())
        device.close()
    }

    @Test
    fun compilerCloseRunsOnceAfterGpuCacheReleaseAndRejectsReentrantCreation() {
        val queue = RasterTestQueue()
        val samplers = heckerpowered.render.vulkan.resource.FakeVulkanSamplerFunctions(queue.pipelineFunctions.deviceIdentity)
        val compiler = FakeVulkanCanonicalShaderCompiler()
        val device = testVulkanGraphicsDevice(queue, samplerFunctions = samplers, canonicalShaderCompiler = compiler)
        device.resolveSampler(heckerpowered.render.vulkan.resource.defaultVulkanSamplerDescription())
        compiler.onClose = {
            assertTrue(samplers.live.isEmpty())
            assertFailsWith<IllegalStateException> { device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex), "during close", emptyMap()) }
        }
        device.close(); device.close()
        assertEquals(1, compiler.closeCount)
        assertFailsWith<IllegalStateException> { device.compileCanonicalShader(canonicalTestDescription(ShaderStage.Vertex), "closed", emptyMap()) }
        assertTrue(compiler.calls.isEmpty())
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun assemblyFailureReleasesCompilerAndSuccessfulConstructionTransfersOwnership() {
        val queue = RasterTestQueue()
        val compiler = FakeVulkanCanonicalShaderCompiler()
        val original = VertexTestFixtures.artifact(ShaderStage.Vertex)
        val conflict = ShaderInterfaceArtifact(original.stage, original.entryPoint, original.spirVSha256, ShaderInterfaceDescription(emptyList(), emptyList(), emptyList()))
        assertFailsWith<IllegalArgumentException> {
            withFailureCleanup({ VulkanGraphicsDevice(queue, compiler, listOf(original, conflict)) }) { compiler.close() }
        }
        assertEquals(1, compiler.closeCount)
        assertTrue(queue.calls.isEmpty())
        val next = FakeVulkanCanonicalShaderCompiler()
        val device = withFailureCleanup({ VulkanGraphicsDevice(queue, next) }) { next.close() }
        assertEquals(0, next.closeCount)
        device.close()
        assertEquals(1, next.closeCount)
    }

    @Test
    fun compilerDestructorFailureHaltsAtDeviceAndAssemblyFatalBoundaries() {
        val classpath = listOf(VulkanCanonicalCompilerFatalProbe::class.java, VulkanGraphicsDevice::class.java, CanonicalShaderCompiler::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (mode in listOf("device", "assembly")) {
            val output = File.createTempFile("vulkan-compiler-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanCanonicalCompilerFatalProbe::class.java.name, mode).redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, "Compiler fatal probe timed out: $mode")
                assertEquals(1, process.exitValue(), output.readText())
                val log = output.readText()
                assertTrue("compiler-fatal-start" in log, log)
                assertTrue("compiler-fatal-failure" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("shutdown-hook" in log, log)
            } finally { output.delete() }
        }
    }
}

internal fun testVulkanGraphicsDevice(nativeFunctions: VulkanTransferFunctions, shaderInterfaces: Collection<ShaderInterfaceArtifact> = emptyList(), memoryStack: MemoryStack = MemoryStack(), hostVisibleBuffers: Boolean = false, samplerFunctions: VulkanSamplerFunctions? = null, canonicalShaderCompiler: CanonicalShaderCompiler = FakeVulkanCanonicalShaderCompiler()): VulkanGraphicsDevice =
    VulkanGraphicsDevice(nativeFunctions, canonicalShaderCompiler, shaderInterfaces, memoryStack, hostVisibleBuffers, samplerFunctions)

internal fun canonicalTestDescription(stage: ShaderStage): ShaderModuleDescription =
    ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "#version 450\nvoid main(){}", "canonical CPU fake"))

internal class FakeVulkanCanonicalShaderCompiler(
    var produce: (ShaderModuleDescription) -> ShaderCompilation = { output(it) },
) : CanonicalShaderCompiler {
    val calls = mutableListOf<Triple<ShaderModuleDescription, String, Map<String, String>>>()
    var closeCount = 0
    var onClose: () -> Unit = {}
    override fun compile(description: ShaderModuleDescription, origin: String, includes: Map<String, String>): ShaderCompilation {
        check(closeCount == 0)
        calls.add(Triple(description, origin, includes))
        return produce(description)
    }
    override fun close() = terminateOnFailure { closeCount++; onClose() }
    companion object {
        fun output(description: ShaderModuleDescription): ShaderCompilation =
            ShaderCompilation(description.copy(code = VertexTestFixtures.description(description.stage).code), VertexTestFixtures.artifact(description.stage).description)
    }
}

object VulkanCanonicalCompilerFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        println("compiler-fatal-start")
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val compiler = FakeVulkanCanonicalShaderCompiler().apply { onClose = { error("compiler-fatal-failure") } }
        val queue = RasterTestQueue()
        if (arguments.single() == "assembly") {
            withFailureCleanup({ error("construction failed") }) { compiler.close() }
        } else {
            VulkanGraphicsDevice(queue, compiler).close()
        }
        println("after-close")
    }
}
