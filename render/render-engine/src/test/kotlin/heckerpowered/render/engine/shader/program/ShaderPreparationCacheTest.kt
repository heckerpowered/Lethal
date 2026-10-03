/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.shader.program

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Matrices
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.RenderEngine
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.engine.draw.PreparedDrawCommand
import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.pass.GeometryElementPassProcessor
import heckerpowered.render.engine.pass.RasterPass
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.shader.binding.PushConstantField
import heckerpowered.render.engine.shader.binding.PushConstantPacking
import heckerpowered.render.engine.shader.binding.VertexInputMapping
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.view.ViewParameters
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.shader.ShaderCompilation
import heckerpowered.render.shader.reflection.ShaderInterfaceDescription
import heckerpowered.render.shader.ShaderLanguage
import heckerpowered.render.shader.ShaderModule
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.ShaderStages
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import kotlin.test.*

class ShaderPreparationCacheTest {
    @Test
    fun engineLoadingPreparationAndFirstStageUseTheSameRealizationCache() {
        val fixture = Fixture()
        val engine = fixture.engine()
        val eager = shader()
        val lazy = shader()
        try {
            engine.prepare(eager)
            engine.prepare(eager)
            assertEquals(listOf(2, 1, 1), fixture.counts())
            engine.stage { rasterPass(fixture.pass(eager)) }
            assertEquals(listOf(2, 1, 1), fixture.counts())
            engine.stage { rasterPass(fixture.pass(lazy)) }
            assertEquals(listOf(4, 2, 2), fixture.counts())
            engine.stage { rasterPass(fixture.pass(lazy)) }
            assertEquals(listOf(4, 2, 2), fixture.counts())
        } finally {
            engine.close()
            fixture.root.close()
        }
        assertEquals(fixture.created, fixture.closed)
        assertFailsWith<IllegalStateException> { engine.prepare(eager) }
    }

    @Test
    fun eagerAndFirstUseShareModulesStagesAndLayoutForTwoDirectDefinitions() {
        val fixture = Fixture()
        try {
            val eager = shader()
            val lazy = shader()
            val prepared = fixture.programs.require(eager)
            assertEquals(listOf(2, 1, 1), fixture.counts())
            repeat(2) {
                val draw = fixture.draw(eager)
                assertSame(prepared.stages, draw.pipeline.shaders)
                assertSame(prepared.layout, draw.pipeline.layout)
                assertEquals(listOf(2, 1, 1), fixture.counts())
            }
            val first = fixture.draw(lazy)
            assertNotSame(prepared.stages, first.pipeline.shaders)
            assertEquals(listOf(4, 2, 2), fixture.counts())
            val second = fixture.draw(lazy)
            assertSame(first.pipeline.shaders, second.pipeline.shaders)
            assertSame(first.pipeline.layout, second.pipeline.layout)
            assertEquals(listOf(4, 2, 2), fixture.counts())
        } finally {
            fixture.root.close()
        }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun aSharedDefinitionHasIndependentDeviceProgramsAndRootLifetimes() {
        val first = Fixture()
        val second = Fixture()
        try {
            val definition = shader()
            val firstProgram = first.programs.require(definition)
            val secondProgram = second.programs.require(definition)
            assertNotSame(firstProgram.stages, secondProgram.stages)
            assertNotSame(firstProgram.layout, secondProgram.layout)
            assertEquals(listOf(2, 1, 1), first.counts())
            assertEquals(listOf(2, 1, 1), second.counts())
            first.root.close()
            assertEquals(first.created, first.closed)
            assertEquals(0, second.closed)
            assertSame(secondProgram, second.programs.require(definition))
        } finally {
            first.root.close()
            second.root.close()
        }
        assertEquals(second.created, second.closed)
    }

    @Test
    fun failedLinkReleasesPartialModulesAndDoesNotCacheAnIncompleteProgram() {
        val fixture = Fixture()
        try {
            val definition = shader()
            fixture.failNextLink = true
            assertFailsWith<IllegalStateException> { fixture.programs.require(definition) }
            assertEquals(2, fixture.closed)
            val ready = fixture.programs.require(definition)
            assertSame(ready, fixture.programs.require(definition))
            assertEquals(listOf(4, 2, 1), fixture.counts())
        } finally {
            fixture.root.close()
        }
        assertEquals(fixture.created, fixture.closed)
    }

    @Test
    fun differentTypedBindingsShareCanonicalPreparationWithinEachEngineAndRetryFailedLinking() {
        var sourceGenerations = 0
        var mappings = 0
        val definition = shaderDefinition("shared canonical gain") {
            canonical {
                sourceGenerations++
                listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
                    CanonicalShaderModule(ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "canonical cache fixture", stage.name)), stage.name)
                }
            }
            inputs {
                mappings++
                ShaderInputLayout(
                    VertexInputMapping(emptyList()),
                    emptyList(),
                    listOf(PushConstantPacking(setOf(ShaderStage.Fragment), 0, 4, listOf(PushConstantField(ParameterName("gain"), 0, 4)))),
                )
            }
        }
        val geometry = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState())
        val scalar = MeshShader<Float>(definition) { gain ->
            MeshShaderInput(geometry, ParameterValues().replacing("gain", NumericParameterValue.floats(gain)))
        }
        val array = MeshShader<FloatArray>(definition) { gains ->
            MeshShaderInput(geometry, ParameterValues().replacing("gain", NumericParameterValue.floats(gains.single())))
        }
        val first = Fixture()
        val second = Fixture()
        try {
            first.failNextLink = true
            assertFailsWith<IllegalStateException> { first.programs.require(scalar) }
            assertEquals(first.created, first.closed)
            val firstProgram = first.programs.require(array)
            assertSame(firstProgram, first.programs.require(scalar))
            assertEquals(2, first.compilationCalls)
            assertEquals(1, mappings)
            assertEquals(listOf(4, 2, 1), first.counts())
            val secondProgram = second.programs.require(scalar)
            assertSame(secondProgram, second.programs.require(array))
            assertNotSame(firstProgram.stages, secondProgram.stages)
            assertNotSame(firstProgram.layout, secondProgram.layout)
            assertEquals(2, second.compilationCalls)
            assertEquals(2, mappings)
            assertEquals(1, sourceGenerations)
            assertEquals(listOf(2, 1, 1), second.counts())
            first.root.close()
            assertEquals(first.created, first.closed)
            assertEquals(0, second.closed)
            assertSame(secondProgram, second.programs.require(array))
        } finally {
            first.root.close()
            second.root.close()
        }
        assertEquals(second.created, second.closed)
    }

    private class Fixture {
        val root = ResourceLifetime.build { this }
        var compilationCalls = 0
        var moduleCalls = 0
        var stageCalls = 0
        var layoutCalls = 0
        var created = 0
        var closed = 0
        var failNextLink = false
        private fun <T> resource(type: Class<T>): T {
            created++
            var released = false
            return proxy(type) { operation, _ ->
                check(operation == "close")
                terminateOnFailure {
                    check(!released)
                    released = true
                    closed++
                    Unit
                }
            }
        }
        private val device = proxy(GraphicsDevice::class.java) { operation, arguments ->
            when (operation) {
                "compileCanonicalShader" -> {
                    compilationCalls++
                    ShaderCompilation(arguments[0] as ShaderModuleDescription, ShaderInterfaceDescription(emptyList(), emptyList(), emptyList()))
                }
                "createShaderModule" -> { moduleCalls++; resource(ShaderModule::class.java) }
                "createShaderStages" -> {
                    stageCalls++
                    if (failNextLink) {
                        failNextLink = false
                        throw IllegalStateException("fixture link failure")
                    }
                    resource(ShaderStages::class.java)
                }
                "createPipelineLayout" -> { layoutCalls++; resource(PipelineLayout::class.java) }
                "createSampler" -> resource(GpuSampler::class.java)
                "awaitIdle" -> Unit
                "encode" -> Unit
                else -> error("Unexpected device operation: $operation")
            }
        }
        val programs = ShaderRealizations(device, root)
        private val processor = GeometryElementPassProcessor(programs)
        fun counts() = listOf(moduleCalls, stageCalls, layoutCalls)
        fun engine(): RenderEngine = RenderEngine.create(device)
        fun pass(shader: MeshShader<Float>): RasterPass {
            val element = shader.bind(1f)
            val submission = RenderSubmission(element, ObjectSubmitContext(AffineTransforms.Identity))
            return RasterPass(
                RenderPassDescription("cache probe", renderArea = RenderArea(0, 0, 1, 1)),
                RenderSubmissionList(listOf(submission)),
                ViewParameters(Matrices.Identity, 1, 1, CompareFunction.Always),
            )
        }
        fun draw(shader: MeshShader<Float>): PreparedDrawCommand {
            val pass = pass(shader)
            val submission = pass.collection.submissions.single()
            return processor.prepare(submission.element as GeometryElement, submission, pass, PassPreparation(device, root)).single()
        }
    }

    companion object {
        private fun shader(): MeshShader<Float> = MeshShader(shaderDefinition("same diagnostic label") {
            native(
                listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
                    ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "device cache fixture", "same diagnostic label"))
                },
                ShaderInputLayout(
                    VertexInputMapping(emptyList()),
                    emptyList(),
                    listOf(PushConstantPacking(
                        setOf(ShaderStage.Vertex, ShaderStage.Fragment),
                        0,
                        4,
                        listOf(PushConstantField(ParameterName("gain"), 0, 4)),
                    )),
                ),
            )
        }) { gain -> MeshShaderInput(
            ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState()),
            ParameterValues().replacing("gain", NumericParameterValue.floats(gain)),
        ) }

        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments -> invoke(method.name.substringBefore('-'), arguments ?: emptyArray()) } as T
    }
}
