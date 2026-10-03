/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.opengl.function.*
import heckerpowered.render.opengl.shader.*
import heckerpowered.render.pipeline.*
import heckerpowered.render.pipeline.color.*
import heckerpowered.render.pipeline.depthstencil.DepthStencilState
import heckerpowered.render.pipeline.multisample.*
import heckerpowered.render.pipeline.primitive.*
import heckerpowered.render.pipeline.rasterization.*
import heckerpowered.render.pipeline.vertex.*
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.shader.*
import heckerpowered.render.shader.primitive.PrimitiveShader
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

class OpenGLRenderPipelineTest {
    @Test
    fun instanceLayoutsRequireDivisorsButEmptyInstanceLayoutDoesNot() {
        for (supported in listOf(false, true)) PipelineDriver().use { driver ->
            driver.instanceAttributesSupported = supported
            val instance = VertexBufferLayout(
                stride = 16,
                stepMode = VertexStepMode.Instance,
                attributes = listOf(VertexAttribute(
                    0,
                    VertexFormat.Float32x4,
                    0,
                )),
            )
            val description = driver.description().copy(vertex = VertexState(listOf(instance)))
            if (supported) driver.create(description) else assertFailsWith<UnsupportedOperationException> { driver.create(description) }
            driver.create(driver.description().copy(vertex = VertexState(listOf(instance.copy(attributes = emptyList())))))
        }
    }


    @Test
    fun creationRetainsAllBasicTopologiesAndRasterModesWithoutNativeMutations() {
        PipelineDriver().use { driver ->
            for (topology in PrimitiveTopology.entries) {
                for (mode in PolygonMode.entries) {
                    val description = driver.description().copy(
                        primitive = PrimitiveState(topology),
                        rasterization = RasterizationState(
                            CullMode.Back,
                            FrontFace.Clockwise,
                            mode,
                        ),
                        colorTargets = listOf(ColorTargetState(
                            TextureFormat.Rgba8UnsignedNormalized,
                            BlendState.StraightAlpha,
                            ColorWriteMask.Rgb,
                        )),
                    )
                    val pipeline = driver.create(description)
                    assertEquals(description, pipeline.description)
                    assertTrue(driver.calls.isEmpty())
                    pipeline.close()
                    pipeline.close()
                    assertTrue(driver.calls.isEmpty())
                    assertFailsWith<IllegalStateException> { pipeline.checkOpen() }
                }
            }
        }
    }

    @Test
    fun vertexAndTargetContainersAreSnapshotsAndCacheKeysRemainStable() {
        PipelineDriver().use { driver ->
            val attributes = mutableListOf(VertexAttribute(
                0,
                VertexFormat.Float32x3,
                0,
            ))
            val buffers = mutableListOf(VertexBufferLayout(
                12,
                attributes = attributes,
            ))
            val targets = mutableListOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized))
            val description = driver.description().copy(vertex = VertexState(buffers), colorTargets = targets)
            val pipeline = driver.resolve(description)
            val original = pipeline.description
            attributes.clear()
            buffers.clear()
            targets[0] = ColorTargetState(
                TextureFormat.Rgba8UnsignedNormalized,
                BlendState.Additive,
            )
            assertEquals(listOf(VertexAttribute(
                0,
                VertexFormat.Float32x3,
                0,
            )), original.vertex.buffers.single().attributes)
            assertEquals(null, original.colorTargets.single().blend)
            assertSame(pipeline, driver.resolve(original))
            assertNotSame(pipeline, driver.resolve(description))
            assertFailsWith<UnsupportedOperationException> { (original.colorTargets as MutableList).clear() }
            assertFailsWith<UnsupportedOperationException> { (original.vertex.buffers as MutableList).clear() }
            assertFailsWith<UnsupportedOperationException> { (original.vertex.buffers.single().attributes as MutableList).clear() }
            assertEquals(listOf("error", "integer:34921", "error"), driver.calls)
        }
    }

    @Test
    fun creationDoesNotShareCallerOwnedPipelinesButResolutionReusesDeviceOwnedOnes() {
        PipelineDriver().use { driver ->
            val request = driver.description()
            val first = driver.create(request)
            val second = driver.create(request)
            assertNotSame(first, second)
            val cached = driver.resolve(request)
            assertNotSame(first, cached)
            assertSame(cached, driver.resolve(request.copy()))
            cached.close()
            assertNotSame(cached, driver.resolve(request))
            assertTrue(driver.calls.isEmpty())
        }
    }

    @Test
    fun cacheHitChecksBorrowedLayoutAndRemovesInvalidEntry() {
        PipelineDriver().use { driver ->
            val layout = driver.device.createPipelineLayout(PipelineLayoutDescription(label = "empty")) as OpenGLPipelineLayout
            val request = driver.description().copy(layout = layout)
            val pipeline = driver.resolve(request)
            layout.close()
            assertFailsWith<IllegalStateException> { driver.resolve(request) }
            assertTrue(pipeline.isClosed)
            assertFailsWith<IllegalStateException> { driver.resolve(request) }
            assertTrue(driver.calls.isEmpty())
        }
    }

    @Test
    fun cacheHitChecksBorrowedProgramAndModulesWithoutDestroyingThem() {
        for (closeModule in listOf(false, true)) {
            PipelineDriver().use { driver ->
                val request = driver.description()
                val pipeline = driver.resolve(request)
                if (closeModule) driver.modules.first().close() else driver.stages.close()
                driver.calls.clear()
                assertFailsWith<IllegalStateException> { driver.resolve(request) }
                assertFailsWith<IllegalStateException> { driver.create(request) }
                assertTrue(pipeline.isClosed)
                assertTrue(driver.calls.isEmpty())
                assertFailsWith<IllegalStateException> { pipeline.checkOpen() }
            }
        }
    }

    @Test
    fun pipelineCloseNeverDeletesBorrowedStagesLayoutOrModules() {
        PipelineDriver().use { driver ->
            val layout = driver.device.createPipelineLayout(PipelineLayoutDescription(label = "borrowed")) as OpenGLPipelineLayout
            val pipeline = driver.create(driver.description().copy(layout = layout))
            pipeline.close()
            context(driver.device) { driver.stages.requireProgram(); driver.modules.forEach { it.requireShader() } }
            layout.checkOpen()
            assertTrue(driver.calls.isEmpty())
            layout.close()
        }
    }

    @Test
    fun deviceCloseReleasesCachedPipelinesAfterBorrowedResourcesHaveBeenClosed() {
        val driver = PipelineDriver()
        val cached = driver.resolve(driver.description())
        driver.stages.close()
        driver.modules.forEach { it.close() }
        driver.calls.clear()
        driver.device.close()
        assertTrue(cached.isClosed)
        assertTrue(driver.calls.isEmpty())
        driver.device.close()
        assertFailsWith<IllegalStateException> { cached.checkOpen() }
    }

    @Test
    fun deviceCloseReleasesPipelineCacheBeforeOtherOwnedResources() {
        val driver = PipelineDriver()
        val cached = driver.resolve(driver.description())
        driver.device.primitives[PrimitiveShader.Position]
        driver.stages.close()
        driver.modules.forEach { it.close() }
        driver.calls.clear()
        driver.onDeletion = { assertTrue(cached.isClosed) }
        driver.device.close()
        assertTrue(cached.isClosed)
        assertEquals(listOf("deleteProgram:101", "deleteShader:102", "deleteShader:101"), driver.calls)
    }

    @Test
    fun wrongContextAndClosedDeviceRejectBeforeCacheReuse() {
        val driver = PipelineDriver()
        val request = driver.description()
        val cached = driver.resolve(request)
        driver.accessible = false
        assertFailsWith<IllegalStateException> { driver.resolve(request) }
        assertFailsWith<IllegalStateException> { driver.create(request) }
        assertFalse(cached.isClosed)
        driver.accessible = true
        assertSame(cached, driver.resolve(request))
        driver.close()
        assertFailsWith<IllegalStateException> { driver.device.createRenderPipeline(request) }
        assertFailsWith<IllegalStateException> { driver.device.resolveRenderPipeline(request) }
    }

    @Test
    fun foreignBackendResourcesAndOtherDeviceResourcesAreRejected() {
        PipelineDriver().use { driver ->
            PipelineDriver().use { other ->
                assertFailsWith<IllegalArgumentException> { driver.create(driver.description().copy(shaders = other.stages)) }
                val layout = other.device.createPipelineLayout(PipelineLayoutDescription(label = "foreign"))
                try {
                    assertFailsWith<IllegalArgumentException> { driver.create(driver.description().copy(layout = layout)) }
                } finally { layout.close() }
                val alienStages = pipelineProxy<ShaderStages> { name, _ -> error("Unexpected alien shader call: $name") }
                val alienLayout = pipelineProxy<PipelineLayout> { name, _ -> error("Unexpected alien layout call: $name") }
                assertFailsWith<IllegalArgumentException> { driver.create(driver.description().copy(shaders = alienStages)) }
                assertFailsWith<IllegalArgumentException> { driver.create(driver.description().copy(layout = alienLayout)) }
                assertTrue(driver.calls.isEmpty())
            }
        }
    }

    @Test
    fun incompleteStagesRawProgramsAndMissingColorOutputTargetsReject() {
        for (stages in listOf(listOf(ShaderStage.Vertex), listOf(ShaderStage.Fragment), emptyList())) {
            PipelineDriver(stages = stages).use { driver ->
                assertFailsWith<IllegalArgumentException> { driver.create(driver.description()) }
                assertTrue(driver.calls.isEmpty())
            }
        }
        PipelineDriver(metadata = false).use { driver ->
            assertFailsWith<UnsupportedOperationException> { driver.create(driver.description()) }
        }
        PipelineDriver(outputLocation = 1).use { driver ->
            assertFailsWith<IllegalArgumentException> { driver.create(driver.description()) }
        }
    }

    @Test
    fun pipelineCreationUsesActiveResourceInterfaceRatherThanLayoutInference() {
        val member = OpenGLPushConstantMember(
            16,
            4,
            1,
            0,
            false,
            "push",
        )
        val reflection = OpenGLShaderInterface(
            emptyList(),
            listOf(OpenGLProgramPushConstant(
                ShaderStage.Vertex,
                member,
                UniformLocation(3),
            )),
            emptyList(),
        )
        PipelineDriver(reflection = reflection).use { driver ->
            assertFailsWith<IllegalArgumentException> { driver.create(driver.description()) }
            val layout = driver.device.createPipelineLayout(PipelineLayoutDescription(
                pushConstants = PushConstantLayout(listOf(PushConstantRange(setOf(ShaderStage.Vertex), 16, 16))),
                label = "push",
            ))
            try {
                val pipeline = driver.create(driver.description().copy(layout = layout))
                assertSame(reflection, pipeline.resourceInterface)
                assertEquals(16, pipeline.resourceInterface.pushConstants.single().member.offsetBytes)
                assertTrue(driver.calls.isEmpty())
            } finally { layout.close() }
        }
    }

    @Test
    fun missingVertexInputAndNativeLocationLimitAreCheckedBeforeReturningPipeline() {
        val reflection = OpenGLShaderInterface(
            emptyList(),
            emptyList(),
            listOf(OpenGLShaderInput(
                7,
                3,
                "position",
            )),
        )
        PipelineDriver(reflection = reflection).use { driver ->
            assertFailsWith<IllegalArgumentException> { driver.create(driver.description()) }
            val vertex = VertexState(listOf(VertexBufferLayout(
                12,
                attributes = listOf(VertexAttribute(
                    7,
                    VertexFormat.Float32,
                    0,
                )),
            )))
            driver.maximumAttributes = 8
            driver.create(driver.description().copy(vertex = vertex))
            driver.maximumAttributes = 7
            assertFailsWith<UnsupportedOperationException> { driver.create(driver.description().copy(vertex = vertex)) }
            assertEquals(listOf("error", "integer:34921", "error", "error", "integer:34921", "error"), driver.calls)
        }
    }

    @Test
    fun unsupportedAttachmentsAndUnexposedStateRejectExplicitly() {
        PipelineDriver().use { driver ->
            val base = driver.description()
            val unequal = BlendState(
                BlendComponent(
                    BlendFactor.One,
                    BlendFactor.Zero,
                ),
                BlendComponent(
                    BlendFactor.One,
                    BlendFactor.Zero,
                    BlendOperation.Subtract,
                ),
            )
            val descriptions = listOf(
                base.copy(colorTargets = emptyList()),
                base.copy(colorTargets = base.colorTargets + base.colorTargets),
                base.copy(colorTargets = listOf(ColorTargetState(TextureFormat.Rgba16Float))),
                base.copy(depthStencil = DepthStencilState(TextureFormat.Depth32Float)),
                base.copy(multisample = MultisampleState(SampleCount.Four)),
                base.copy(multisample = MultisampleState(alphaToCoverageEnabled = true)),
                base.copy(primitive = PrimitiveState(
                    PrimitiveTopology.TriangleStrip,
                    true,
                )),
                base.copy(colorTargets = listOf(ColorTargetState(
                    TextureFormat.Rgba8UnsignedNormalized,
                    unequal,
                ))),
                base.copy(vertex = VertexState(listOf(VertexBufferLayout(
                    4,
                    VertexStepMode.Instance,
                    listOf(VertexAttribute(
                        0,
                        VertexFormat.Float32,
                        0,
                    )),
                )))),
                base.copy(vertex = VertexState(listOf(VertexBufferLayout(
                    4,
                    attributes = listOf(VertexAttribute(
                        0,
                        VertexFormat.Uint32,
                        0,
                    )),
                )))),
            )
            for (description in descriptions) {
                assertFailsWith<UnsupportedOperationException> { driver.create(description) }
                assertFailsWith<UnsupportedOperationException> { driver.resolve(description) }
            }
            assertTrue(driver.calls.isEmpty())
        }
    }

    @Test
    fun unusedInstanceSlotAndNormalizedVertexOffsetsRemainSupported() {
        PipelineDriver().use { driver ->
            for (format in listOf(VertexFormat.Uint8x2Normalized, VertexFormat.Uint8x4Normalized)) {
                val vertex = VertexState(listOf(
                    VertexBufferLayout(
                        0,
                        VertexStepMode.Instance,
                        emptyList(),
                    ),
                    VertexBufferLayout(
                        format.sizeInBytes,
                        attributes = listOf(VertexAttribute(
                            0,
                            format,
                            100,
                        )),
                    ),
                ))
                assertEquals(vertex, driver.create(driver.description().copy(vertex = vertex)).description.vertex)
                val constantAddressVertex = vertex.copy(buffers = listOf(
                    vertex.buffers[0],
                    vertex.buffers[1].copy(stride = 0),
                ))
                val unsupported = driver.description().copy(vertex = constantAddressVertex)
                assertFailsWith<UnsupportedOperationException> { driver.create(unsupported) }
                assertFailsWith<UnsupportedOperationException> { driver.resolve(unsupported) }
            }
        }
    }

    @Test
    fun nativeQueryErrorsAreOperationalFailuresAndDoNotPopulateCache() {
        PipelineDriver().use { driver ->
            val vertex = VertexState(listOf(VertexBufferLayout(
                4,
                attributes = listOf(VertexAttribute(
                    0,
                    VertexFormat.Float32,
                    0,
                )),
            )))
            val request = driver.description().copy(vertex = vertex)
            driver.queryError = 0x0502
            assertFailsWith<OpenGLOperationException> { driver.resolve(request) }
            driver.queryError = 0
            val pipeline = driver.resolve(request)
            assertSame(pipeline, driver.resolve(request))
            assertEquals(listOf("error", "integer:34921", "error", "error", "integer:34921", "error"), driver.calls)
        }
    }
}

private class PipelineDriver(
    stages: List<ShaderStage> = listOf(ShaderStage.Vertex, ShaderStage.Fragment),
    metadata: Boolean = true,
    outputLocation: Int = 0,
    reflection: OpenGLShaderInterface = OpenGLShaderInterface(
        emptyList(),
        emptyList(),
        emptyList(),
    ),
) : AutoCloseable {
    val calls = mutableListOf<String>()
    var accessible = true
    var maximumAttributes = 16
    var instanceAttributesSupported = false
    var queryError = 0
    var onDeletion: () -> Unit = {}
    private var nextShader = 100
    private var nextProgram = 100
    private var error = 0
    private val framebuffers = pipelineProxy<OpenGLFramebufferFunctions> { name, _ -> error("Unexpected framebuffer mutation: $name") }
    private val functions = pipelineProxy<OpenGLFunctions> { name, arguments ->
        when (name.substringBefore('-')) {
            "checkCurrentContext" -> { check(accessible); null }
            "getSupportsFloatColorTextures", "getSupportsSeparateBlendEquations", "getSupportsDepthTextures", "getSupportsFloatDepthTextures" -> false
            "getFramebuffers" -> framebuffers
            "getSupportsInstanceAttributes" -> instanceAttributesSupported
            "getError" -> { calls.add("error"); error.also { error = 0 } }
            "getInteger" -> {
                assertEquals(0x8869, arguments!![0])
                calls.add("integer:${arguments[0]}")
                error = queryError
                maximumAttributes
            }
            "createShader" -> ++nextShader
            "createProgram" -> ++nextProgram
            "shaderSource", "compileShader", "attachShader", "bindVertexAttributeLocation", "linkProgram" -> null
            "getShaderCompileStatus", "getProgramLinkStatus" -> true
            "deleteProgram" -> { onDeletion(); calls.add("deleteProgram:${arguments!![0]}"); null }
            "deleteShader" -> { onDeletion(); calls.add("deleteShader:${arguments!![0]}"); null }
            else -> error("Unexpected native mutation: $name")
        }
    }
    val device = OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler()).also { calls.clear() }
    val modules = stages.mapIndexed { index, stage ->
        val artifact = if (metadata) OpenGLShaderArtifact(
            stage,
            120,
            "#version 120\nvoid main() {}",
            "#version 140\nvoid main() {}",
            emptyList(),
            if (stage == ShaderStage.Fragment) listOf(OpenGLShaderInput(
                outputLocation,
                4,
                "color",
            )) else emptyList(),
            emptyList(),
            emptyList(),
        ) else null
        OpenGLShaderModule(
            device,
            ShaderName(index + 1),
            ShaderModuleDescription(
                stage,
                ShaderSource(
                    ShaderLanguage.Glsl,
                    "void main() {}",
                    "synthetic",
                ),
            ),
            artifact,
        )
    }
    val stages = OpenGLShaderStages(
        device,
        ProgramName(1),
        modules,
        "synthetic",
        resourceInterface = if (metadata) reflection else null,
    )
    private val owned = mutableListOf<OpenGLRenderPipeline>()

    fun description(): RenderPipelineDescription = RenderPipelineDescription(
        label = "pipeline test",
        shaders = stages,
        colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)),
    )

    fun create(description: RenderPipelineDescription): OpenGLRenderPipeline =
        (device.createRenderPipeline(description) as OpenGLRenderPipeline).also { owned.add(it) }

    fun resolve(description: RenderPipelineDescription): OpenGLRenderPipeline = device.resolveRenderPipeline(description) as OpenGLRenderPipeline

    override fun close() = terminateOnFailure {
        owned.forEach { it.close() }
        stages.close()
        modules.forEach { it.close() }
        device.close()
    }
}

private inline fun <reified T> pipelineProxy(crossinline invoke: (String, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments -> invoke(method.name, arguments) } as T
