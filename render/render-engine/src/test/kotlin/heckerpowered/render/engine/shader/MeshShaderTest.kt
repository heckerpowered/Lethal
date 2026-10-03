/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader

import heckerpowered.math.Matrices
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.material.parameter.*
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.scene.GeometryElementBuilder
import heckerpowered.render.engine.shader.binding.*
import heckerpowered.render.engine.shader.parameter.NumericPacking
import heckerpowered.render.engine.shader.parameter.ParameterDerivations
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.engine.shader.program.MeshShaderInput
import heckerpowered.render.engine.shader.program.MeshShader
import heckerpowered.render.engine.shader.program.FragmentOutput
import heckerpowered.render.engine.shader.program.shaderDefinition
import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class MeshShaderTest {
    private val geometry = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState())
    @Test
    fun definitionCapturesBinaryCodeStageSetsAndPackingFields() {
        val bytes = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
        val modules = arrayListOf(
            ShaderModuleDescription(ShaderStage.Vertex, ShaderSource(ShaderLanguage.Glsl, "vertex fixture", "test")),
            ShaderModuleDescription(ShaderStage.Fragment, ShaderBinary.viewOf(bytes, ShaderBinaryFormat.SpirV, "test")),
        )
        val fields = arrayListOf(PushConstantField(ParameterName("value"), 0, 4))
        val stages = mutableSetOf(ShaderStage.Fragment)
        val shader = MeshShader<Float>(shaderDefinition("test") {
            native(
                modules,
                ShaderInputLayout(
                    VertexInputMapping(emptyList()),
                    emptyList(),
                    listOf(PushConstantPacking(stages, 0, 4, listOf(PushConstantField(ParameterName("packed"), 0, 4)))),
                    derivations = ParameterDerivations(packing = listOf(NumericPacking(ParameterName("packed"), 4, fields))),
                ),
            )
        }) { MeshShaderInput(geometry, ParameterValues().replacing("value", NumericParameterValue.floats(it))) }
        modules.clear()
        bytes.put(0, 9)
        stages.clear()
        fields.clear()

        val device = Proxy.newProxyInstance(GraphicsDevice::class.java.classLoader, arrayOf(GraphicsDevice::class.java)) { _, _, _ -> error("Native definitions need no CPU preparation") } as GraphicsDevice
        val definition = ResourceLifetime.build { this }.use { ShaderRealizations(device, it).definition(shader) }
        assertEquals(2, definition.modules.size)
        assertEquals(1.toByte(), (definition.modules[1].code as ShaderBinary).bytes.get(0))
        assertEquals(setOf(ShaderStage.Fragment), definition.inputs.pushes.single().stages)
        val values = definition.inputs.derivations.resolve(shader.bind(.75f).shading.parameters, Matrices.Identity)
        assertEquals(.75f, ByteBuffer.wrap(values.requireNumeric(ParameterName("packed")).bytes()).order(ByteOrder.nativeOrder()).float)
        assertFailsWith<UnsupportedOperationException> { (definition.modules as MutableList<ShaderModuleDescription>).clear() }
    }

    @Test
    fun bindingEncodesMutableInputImmediately() {
        val shader = MeshShader<FloatArray>(shaderDefinition("test") {
            native(modules(), ShaderInputLayout(VertexInputMapping(emptyList()), emptyList(), emptyList()))
        }) { MeshShaderInput(geometry, ParameterValues().replacing("value", NumericParameterValue.floats(it.single()))) }
        val values = floatArrayOf(.75f)
        val shading = shader.bind(values).shading
        values[0] = .25f

        assertSame(shader, shading.shader)
        assertEquals(.75f, ByteBuffer.wrap(shading.parameters.requireNumeric(ParameterName("value")).bytes()).order(ByteOrder.nativeOrder()).float)
    }

    @Test
    fun configuredBindingEncodesOnceBeforeConfigurationAndKeepsBorrowedInputs() {
        val buffer = proxy<GpuBuffer> { property ->
            when (property) {
                "getSizeBytes" -> 4L
                else -> error("Binding must not access or release borrowed GPU storage: $property")
            }
        }
        val view = GpuBufferView(buffer, 0, 4)
        val resource = BufferParameterValue(view)
        var encodings = 0
        val shader = MeshShader<FloatArray>(shaderDefinition("configured") {
            native(modules(), ShaderInputLayout(VertexInputMapping(emptyList()), emptyList(), emptyList()))
        }) { values ->
            encodings++
            MeshShaderInput(
                geometry,
                ParameterValues(
                    ParameterName("gain") to NumericParameterValue.floats(values.single()),
                    ParameterName("buffer") to resource,
                ),
            )
        }
        val values = floatArrayOf(.75f)
        val element = shader.bind(values) {
            assertEquals(1, encodings)
            values[0] = .25f
            composition(CompositingMode.SourceOver)
            depthWrite(false)
        }
        values[0] = 1f

        assertEquals(1, encodings)
        assertSame(shader, element.shading.shader)
        assertSame(geometry, element.geometry)
        assertSame(resource, element.shading.parameters.require(ParameterName("buffer")))
        assertSame(view, assertIs<BufferParameterValue>(element.shading.parameters.require(ParameterName("buffer"))).view)
        assertSame(buffer, view.buffer)
        assertEquals(.75f, ByteBuffer.wrap(element.shading.parameters.requireNumeric(ParameterName("gain")).bytes()).order(ByteOrder.nativeOrder()).float)
        assertSame(CompositingMode.SourceOver, element.composition)
        assertEquals(false, element.depthWrite)
    }

    @Test
    fun emptyBindingConfigurationPreservesPassInheritedState() {
        val shader = definition()
        val ordinary = shader.bind(Unit)
        val configured = shader.bind(Unit) {}

        assertSame(geometry, configured.geometry)
        assertEquals(ordinary.compositions, configured.compositions)
        assertTrue(configured.compositions.isEmpty())
        assertSame(CompositingMode.Replace, configured.composition)
        assertNull(configured.depthWrite)
        assertEquals(ordinary.depthStencil, configured.depthStencil)
        assertEquals(ordinary.usesViewDepthCompare, configured.usesViewDepthCompare)
        assertEquals(ordinary.cullingBounds, configured.cullingBounds)
    }

    @Test
    fun configuredCompositionsKeepOutputLocationsAndSnapshotEscapedBuilder() {
        val shader = MeshShader<Unit>(shaderDefinition("multiple outputs") {
            native(modules(), ShaderInputLayout(VertexInputMapping(emptyList()), emptyList(), emptyList()))
            output(0, FragmentOutput())
            output(2, FragmentOutput())
        }) { MeshShaderInput(geometry) }
        lateinit var builder: GeometryElementBuilder
        val element = shader.bind(Unit) {
            builder = this
            composition(CompositingMode.Add, location = 2)
            composition(CompositingMode.SourceOver, location = 0)
            depthWrite(true)
        }
        builder.composition(CompositingMode.Replace, location = 2)
        builder.depthWrite(false)

        assertEquals(setOf(0, 2), shader.outputs.keys)
        assertEquals(setOf(0, 2), element.compositions.keys)
        assertSame(CompositingMode.SourceOver, element.compositions.getValue(0))
        assertSame(CompositingMode.Add, element.compositions.getValue(2))
        assertEquals(true, element.depthWrite)
        assertFailsWith<UnsupportedOperationException> {
            (element.compositions as MutableMap<Int, CompositingMode>).clear()
        }
        assertFailsWith<IllegalArgumentException> {
            shader.bind(Unit) { composition(CompositingMode.Add, location = -1) }
        }
    }

    @Test
    fun oneDefinitionSupportsDifferentTypedEncodersSharingOneDeviceProgram() {
        val definition = shaderDefinition("gain") {
            native(modules(), ShaderInputLayout(
                VertexInputMapping(emptyList()),
                emptyList(),
                listOf(PushConstantPacking(setOf(ShaderStage.Fragment), 0, 4, listOf(PushConstantField(ParameterName("gain"), 0, 4)))),
            ))
        }
        val scalar = MeshShader<Float>(definition) { gain ->
            MeshShaderInput(geometry, ParameterValues().replacing("gain", NumericParameterValue.floats(gain)))
        }
        val array = MeshShader<FloatArray>(definition) { gains ->
            MeshShaderInput(geometry, ParameterValues().replacing("gain", NumericParameterValue.floats(gains.single())))
        }
        val device = ShaderDevice()
        val lifetime = ResourceLifetime.build { this }
        try {
            assertSame(scalar.definition, array.definition)
            val scalarValue = scalar.bind(.25f).shading.parameters.requireNumeric(ParameterName("gain"))
            val arrayValue = array.bind(floatArrayOf(.75f)).shading.parameters.requireNumeric(ParameterName("gain"))
            assertEquals(.25f, ByteBuffer.wrap(scalarValue.bytes()).order(ByteOrder.nativeOrder()).float)
            assertEquals(.75f, ByteBuffer.wrap(arrayValue.bytes()).order(ByteOrder.nativeOrder()).float)
            val programs = ShaderRealizations(device.device, lifetime)
            assertSame(programs.definition(scalar), programs.definition(array))
            assertSame(programs.require(scalar), programs.require(array))
            assertEquals(4, device.created)
        } finally {
            lifetime.close()
        }
        assertEquals(device.created, device.closed)
    }

    @Test
    fun sameLabelsDoNotMergeDefinitionsOrDeviceOwnership() {
        val first = definition()
        val second = definition()
        val deviceA = ShaderDevice()
        val deviceB = ShaderDevice()
        val lifetimeA = ResourceLifetime.build { this }
        val lifetimeB = ResourceLifetime.build { this }
        try {
            val programsA = ShaderRealizations(deviceA.device, lifetimeA)
            val programsB = ShaderRealizations(deviceB.device, lifetimeB)
            val program = programsA.require(first)
            assertSame(program, programsA.require(first))
            assertNotSame(program, programsA.require(second))
            assertNotSame(program, programsB.require(first))
            assertEquals(8, deviceA.created)
            assertEquals(4, deviceB.created)
            lifetimeA.close()
            assertEquals(deviceA.created, deviceA.closed)
            assertEquals(0, deviceB.closed)
            assertSame(programsB.require(first), programsB.require(first))
        } finally {
            lifetimeA.close()
            lifetimeB.close()
        }
        assertEquals(deviceB.created, deviceB.closed)
    }

    @Test
    fun failedRealizationReleasesPartialResourcesAndCanRetry() {
        for (failurePoint in 1..4) {
            val device = ShaderDevice(failurePoint)
            val lifetime = ResourceLifetime.build { this }
            try {
                val programs = ShaderRealizations(device.device, lifetime)
                val shader = definition()
                assertFailsWith<AssertionError> { programs.require(shader) }
                assertEquals(device.created, device.closed)
                device.failAt = null
                val program = programs.require(shader)
                assertSame(program, programs.require(shader))
                assertEquals(4, device.created - device.closed)
            } finally { lifetime.close() }
            assertEquals(device.created, device.closed)
        }
    }

    private fun definition(): MeshShader<Unit> = MeshShader(shaderDefinition("same label") {
        native(modules(), ShaderInputLayout(VertexInputMapping(emptyList()), emptyList(), emptyList()))
    }) { MeshShaderInput(geometry) }

    private fun modules(): List<ShaderModuleDescription> = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map {
        ShaderModuleDescription(it, ShaderSource(ShaderLanguage.Glsl, "CPU realization fixture", "test"))
    }

    private class ShaderDevice(var failAt: Int? = null) {
        var created = 0
        var closed = 0
        private var attempts = 0
        val device = proxy<GraphicsDevice> { operation ->
            attempts++
            if (attempts == failAt) throw AssertionError("Shader creation failed")
            when (operation) {
                "createShaderModule" -> owned<ShaderModule>()
                "createShaderStages" -> owned<ShaderStages>()
                "createPipelineLayout" -> owned<PipelineLayout>()
                else -> error("Unexpected device operation: $operation")
            }
        }

        private inline fun <reified T> owned(): T {
            created++
            return proxy { operation ->
                when (operation) {
                    "close" -> terminateOnFailure { closed++; Unit }
                    else -> error("Unexpected resource operation: $operation")
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified T> proxy(crossinline invoke: (String) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> invoke(method.name.substringBefore('-')) } as T
