/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.opengl.RecordingCompiler

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.opengl.*
import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.shader.*
import heckerpowered.render.shader.primitive.PrimitiveShader
import heckerpowered.render.shader.primitive.ScreenConstantsMemoryLayout
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.test.*

class OpenGLFillScreenArtifactTest {
    @Test
    fun generatedArtifactMatchesActualBuiltinVertexAndScreenConstantLayouts() {
        val vertex = artifact(ShaderStage.Vertex)
        val fragment = artifact(ShaderStage.Fragment)
        val primitive = PrimitiveShader.FillScreen
        val vertexBuffer = primitive.vertexState.buffers.single()
        assertEquals(8, vertexBuffer.stride)
        val input = vertex.inputs.single()
        val attribute = vertexBuffer.attributes.single()
        assertEquals(attribute.location, input.location)
        assertEquals(VertexFormat.Float32x2, attribute.format)
        assertEquals(2, input.components)
        assertEquals(0, attribute.offset)
        assertTrue(vertex.bindings.isEmpty() && fragment.bindings.isEmpty())
        assertTrue(vertex.pushConstants.isEmpty())
        assertTrue(fragment.inputs.isEmpty())
        val color = fragment.pushConstants.single()
        assertEquals(ScreenConstantsMemoryLayout.DESTINATION_OFFSET_BYTES + ScreenConstantsMemoryLayout.COLOR_OFFSET, color.offsetBytes)
        assertEquals(ScreenConstantsMemoryLayout.SIZE.toLong(), color.sizeBytes)
        assertEquals(4, color.components)
        assertEquals(1, color.columns)
        val range = checkNotNull(primitive.layoutDescription.pushConstants).ranges.single()
        assertEquals(setOf(ShaderStage.Fragment), range.stages)
        assertEquals(color.offsetBytes, range.offsetBytes)
        assertEquals(color.sizeBytes, range.sizeBytes.toLong())
        assertEquals(0, fragment.outputs.single().location)
        assertEquals(4, fragment.outputs.single().components)
        assertTrue(primitive.layoutDescription.descriptorSets.isEmpty())
    }

    @Test
    fun builtinArtifactsLinkReflectAndCreatePipelineWithoutLegacyNameInference() {
        for (legacy in listOf(true, false)) {
            FillScreenDriver(legacy).use { driver ->
                val primitive = PrimitiveShader.FillScreen
                val stages = driver.device.primitives[primitive] as OpenGLShaderStages
                assertSame(stages, driver.device.primitives[primitive])
                assertNull(stages.standardShader)
                val layout = driver.device.createPipelineLayout(primitive.layoutDescription)
                try {
                    val pipeline = driver.device.createRenderPipeline(RenderPipelineDescription(
                        label = "artifact fill",
                        shaders = stages,
                        vertex = primitive.vertexState,
                        layout = layout,
                        colorTargets = listOf(ColorTargetState(TextureFormat.Rgba8UnsignedNormalized)),
                    )) as OpenGLRenderPipeline
                    try {
                        val reflected = pipeline.resourceInterface
                        assertEquals(artifact(ShaderStage.Vertex).inputs, reflected.inputs)
                        assertTrue(reflected.bindings.isEmpty())
                        val color = reflected.pushConstants.single()
                        assertEquals(ShaderStage.Fragment, color.stage)
                        assertEquals(artifact(ShaderStage.Fragment).pushConstants.single(), color.member)
                        assertEquals(9, color.location.value)
                        assertEquals(listOf(0 to artifact(ShaderStage.Vertex).inputs.single().name), driver.boundAttributes)
                        assertEquals(listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
                            val artifact = artifact(stage)
                            if (legacy) artifact.source else artifact.coreSource
                        }, driver.sources)
                        val output = artifact(ShaderStage.Fragment).outputs.single()
                        assertEquals(if (legacy) emptyList() else listOf("bind:${output.location}:${output.name}", "link", "query:${output.name}"), driver.outputCalls)
                        val values = OpenGLPushConstants()
                        val source = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).apply {
                            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { putFloat(it) }
                            flip()
                        }
                        values.write(checkNotNull(primitive.layoutDescription.pushConstants), setOf(ShaderStage.Fragment), 0, source)
                        assertContentEquals(floatArrayOf(0.25f, 0.5f, 0.75f, 1f), values.valuesFor(primitive.layoutDescription.pushConstants!!, color))
                    } finally {
                        pipeline.close()
                    }
                } finally {
                    layout.close()
                }
            }
        }
    }

    @Test
    fun coreOutputCapabilityIsRequiredBeforeProgramAllocation() {
        FillScreenDriver(false).use { driver ->
            driver.supportsOutputLocations = false
            assertFailsWith<UnsupportedOperationException> { driver.device.primitives[PrimitiveShader.FillScreen] }
            assertEquals(0, driver.createdPrograms)
            assertEquals(2, driver.deletedShaders.size)
        }
    }

    @Test
    fun legacyOutputDoesNotRequireLocationCapability() {
        FillScreenDriver(true).use { driver ->
            driver.supportsOutputLocations = false
            driver.device.primitives[PrimitiveShader.FillScreen]
            assertTrue(driver.outputCalls.isEmpty())
        }
    }

    @Test
    fun wrongCoreOutputLocationReleasesProgramAndModules() {
        FillScreenDriver(false).use { driver ->
            driver.outputLocation = 1
            assertFailsWith<IllegalArgumentException> { driver.device.primitives[PrimitiveShader.FillScreen] }
            assertEquals(1, driver.deletedPrograms.size)
            assertEquals(2, driver.deletedShaders.size)
        }
    }

    @Test
    fun optimizedOutCoreOutputIsAllowed() {
        FillScreenDriver(false).use { driver ->
            driver.outputLocation = -1
            driver.device.primitives[PrimitiveShader.FillScreen]
            assertEquals(3, driver.outputCalls.size)
        }
    }

    @Test
    fun selectedLegacy140SourceUsesMetadataNameAndLocation() {
        FillScreenDriver(true).use { driver ->
            driver.languageVersion = "1.40"
            driver.outputLocation = 3
            val original = artifact(ShaderStage.Fragment)
            val changed = original.copy(
                glslVersion = 140,
                source = original.coreSource.replace(original.outputs.single().name, "custom_output"),
                outputs = listOf(OpenGLShaderInput(
                    location = 3,
                    components = 4,
                    name = "custom_output",
                )),
            )
            val module = driver.device.createShaderModule(ShaderModuleDescription(
                stage = ShaderStage.Fragment,
                code = ShaderBinary.copyOf(ByteBuffer.wrap(changed.encode()), ShaderBinaryFormat.OpenGLGlsl, "custom fragment"),
                label = "custom fragment",
            ))
            try {
                val stages = driver.device.createShaderStages(ShaderStagesDescription(
                    modules = listOf(module),
                    label = "custom output",
                ))
                try {
                    assertEquals(listOf("bind:3:custom_output", "link", "query:custom_output"), driver.outputCalls)
                } finally {
                    stages.close()
                }
            } finally {
                module.close()
            }
        }
    }

    @Test
    fun nativeOutputErrorsReleaseProgramAndModules() {
        for (operation in listOf("bind", "query")) {
            FillScreenDriver(false).use { driver ->
                driver.failedOutputOperation = operation
                assertFailsWith<OpenGLOperationException> { driver.device.primitives[PrimitiveShader.FillScreen] }
                assertEquals(1, driver.deletedPrograms.size)
                assertEquals(2, driver.deletedShaders.size)
            }
        }
    }

    @Test
    fun failedBuiltinLinkReleasesBothModulesAndProgramBeforeRetry() {
        FillScreenDriver(true).use { driver ->
            driver.linked = false
            assertFailsWith<ShaderStagesCreationException> { driver.device.primitives[PrimitiveShader.FillScreen] }
            assertEquals(2, driver.deletedShaders.size)
            assertEquals(1, driver.deletedPrograms.size)
            driver.linked = true
            val stages = driver.device.primitives[PrimitiveShader.FillScreen]
            assertSame(stages, driver.device.primitives[PrimitiveShader.FillScreen])
            assertEquals(4, driver.sources.size)
        }
    }
}

private fun artifact(stage: ShaderStage): OpenGLShaderArtifact {
    val extension = if (stage == ShaderStage.Vertex) "vert" else "frag"
    val path = "/heckerpowered/render/opengl/shader/fill-screen.$extension.rhigl"
    val bytes = checkNotNull(OpenGLFillScreenArtifactTest::class.java.getResourceAsStream(path)).use { it.readBytes() }
    return OpenGLShaderArtifact.decode(ByteBuffer.wrap(bytes))
}

private class FillScreenDriver(private val legacy: Boolean) : AutoCloseable {
    var linked = true
    var supportsOutputLocations = true
    var outputLocation = 0
    var languageVersion = if (legacy) "1.20" else "1.40"
    var failedOutputOperation: String? = null
    private var error = 0
    val outputCalls = mutableListOf<String>()
    val createdPrograms: Int get() = program
    val sources = mutableListOf<String>()
    val boundAttributes = mutableListOf<Pair<Int, String>>()
    val deletedShaders = mutableListOf<Int>()
    val deletedPrograms = mutableListOf<Int>()
    private var hasVertex = false
    private var shader = 0
    private var program = 0
    private val input = artifact(ShaderStage.Vertex).inputs.single()
    private val color = artifact(ShaderStage.Fragment).pushConstants.single()
    private val framebuffers = Proxy.newProxyInstance(OpenGLFramebufferFunctions::class.java.classLoader, arrayOf(OpenGLFramebufferFunctions::class.java)) { _, method, _ ->
        error("Unexpected framebuffer call: ${method.name}")
    } as OpenGLFramebufferFunctions
    private val functions = Proxy.newProxyInstance(OpenGLFunctions::class.java.classLoader, arrayOf(OpenGLFunctions::class.java)) { _, method, arguments ->
        val args = arguments ?: emptyArray()
        when (val name = method.name.substringBefore('-')) {
            "checkCurrentContext", "compileShader", "attachShader" -> null
            "linkProgram" -> { if (outputCalls.isNotEmpty()) outputCalls.add("link"); null }
            "getSupportsFragmentOutputLocations" -> supportsOutputLocations
            "bindFragmentOutputLocation" -> {
                outputCalls.add("bind:${args[1]}:${args[2]}")
                if (failedOutputOperation == "bind") error = 0x0502
                null
            }
            "getFragmentOutputLocation" -> {
                outputCalls.add("query:${args[1]}")
                if (failedOutputOperation == "query") error = 0x0502
                outputLocation
            }
            "getFramebuffers" -> framebuffers
            "getUniformBuffers" -> null
            "getSupportsLegacyPixelTransfer" -> legacy
            "getSupportsSeparateBlendEquations" -> true
            "getString" -> languageVersion
            "getError" -> error.also { error = 0 }
            "createShader" -> { if (args[0] == ShaderType.Vertex) hasVertex = true; ++shader }
            "createProgram" -> ++program
            "getShaderCompileStatus" -> true
            "getProgramLinkStatus" -> linked
            "getProgramInfoLog" -> "synthetic link failure"
            "shaderSource" -> { sources.add(args[1].toString()); null }
            "bindVertexAttributeLocation" -> { boundAttributes.add((args[1] as Int) to args[2].toString()); null }
            "deleteShader" -> { deletedShaders.add(args[0] as Int); null }
            "deleteProgram" -> { deletedPrograms.add(args[0] as Int); null }
            "getInteger" -> 16
            "getProgramInteger" -> when (args[1]) {
                0x8B86 -> 1
                0x8B89 -> if (hasVertex) 1 else 0
                0x8B87 -> color.name.length + 1
                0x8B8A -> input.name.length + 1
                else -> error("Unexpected program query: ${args[1]}")
            }
            "getActiveUniform", "getActiveVertexAttribute" -> {
                val uniform = name == "getActiveUniform"
                val variableName = if (uniform) color.name else input.name
                val bytes = variableName.toByteArray(Charsets.UTF_8)
                (args[2] as IntBuffer).put(0, bytes.size)
                (args[3] as IntBuffer).put(0, 1)
                (args[4] as IntBuffer).put(0, if (uniform) 0x8B52 else 0x8B50)
                val destination = args[5] as ByteBuffer
                bytes.forEachIndexed { index, value -> destination.put(index, value) }
                null
            }
            "getUniformLocation" -> { assertEquals(color.name, args[1]); 9 }
            "getVertexAttributeLocation" -> { assertEquals(input.name, args[1]); 0 }
            else -> error("Unexpected native call: $name")
        }
    } as OpenGLFunctions
    val device = OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler())

    override fun close() = terminateOnFailure { device.close() }
}
