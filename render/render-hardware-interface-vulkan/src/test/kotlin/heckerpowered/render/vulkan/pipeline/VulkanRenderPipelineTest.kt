/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.vulkan.pipeline

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.pipeline.*
import heckerpowered.render.pipeline.color.*
import heckerpowered.render.pipeline.depthstencil.DepthStencilState
import heckerpowered.render.pipeline.multisample.*
import heckerpowered.render.pipeline.primitive.*
import heckerpowered.render.pipeline.rasterization.*
import heckerpowered.render.pipeline.vertex.*
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.shader.*
import heckerpowered.render.shader.binding.DescriptorSetLayout
import heckerpowered.render.shader.reflection.*
import heckerpowered.render.vulkan.command.VertexTestFixtures
import heckerpowered.render.vulkan.function.*
import heckerpowered.render.vulkan.shader.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.*

class VulkanRenderPipelineTest {
    @Test
    fun positionInputPreservesLaterSlotAndOriginalTwoStageMatrixColorReads() {
        val functions = PipelineTestFunctions().apply { vertexSupported = true }
        val stages = positionStages(functions)
        val layout = createVulkanPipelineLayout(functions, VertexTestFixtures.layoutDescription()) as VulkanPipelineLayout
        val buffers = VertexTestFixtures.vertexState(2, 32, 12).buffers.toMutableList()
        val attributes = buffers[2].attributes.toMutableList()
        buffers[2] = buffers[2].copy(attributes = attributes)
        functions.duringCreate = { buffers.clear(); attributes.clear() }
        val pipeline = createVulkanRenderPipeline(functions, request(stages, layout).copy(vertex = VertexState(buffers))) as VulkanRenderPipeline
        assertEquals(3, pipeline.description.vertex.buffers.size)
        assertEquals(VertexAttribute(0, VertexFormat.Float32x3, 12), pipeline.description.vertex.buffers[2].attributes.single())
        assertEquals(listOf(VulkanPushConstantRead(ShaderStage.Vertex, 0, 64), VulkanPushConstantRead(ShaderStage.Vertex, 64, 16),
            VulkanPushConstantRead(ShaderStage.Fragment, 0, 64), VulkanPushConstantRead(ShaderStage.Fragment, 64, 16)), pipeline.resourceInterface.pushConstantReads)
        pipeline.close(); layout.close(); release(stages)
    }

    @Test
    fun vertexFactsLayoutAndMissingNativeCapabilityRejectBeforePipelineAllocation() {
        val functions = PipelineTestFunctions()
        val stages = positionStages(functions)
        val layout = createVulkanPipelineLayout(functions, VertexTestFixtures.layoutDescription()) as VulkanPipelineLayout
        functions.calls.clear()
        assertFailsWith<UnsupportedOperationException> { createVulkanRenderPipeline(functions, request(stages, layout).copy(vertex = VertexTestFixtures.vertexState())) }
        assertTrue(functions.calls.isEmpty())
        functions.vertexSupported = true
        assertFailsWith<IllegalArgumentException> { createVulkanRenderPipeline(functions, request(stages, layout)) }
        for (state in listOf(
            VertexState(listOf(VertexBufferLayout(12, attributes = listOf(VertexAttribute(1, VertexFormat.Float32x3, 0))))),
            VertexState(listOf(VertexBufferLayout(8, attributes = listOf(VertexAttribute(0, VertexFormat.Float32x2, 0))))),
            VertexState(listOf(VertexBufferLayout(12, VertexStepMode.Instance, listOf(VertexAttribute(0, VertexFormat.Float32x3, 0))))),
        )) assertFailsWith<UnsupportedOperationException> { createVulkanRenderPipeline(functions, request(stages, layout).copy(vertex = state)) }
        assertTrue(functions.calls.isEmpty())
        layout.close(); release(stages)
    }

    @Test
    fun originalMatrixFactsRejectWrongStrideMajorExtentAndTypeBeforeNativeCreation() {
        for (mode in listOf("matrix-stride", "row-major", "member-size", "block-size", "array")) {
            val functions = PipelineTestFunctions().apply { vertexSupported = true }
            val original = VertexTestFixtures.artifact(ShaderStage.Vertex)
            val block = original.description.resources.single()
            val member = block.members.first()
            val changed = when (mode) {
                "matrix-stride" -> member.copy(matrixStrideBytes = 32)
                "row-major" -> member.copy(rowMajor = true)
                "member-size" -> member.copy(sizeBytes = 48)
                "array" -> member.copy(type = ShaderValueDescription(ShaderScalarKind.Float, 32, 4, 4, listOf(1)), arrayStrideBytes = 64)
                else -> member
            }
            val badBlock = ShaderInterfaceResource(block.kind, block.name, null, null, emptyList(), if (mode == "block-size") 79 else 80, block.blockName, listOf(changed, block.members.last()), null)
            val facts = ShaderInterfaceArtifact(original.stage, original.entryPoint, original.spirVSha256, ShaderInterfaceDescription(original.description.inputs, emptyList(), listOf(badBlock)))
            if (mode == "block-size") {
                assertFailsWith<IllegalArgumentException> { createVulkanShaderModule(functions.shaderFunctions, VertexTestFixtures.description(ShaderStage.Vertex), facts) }
                assertEquals(0, functions.shaderFunctions.created)
                assertTrue(functions.calls.isEmpty(), mode)
                continue
            }
            val stages = positionStages(functions, facts)
            val layout = createVulkanPipelineLayout(functions, VertexTestFixtures.layoutDescription()) as VulkanPipelineLayout
            functions.calls.clear()
            assertFailsWith<UnsupportedOperationException> { createVulkanRenderPipeline(functions, request(stages, layout).copy(vertex = VertexTestFixtures.vertexState())) }
            assertTrue(functions.calls.isEmpty(), mode)
            layout.close(); release(stages)
        }
    }

    @Test
    fun surfacePushRequiresCompleteEightyBytesExposedToBothStages() {
        val functions = PipelineTestFunctions().apply { vertexSupported = true }
        val stages = positionStages(functions)
        for (description in listOf(VertexTestFixtures.layoutDescription(setOf(ShaderStage.Fragment)), VertexTestFixtures.layoutDescription(sizeBytes = 64))) {
            val layout = createVulkanPipelineLayout(functions, description) as VulkanPipelineLayout
            functions.calls.clear()
            assertFailsWith<IllegalArgumentException> { createVulkanRenderPipeline(functions, request(stages, layout).copy(vertex = VertexTestFixtures.vertexState())) }
            assertTrue(functions.calls.isEmpty())
            layout.close()
        }
        release(stages)
    }

    @Test
    fun createsRealProfileAndBorrowsModulesStagesAndPublicLayout() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions)
        val layout = layout(functions)
        val pipeline = createVulkanRenderPipeline(functions, request(stages, layout)) as VulkanRenderPipeline
        assertEquals(31L, pipeline.requireHandle(functions.deviceIdentity))
        assertEquals(listOf(VulkanPushConstantRead(ShaderStage.Fragment, 0, 16)), pipeline.resourceInterface.pushConstantReads)
        assertEquals(listOf("layout", "support", "render-pass", "pipeline", "destroy-pass:21"), functions.calls)
        assertEquals(listOf(ShaderStage.Vertex, ShaderStage.Fragment), functions.consumedStages.map { it.stage })
        assertTrue(functions.consumedStages.all { it.module != 0L && it.entryPoint == "main" })
        pipeline.close()
        pipeline.close()
        assertEquals("destroy-pipeline:31", functions.calls.last())
        assertFailsWith<IllegalStateException> { pipeline.requireHandle(functions.deviceIdentity) }
        assertEquals(11L, layout.requireHandle(functions.deviceIdentity))
        stages.checkOpen()
        assertEquals(2, functions.shaderFunctions.created)
        assertEquals(0, functions.shaderFunctions.destroyed)
        layout.close()
        stages.close()
        stages.modules.forEach { it.close() }
        assertEquals(2, functions.shaderFunctions.destroyed)
    }

    @Test
    fun acceptsSeparateShaderAndPipelineGroupsForOneDevice() {
        val device = Any()
        val vertexGroup = PipelineTestShaderFunctions(device)
        val fragmentGroup = PipelineTestShaderFunctions(device)
        val pipelineGroup = PipelineTestFunctions(PipelineTestShaderFunctions(device))
        val layoutGroup = PipelineTestFunctions(PipelineTestShaderFunctions(device))
        val modules = listOf(module(vertexGroup, "triangle.vert"), module(fragmentGroup, "color.frag"))
        val stages = createVulkanShaderStages(vertexGroup, ShaderStagesDescription(modules, "same-device groups")) as VulkanShaderStages
        val layout = layout(layoutGroup)
        val pipeline = createVulkanRenderPipeline(pipelineGroup, request(stages, layout)) as VulkanRenderPipeline
        assertEquals(31L, pipeline.requireHandle(device))
        pipeline.close()
        layout.close()
        stages.close()
        modules.forEach { it.close() }
        assertEquals(1, vertexGroup.destroyed)
        assertEquals(1, fragmentGroup.destroyed)
    }

    @Test
    fun rejectsForeignStagesAndLayoutsBeforeNativePipelineWork() {
        val functions = PipelineTestFunctions()
        val foreign = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions)
        val foreignStages = stages(foreign.shaderFunctions)
        val ownLayout = layout(functions)
        val foreignLayout = layout(foreign)
        functions.calls.clear()
        assertFailsWith<IllegalArgumentException> { createVulkanRenderPipeline(functions, request(foreignStages, ownLayout)) }
        assertFailsWith<IllegalArgumentException> { createVulkanRenderPipeline(functions, request(stages, foreignLayout)) }
        assertTrue(functions.calls.isEmpty())
        ownLayout.close()
        foreignLayout.close()
        release(stages)
        release(foreignStages)
    }

    @Test
    fun rejectsForeignDuplicateAndClosedModulesWhenCombiningStages() {
        val group = PipelineTestShaderFunctions()
        val vertex = module(group, "triangle.vert")
        val foreign = module(PipelineTestShaderFunctions(), "color.frag")
        assertFailsWith<IllegalArgumentException> { createVulkanShaderStages(group, ShaderStagesDescription(listOf(vertex, foreign), "foreign")) }
        assertFailsWith<IllegalArgumentException> { createVulkanShaderStages(group, ShaderStagesDescription(listOf(vertex, vertex), "duplicate")) }
        assertFailsWith<IllegalArgumentException> { createVulkanShaderStages(group, ShaderStagesDescription(emptyList(), "empty")) }
        vertex.close()
        assertFailsWith<IllegalStateException> { createVulkanShaderStages(group, ShaderStagesDescription(listOf(vertex), "closed")) }
        foreign.close()
    }

    @Test
    fun snapshotsModuleAndPipelineRequestCollections() {
        val functions = PipelineTestFunctions()
        val modules = mutableListOf(module(functions.shaderFunctions, "triangle.vert"), module(functions.shaderFunctions, "color.frag"))
        val stages = createVulkanShaderStages(functions.shaderFunctions, ShaderStagesDescription(modules, "snapshot")) as VulkanShaderStages
        modules.clear()
        assertEquals(2, stages.modules.size)
        val layout = layout(functions)
        val targets = mutableListOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized))
        val buffers = mutableListOf<VertexBufferLayout>()
        functions.duringCreate = {
            targets.clear()
            buffers.add(VertexBufferLayout(0, attributes = emptyList()))
        }
        val description = request(stages, layout).copy(colorTargets = targets, vertex = VertexState(buffers))
        val pipeline = createVulkanRenderPipeline(functions, description) as VulkanRenderPipeline
        assertEquals(1, pipeline.description.colorTargets.size)
        assertTrue(pipeline.description.vertex.buffers.isEmpty())
        assertFailsWith<UnsupportedOperationException> { (pipeline.description.colorTargets as MutableList<ColorTargetState>).clear() }
        assertFailsWith<UnsupportedOperationException> { (pipeline.description.vertex.buffers as MutableList<VertexBufferLayout>).add(VertexBufferLayout(0, attributes = emptyList())) }
        pipeline.close()
        layout.close()
        release(stages)
    }

    @Test
    fun rejectsUnsupportedFixedStateBeforeNativeCreation() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions)
        val layout = layout(functions)
        val accepted = request(stages, layout)
        functions.calls.clear()
        val cases = listOf(
            accepted.copy(colorTargets = emptyList()),
            accepted.copy(colorTargets = accepted.colorTargets + accepted.colorTargets),
            accepted.copy(colorTargets = listOf(ColorTargetState(TextureFormat.Rgba16Float))),
            accepted.copy(colorTargets = listOf(accepted.colorTargets.single().copy(blend = BlendState.StraightAlpha))),
            accepted.copy(colorTargets = listOf(accepted.colorTargets.single().copy(writeMask = ColorWriteMask.Rgb))),
            accepted.copy(vertex = VertexState(listOf(VertexBufferLayout(0, attributes = emptyList())))),
            accepted.copy(primitive = PrimitiveState(PrimitiveTopology.TriangleStrip)),
            accepted.copy(primitive = PrimitiveState(PrimitiveTopology.TriangleStrip, true)),
            accepted.copy(multisample = MultisampleState(SampleCount.Two)),
            accepted.copy(multisample = MultisampleState(alphaToCoverageEnabled = true)),
            accepted.copy(depthStencil = DepthStencilState(TextureFormat.Depth32Float)),
            accepted.copy(rasterization = RasterizationState(cullMode = CullMode.Back)),
            accepted.copy(rasterization = RasterizationState(polygonMode = PolygonMode.Line)),
        )
        for (description in cases) assertFailsWith<UnsupportedOperationException> { createVulkanRenderPipeline(functions, description) }
        assertTrue(functions.calls.isEmpty())
        layout.close()
        release(stages)
    }

    @Test
    fun usesDeviceColorTargetSupportAndPushCapacity() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions)
        val layout = layout(functions)
        functions.calls.clear()
        functions.targetSupported = false
        assertFailsWith<UnsupportedOperationException> { createVulkanRenderPipeline(functions, request(stages, layout)) }
        assertEquals(listOf("support"), functions.calls)
        functions.targetSupported = true
        functions.maxPushConstantsSize = 8
        functions.calls.clear()
        assertFailsWith<UnsupportedOperationException> { createVulkanRenderPipeline(functions, request(stages, layout)) }
        assertFailsWith<UnsupportedOperationException> { layout(functions) }
        assertTrue(functions.calls.isEmpty())
        functions.maxPushConstantsSize = 128
        layout.close()
        release(stages)
    }

    @Test
    fun requiresExactlyVertexAndFragmentAndTrustedSelectedFacts() {
        val functions = PipelineTestFunctions()
        val vertex = module(functions.shaderFunctions, "triangle.vert")
        val vertexOnly = createVulkanShaderStages(functions.shaderFunctions, ShaderStagesDescription(listOf(vertex), "vertex only")) as VulkanShaderStages
        assertFailsWith<IllegalArgumentException> { createVulkanRenderPipeline(functions, request(vertexOnly, null)) }
        val fragmentDescription = PipelineTestFixtures.description("color.frag")
        val withoutFacts = createVulkanShaderModule(functions.shaderFunctions, fragmentDescription) as VulkanShaderModule
        val missingFacts = createVulkanShaderStages(functions.shaderFunctions, ShaderStagesDescription(listOf(vertex, withoutFacts), "unreflected")) as VulkanShaderStages
        val layout = layout(functions)
        functions.calls.clear()
        assertFailsWith<UnsupportedOperationException> { createVulkanRenderPipeline(functions, request(missingFacts, layout)) }
        assertTrue(functions.calls.isEmpty())
        layout.close()
        missingFacts.close()
        vertexOnly.close()
        vertex.close()
        withoutFacts.close()
    }

    @Test
    fun rejectsActuallyReflectedDescriptorsInsteadOfInferringAnEmptyLayout() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions, "descriptor.frag")
        val facts = stages.modules.single { it.stage == ShaderStage.Fragment }.interfaceArtifact!!.description
        assertEquals(0, facts.resources.single().set)
        assertEquals(0, facts.resources.single().binding)
        assertFailsWith<UnsupportedOperationException> { createVulkanRenderPipeline(functions, request(stages, null)) }
        assertTrue(functions.calls.isEmpty())
        assertFailsWith<UnsupportedOperationException> {
            createVulkanPipelineLayout(functions, PipelineLayoutDescription(listOf(DescriptorSetLayout.Empty), label = "reserved descriptor set"))
        }
        assertTrue(functions.calls.isEmpty())
        release(stages)
    }

    @Test
    fun rejectsMissingWrongStageAndShortPushLayoutsButAllowsUnusedReservedBytes() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions)
        assertFailsWith<IllegalArgumentException> { createVulkanRenderPipeline(functions, request(stages, null)) }
        for (description in listOf(
            PipelineLayoutDescription(label = "empty"),
            pushDescription(ShaderStage.Vertex, 0, 16),
            pushDescription(ShaderStage.Fragment, 0, 8),
            pushDescription(ShaderStage.Fragment, 16, 16),
        )) {
            val layout = createVulkanPipelineLayout(functions, description) as VulkanPipelineLayout
            functions.calls.clear()
            assertFailsWith<IllegalArgumentException> { createVulkanRenderPipeline(functions, request(stages, layout)) }
            assertTrue(functions.calls.isEmpty())
            layout.close()
        }
        val layout = createVulkanPipelineLayout(functions, pushDescription(ShaderStage.Fragment, 0, 32)) as VulkanPipelineLayout
        val pipeline = createVulkanRenderPipeline(functions, request(stages, layout)) as VulkanRenderPipeline
        assertEquals(listOf(VulkanPushConstantRead(ShaderStage.Fragment, 0, 16)), pipeline.resourceInterface.pushConstantReads)
        pipeline.close()
        layout.close()
        release(stages)
    }

    @Test
    fun checksRealReflectedAbsolutePushOffset() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions, "offset.frag")
        val wrong = layout(functions)
        functions.calls.clear()
        assertFailsWith<IllegalArgumentException> { createVulkanRenderPipeline(functions, request(stages, wrong)) }
        assertTrue(functions.calls.isEmpty())
        wrong.close()
        val shifted = createVulkanPipelineLayout(functions, pushDescription(ShaderStage.Fragment, 16, 16)) as VulkanPipelineLayout
        val pipeline = createVulkanRenderPipeline(functions, request(stages, shifted)) as VulkanRenderPipeline
        assertEquals(listOf(VulkanPushConstantRead(ShaderStage.Fragment, 16, 16)), pipeline.resourceInterface.pushConstantReads)
        pipeline.close()
        shifted.close()
        release(stages)
    }

    @Test
    fun ownsOnlyPrivateEmptyLayoutForResourceFreeShaders() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions, "constant.frag")
        val pipeline = createVulkanRenderPipeline(functions, request(stages, null)) as VulkanRenderPipeline
        assertTrue(pipeline.resourceInterface.pushConstantReads.isEmpty())
        assertNull(pipeline.description.layout)
        pipeline.close()
        assertEquals(listOf("support", "layout", "render-pass", "pipeline", "destroy-pass:21", "destroy-pipeline:31", "destroy-layout:11"), functions.calls)
        release(stages)
    }

    @Test
    fun checksClosedBorrowedInputsAtCreationAndHandleAccess() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions)
        val layout = layout(functions)
        val pipeline = createVulkanRenderPipeline(functions, request(stages, layout)) as VulkanRenderPipeline
        layout.close()
        assertFailsWith<IllegalStateException> { pipeline.requireHandle(functions.deviceIdentity) }
        functions.calls.clear()
        assertFailsWith<IllegalStateException> { createVulkanRenderPipeline(functions, request(stages, layout)) }
        assertTrue(functions.calls.isEmpty())
        pipeline.close()
        stages.modules.first().close()
        assertFailsWith<IllegalStateException> { stages.checkOpen() }
        assertFailsWith<IllegalStateException> { createVulkanRenderPipeline(functions, request(stages, null)) }
        release(stages)
    }

    @Test
    fun cleansTemporaryAndPrivateObjectsAfterOperationalCreationFailure() {
        for (failure in listOf("render-pass", "pipeline")) {
            val functions = PipelineTestFunctions()
            val stages = stages(functions.shaderFunctions, "constant.frag")
            functions.failureStage = failure
            assertFailsWith<IllegalStateException> { createVulkanRenderPipeline(functions, request(stages, null)) }
            val expected = if (failure == "render-pass") listOf("support", "layout", "render-pass", "destroy-layout:11")
                else listOf("support", "layout", "render-pass", "pipeline", "destroy-pass:21", "destroy-layout:11")
            assertEquals(expected, functions.calls)
            release(stages)
        }
    }

    @Test
    fun neverPublishesZeroHandlesAndCleansEarlierOwnedObjects() {
        for (zero in listOf("layout", "render-pass", "pipeline")) {
            val functions = PipelineTestFunctions()
            val stages = stages(functions.shaderFunctions, "constant.frag")
            functions.zeroStage = zero
            assertFailsWith<IllegalStateException> { createVulkanRenderPipeline(functions, request(stages, null)) }
            if (zero != "layout") assertEquals("destroy-layout:11", functions.calls.last())
            if (zero == "pipeline") assertTrue("destroy-pass:21" in functions.calls)
            assertFalse(functions.calls.any { it.endsWith(":0") })
            release(stages)
        }
    }

    @Test
    fun checksPermittedThreadBeforeOperationalCreation() {
        val functions = PipelineTestFunctions()
        val stages = stages(functions.shaderFunctions)
        functions.shaderFunctions.accessAllowed = false
        assertFailsWith<IllegalStateException> { createVulkanPipelineLayout(functions, pushDescription()) }
        assertFailsWith<IllegalStateException> { createVulkanRenderPipeline(functions, request(stages, null)) }
        assertTrue(functions.calls.isEmpty())
        functions.shaderFunctions.accessAllowed = true
        release(stages)
    }

    @Test
    fun destructorAndFailureCleanupHaltWithoutWaitsOrShutdownHooks() {
        val classpath = listOf(VulkanPipelineFatalProbe::class.java, VulkanPipelineFunctions::class.java, ShaderModule::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
        for (scenario in listOf("layout-destroy", "layout-access", "pipeline-destroy", "private-layout-destroy", "temporary-pass-destroy", "failure-pass-destroy", "failure-layout-destroy", "stages-access")) {
            val output = File.createTempFile("vulkan-pipeline-fatal-", ".log")
            try {
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", classpath, VulkanPipelineFatalProbe::class.java.name, scenario)
                    .redirectErrorStream(true).redirectOutput(output).start()
                val completed = process.waitFor(20, TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                assertTrue(completed, "Fatal probe timed out: $scenario")
                assertEquals(1, process.exitValue(), output.readText())
                val log = output.readText()
                assertTrue("fatal-probe-start" in log, log)
                assertTrue("fatal-probe-failure" in log, log)
                assertFalse("after-close" in log, log)
                assertFalse("operation-caught" in log, log)
                assertFalse("shutdown-hook" in log, log)
                if (scenario == "private-layout-destroy") {
                    assertTrue("destroy-pipeline:31" in log, log)
                    assertTrue("destroy-layout:11" in log, log)
                    assertTrue(log.indexOf("destroy-pipeline:31") < log.indexOf("destroy-layout:11"), log)
                }
            } finally {
                output.delete()
            }
        }
    }
}

private fun pushDescription(stage: ShaderStage = ShaderStage.Fragment, offset: Int = 0, size: Int = 16): PipelineLayoutDescription =
    PipelineLayoutDescription(pushConstants = PushConstantLayout(listOf(PushConstantRange(setOf(stage), offset, size))), label = "push layout")

private fun layout(functions: PipelineTestFunctions): VulkanPipelineLayout = createVulkanPipelineLayout(functions, pushDescription()) as VulkanPipelineLayout

private fun module(functions: PipelineTestShaderFunctions, name: String): VulkanShaderModule =
    createVulkanShaderModule(functions, PipelineTestFixtures.description(name), PipelineTestFixtures.artifact(name)) as VulkanShaderModule

private fun stages(functions: PipelineTestShaderFunctions, fragment: String = "color.frag"): VulkanShaderStages =
    createVulkanShaderStages(functions, ShaderStagesDescription(listOf(module(functions, "triangle.vert"), module(functions, fragment)), "procedural stages")) as VulkanShaderStages

private fun release(stages: VulkanShaderStages) {
    stages.close()
    stages.modules.forEach { it.close() }
}

private fun request(stages: ShaderStages, layout: PipelineLayout?): RenderPipelineDescription =
    RenderPipelineDescription("procedural pipeline", stages, layout, colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)))

private class PipelineTestShaderFunctions(
    override val deviceIdentity: Any = Any(),
) : VulkanShaderModuleFunctions {
    var accessAllowed = true
    var created = 0
    var destroyed = 0

    override fun checkAccess() {
        check(accessAllowed) { "fatal-probe-failure: thread access" }
    }

    override fun createShaderModule(words: IntBuffer): Long {
        check(words.isReadOnly && words.hasRemaining())
        return (++created).toLong()
    }

    override fun destroyShaderModule(module: Long) {
        destroyed++
    }
}

private class PipelineTestFunctions(
    override val shaderFunctions: PipelineTestShaderFunctions = PipelineTestShaderFunctions(),
) : VulkanPipelineFunctions {
    override var maxPushConstantsSize = 128
    var targetSupported = true
    var vertexSupported = false

    override fun validateVertexState(state: VertexState) {
        if (!vertexSupported) super.validateVertexState(state)
    }

    val calls = mutableListOf<String>()
    var failureStage = ""
    var cleanupFailure = ""
    var zeroStage = ""
    var printCalls = false
    var duringCreate: () -> Unit = {}
    var consumedStages = emptyList<VulkanPipelineShaderStage>()

    override fun supportsColorTarget(format: TextureFormat, sampleCount: SampleCount): Boolean {
        hit("support")
        check(format == TextureFormat.Rgba8UnsignedNormalized && sampleCount == SampleCount.One)
        return targetSupported
    }

    override fun createPipelineLayout(description: PipelineLayoutDescription): Long {
        hit("layout")
        return if (zeroStage == "layout") 0 else 11
    }

    override fun destroyPipelineLayout(layout: Long) = hit("destroy-layout:$layout")

    override fun createRenderPass(format: TextureFormat, sampleCount: SampleCount): Long {
        hit("render-pass")
        return if (zeroStage == "render-pass") 0 else 21
    }

    override fun destroyRenderPass(renderPass: Long) = hit("destroy-pass:$renderPass")

    override fun createGraphicsPipeline(description: RenderPipelineDescription, stages: List<VulkanPipelineShaderStage>, layout: Long, renderPass: Long): Long {
        hit("pipeline")
        consumedStages = stages.toList()
        duringCreate()
        return if (zeroStage == "pipeline") 0 else 31
    }

    override fun destroyPipeline(pipeline: Long) = hit("destroy-pipeline:$pipeline")

    private fun hit(event: String) {
        calls.add(event)
        if (printCalls) println(event)
        if (event == failureStage || event.substringBefore(':') == cleanupFailure) error("fatal-probe-failure: $event")
    }
}

object VulkanPipelineFatalProbe {
    @JvmStatic
    fun main(arguments: Array<String>) {
        Runtime.getRuntime().addShutdownHook(Thread { println("shutdown-hook") })
        val scenario = arguments.single()
        val functions = PipelineTestFunctions().apply { printCalls = true }
        val stages = stages(functions.shaderFunctions, if (scenario.startsWith("layout-") || scenario == "stages-access") "color.frag" else "constant.frag")
        println("fatal-probe-start")
        when (scenario) {
            "layout-destroy", "layout-access" -> {
                val layout = layout(functions)
                if (scenario == "layout-access") functions.shaderFunctions.accessAllowed = false else functions.cleanupFailure = "destroy-layout"
                layout.close()
            }
            "pipeline-destroy", "private-layout-destroy" -> {
                val pipeline = createVulkanRenderPipeline(functions, request(stages, null))
                functions.cleanupFailure = if (scenario == "pipeline-destroy") "destroy-pipeline" else "destroy-layout"
                pipeline.close()
            }
            "temporary-pass-destroy", "failure-pass-destroy", "failure-layout-destroy" -> {
                functions.cleanupFailure = if (scenario == "failure-layout-destroy") "destroy-layout" else "destroy-pass"
                if (scenario != "temporary-pass-destroy") functions.failureStage = "pipeline"
                try {
                    createVulkanRenderPipeline(functions, request(stages, null))
                } catch (failure: Exception) {
                    println("operation-caught")
                }
            }
            "stages-access" -> {
                functions.shaderFunctions.accessAllowed = false
                stages.close()
            }
            else -> error("Unknown fatal probe")
        }
        println("after-close")
    }
}

/** Actual shaderc/SPIRV-Cross 3.4.1 output: Vulkan1.0/SPIR-V1.0, no optimization, RHIF v1. */
private object PipelineTestFixtures {
    private val encoded: Map<String, Pair<String, String>> = mapOf(
        "triangle.vert" to ("AwIjBwAAAQALAA0AKQAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABwAAAAAABAAAAG1haW4AAAAADQAAABsAAAADAAMAAgAAAMIBAAAEAAoAR0xfR09PR0xFX2NwcF9zdHlsZV9saW5lX2RpcmVjdGl2ZQAABAAIAEdMX0dPT0dMRV9pbmNsdWRlX2RpcmVjdGl2ZQAFAAQABAAAAG1haW4AAAAABQAGAAsAAABnbF9QZXJWZXJ0ZXgAAAAABgAGAAsAAAAAAAAAZ2xfUG9zaXRpb24ABgAHAAsAAAABAAAAZ2xfUG9pbnRTaXplAAAAAAYABwALAAAAAgAAAGdsX0NsaXBEaXN0YW5jZQAGAAcACwAAAAMAAABnbF9DdWxsRGlzdGFuY2UABQADAA0AAAAAAAAABQAGABsAAABnbF9WZXJ0ZXhJbmRleAAABQAFAB4AAABpbmRleGFibGUAAABHAAMACwAAAAIAAABIAAUACwAAAAAAAAALAAAAAAAAAEgABQALAAAAAQAAAAsAAAABAAAASAAFAAsAAAACAAAACwAAAAMAAABIAAUACwAAAAMAAAALAAAABAAAAEcABAAbAAAACwAAACoAAAATAAIAAgAAACEAAwADAAAAAgAAABYAAwAGAAAAIAAAABcABAAHAAAABgAAAAQAAAAVAAQACAAAACAAAAAAAAAAKwAEAAgAAAAJAAAAAQAAABwABAAKAAAABgAAAAkAAAAeAAYACwAAAAcAAAAGAAAACgAAAAoAAAAgAAQADAAAAAMAAAALAAAAOwAEAAwAAAANAAAAAwAAABUABAAOAAAAIAAAAAEAAAArAAQADgAAAA8AAAAAAAAAFwAEABAAAAAGAAAAAgAAACsABAAIAAAAEQAAAAMAAAAcAAQAEgAAABAAAAARAAAAKwAEAAYAAAATAAAAAABAvywABQAQAAAAFAAAABMAAAATAAAAKwAEAAYAAAAVAAAAAABAPywABQAQAAAAFgAAABUAAAATAAAAKwAEAAYAAAAXAAAAAAAwPywABQAQAAAAGAAAABMAAAAXAAAALAAGABIAAAAZAAAAFAAAABYAAAAYAAAAIAAEABoAAAABAAAADgAAADsABAAaAAAAGwAAAAEAAAAgAAQAHQAAAAcAAAASAAAAIAAEAB8AAAAHAAAAEAAAACsABAAGAAAAIgAAAAAAAAArAAQABgAAACMAAAAAAIA/IAAEACcAAAADAAAABwAAADYABQACAAAABAAAAAAAAAADAAAA+AACAAUAAAA7AAQAHQAAAB4AAAAHAAAAPQAEAA4AAAAcAAAAGwAAAD4AAwAeAAAAGQAAAEEABQAfAAAAIAAAAB4AAAAcAAAAPQAEABAAAAAhAAAAIAAAAFEABQAGAAAAJAAAACEAAAAAAAAAUQAFAAYAAAAlAAAAIQAAAAEAAABQAAcABwAAACYAAAAkAAAAJQAAACIAAAAjAAAAQQAFACcAAAAoAAAADQAAAA8AAAA+AAMAKAAAACYAAAD9AAEAOAABAA==" to "UkhJRgAAAAEAAAABAAAABG1haW59BurDBZzuUWv8CzoQmEfg383G86AqFb95HfE3umXkTAAAAAAAAAAAAAAAAA=="),
        "color.frag" to ("AwIjBwAAAQALAA0AEgAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABgAEAAAABAAAAG1haW4AAAAACQAAABAAAwAEAAAABwAAAAMAAwACAAAAwgEAAAQACgBHTF9HT09HTEVfY3BwX3N0eWxlX2xpbmVfZGlyZWN0aXZlAAAEAAgAR0xfR09PR0xFX2luY2x1ZGVfZGlyZWN0aXZlAAUABAAEAAAAbWFpbgAAAAAFAAQACQAAAHJlc3VsdAAABQAFAAoAAABQYXJhbWV0ZXJzAAAGAAUACgAAAAAAAABjb2xvcgAAAAUABQAMAAAAcGFyYW1ldGVycwAARwAEAAkAAAAeAAAAAAAAAEcAAwAKAAAAAgAAAEgABQAKAAAAAAAAACMAAAAAAAAAEwACAAIAAAAhAAMAAwAAAAIAAAAWAAMABgAAACAAAAAXAAQABwAAAAYAAAAEAAAAIAAEAAgAAAADAAAABwAAADsABAAIAAAACQAAAAMAAAAeAAMACgAAAAcAAAAgAAQACwAAAAkAAAAKAAAAOwAEAAsAAAAMAAAACQAAABUABAANAAAAIAAAAAEAAAArAAQADQAAAA4AAAAAAAAAIAAEAA8AAAAJAAAABwAAADYABQACAAAABAAAAAAAAAADAAAA+AACAAUAAABBAAUADwAAABAAAAAMAAAADgAAAD0ABAAHAAAAEQAAABAAAAA+AAMACQAAABEAAAD9AAEAOAABAA==" to "UkhJRgAAAAEAAAACAAAABG1haW4F2Mf2nVkkNdxK9M67dymx8nSZm1Al5V7gOsSoy4JJEAAAAAAAAAABAAAABnJlc3VsdAAAAAAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAEAAAADAAAACnBhcmFtZXRlcnP//////////wAAAAAAAAAAAAAAEAEAAAAKUGFyYW1ldGVycwAAAAEAAAAFY29sb3IAAAAAAAAAAAAAABAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAAAAAAAAAA="),
        "constant.frag" to ("AwIjBwAAAQALAA0ADQAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABgAEAAAABAAAAG1haW4AAAAACQAAABAAAwAEAAAABwAAAAMAAwACAAAAwgEAAAQACgBHTF9HT09HTEVfY3BwX3N0eWxlX2xpbmVfZGlyZWN0aXZlAAAEAAgAR0xfR09PR0xFX2luY2x1ZGVfZGlyZWN0aXZlAAUABAAEAAAAbWFpbgAAAAAFAAQACQAAAHJlc3VsdAAARwAEAAkAAAAeAAAAAAAAABMAAgACAAAAIQADAAMAAAACAAAAFgADAAYAAAAgAAAAFwAEAAcAAAAGAAAABAAAACAABAAIAAAAAwAAAAcAAAA7AAQACAAAAAkAAAADAAAAKwAEAAYAAAAKAAAAAACAPysABAAGAAAACwAAAAAAAAAsAAcABwAAAAwAAAAKAAAACwAAAAsAAAAKAAAANgAFAAIAAAAEAAAAAAAAAAMAAAD4AAIABQAAAD4AAwAJAAAADAAAAP0AAQA4AAEA" to "UkhJRgAAAAEAAAACAAAABG1haW53HqaFRNoH4UpuzjZk0aqjtMuqdjjxsbjW+5GDtjCnswAAAAAAAAABAAAABnJlc3VsdAAAAAAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAA="),
        "descriptor.frag" to ("AwIjBwAAAQALAA0AEwAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABgAEAAAABAAAAG1haW4AAAAACQAAABAAAwAEAAAABwAAAAMAAwACAAAAwgEAAAQACgBHTF9HT09HTEVfY3BwX3N0eWxlX2xpbmVfZGlyZWN0aXZlAAAEAAgAR0xfR09PR0xFX2luY2x1ZGVfZGlyZWN0aXZlAAUABAAEAAAAbWFpbgAAAAAFAAQACQAAAHJlc3VsdAAABQAEAA0AAABpbWFnZQAAAEcABAAJAAAAHgAAAAAAAABHAAQADQAAACEAAAAAAAAARwAEAA0AAAAiAAAAAAAAABMAAgACAAAAIQADAAMAAAACAAAAFgADAAYAAAAgAAAAFwAEAAcAAAAGAAAABAAAACAABAAIAAAAAwAAAAcAAAA7AAQACAAAAAkAAAADAAAAGQAJAAoAAAAGAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAGwADAAsAAAAKAAAAIAAEAAwAAAAAAAAACwAAADsABAAMAAAADQAAAAAAAAAXAAQADwAAAAYAAAACAAAAKwAEAAYAAAAQAAAAAAAAPywABQAPAAAAEQAAABAAAAAQAAAANgAFAAIAAAAEAAAAAAAAAAMAAAD4AAIABQAAAD0ABAALAAAADgAAAA0AAABXAAUABwAAABIAAAAOAAAAEQAAAD4AAwAJAAAAEgAAAP0AAQA4AAEA" to "UkhJRgAAAAEAAAACAAAABG1haW4QI874lFISiWkG+x5u/F0KVMXMvJz3nGEnG9EsTT71pQAAAAAAAAABAAAABnJlc3VsdAAAAAAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAEAAAAEAAAABWltYWdlAAAAAAAAAAAAAAAA//////////8AAAAAAAEAAAACAAAAAAAABAAAACAAAAABAAAAAQAAAAA="),
        "offset.frag" to ("AwIjBwAAAQALAA0AEgAAAAAAAAARAAIAAQAAAAsABgABAAAAR0xTTC5zdGQuNDUwAAAAAA4AAwAAAAAAAQAAAA8ABgAEAAAABAAAAG1haW4AAAAACQAAABAAAwAEAAAABwAAAAMAAwACAAAAwgEAAAQACgBHTF9HT09HTEVfY3BwX3N0eWxlX2xpbmVfZGlyZWN0aXZlAAAEAAgAR0xfR09PR0xFX2luY2x1ZGVfZGlyZWN0aXZlAAUABAAEAAAAbWFpbgAAAAAFAAQACQAAAHJlc3VsdAAABQAFAAoAAABQYXJhbWV0ZXJzAAAGAAUACgAAAAAAAABjb2xvcgAAAAUABQAMAAAAcGFyYW1ldGVycwAARwAEAAkAAAAeAAAAAAAAAEcAAwAKAAAAAgAAAEgABQAKAAAAAAAAACMAAAAQAAAAEwACAAIAAAAhAAMAAwAAAAIAAAAWAAMABgAAACAAAAAXAAQABwAAAAYAAAAEAAAAIAAEAAgAAAADAAAABwAAADsABAAIAAAACQAAAAMAAAAeAAMACgAAAAcAAAAgAAQACwAAAAkAAAAKAAAAOwAEAAsAAAAMAAAACQAAABUABAANAAAAIAAAAAEAAAArAAQADQAAAA4AAAAAAAAAIAAEAA8AAAAJAAAABwAAADYABQACAAAABAAAAAAAAAADAAAA+AACAAUAAABBAAUADwAAABAAAAAMAAAADgAAAD0ABAAHAAAAEQAAABAAAAA+AAMACQAAABEAAAD9AAEAOAABAA==" to "UkhJRgAAAAEAAAACAAAABG1haW45LEvQlbXBGMnhIZnkChEytcR7Q1Jrsoq6SWN2L3CjogAAAAAAAAABAAAABnJlc3VsdAAAAAAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAEAAAADAAAACnBhcmFtZXRlcnP//////////wAAAAAAAAAAAAAAIAEAAAAKUGFyYW1ldGVycwAAAAEAAAAFY29sb3IAAAAQAAAAAAAAABAAAAAEAAAAIAAAAAQAAAABAAAAAAAAAAAAAAAAAAA="),
    )

    fun description(name: String): ShaderModuleDescription {
        val code = Base64.getDecoder().decode(encoded.getValue(name).first)
        return ShaderModuleDescription(if (name.endsWith(".vert")) ShaderStage.Vertex else ShaderStage.Fragment,
            ShaderBinary.copyOf(ByteBuffer.wrap(code), ShaderBinaryFormat.SpirV, name))
    }

    fun artifact(name: String): ShaderInterfaceArtifact =
        ShaderInterfaceArtifact.decode(ByteBuffer.wrap(Base64.getDecoder().decode(encoded.getValue(name).second)))
}

private fun positionStages(functions: PipelineTestFunctions, vertexFacts: ShaderInterfaceArtifact = VertexTestFixtures.artifact(ShaderStage.Vertex)): VulkanShaderStages {
    val modules = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
        createVulkanShaderModule(functions.shaderFunctions, VertexTestFixtures.description(stage), if (stage == ShaderStage.Vertex) vertexFacts else VertexTestFixtures.artifact(stage)) as VulkanShaderModule
    }
    return createVulkanShaderStages(functions.shaderFunctions, ShaderStagesDescription(modules, "direct position")) as VulkanShaderStages
}
