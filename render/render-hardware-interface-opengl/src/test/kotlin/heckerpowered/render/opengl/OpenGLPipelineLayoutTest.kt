/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.pipeline.*
import heckerpowered.render.opengl.function.*
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.binding.*
import java.lang.reflect.Proxy
import kotlin.test.*

class OpenGLPipelineLayoutTest {
    @Test
    fun emptyLayoutsNeedNoUniformCapabilityOrNativeState() {
        val driver = LayoutDriver(false)
        val layout = driver.device.createPipelineLayout(PipelineLayoutDescription(label = "empty")) as OpenGLPipelineLayout
        assertEquals(0, layout.pushConstantSizeBytes)
        assertTrue(driver.calls.isEmpty())
        layout.close()
        layout.close()
        assertTrue(driver.calls.isEmpty())
        assertFailsWith<IllegalStateException> { layout.checkOpen() }
        driver.device.close()
    }

    @Test
    fun sparseSetsBindingsAndArraysRetainDistinctAddressesAtExactLimits() {
        val driver = LayoutDriver()
        driver.maximumBindings = 4
        driver.limits[0x8A2B] = 4
        driver.limits[0x8A2D] = 4
        driver.limits[0x8A2E] = 8
        val shared = DescriptorSetLayout(listOf(binding(9, 2)))
        val description = PipelineLayoutDescription(listOf(shared, DescriptorSetLayout.Empty, shared), label = "sparse")
        val layout = driver.device.createPipelineLayout(description) as OpenGLPipelineLayout
        assertEquals(listOf(0, 1, 2, 3), listOf(
            layout.uniformBinding(0, 9, 0).value,
            layout.uniformBinding(0, 9, 1).value,
            layout.uniformBinding(2, 9, 0).value,
            layout.uniformBinding(2, 9, 1).value,
        ))
        assertSame(description, layout.description)
        assertFailsWith<IllegalArgumentException> { layout.uniformBinding(1, 9, 0) }
        assertFailsWith<IllegalArgumentException> { layout.uniformBinding(0, 10, 0) }
        assertFailsWith<IllegalArgumentException> { layout.uniformBinding(0, 9, -1) }
        assertFailsWith<IllegalArgumentException> { layout.uniformBinding(0, 9, 2) }
        assertEquals(listOf("error", "bindings", "error", "query:35374", "error", "query:35371", "error", "query:35373", "error", "error"), driver.calls)
        layout.close()
        driver.device.close()
    }

    @Test
    fun ordinaryPushConstantsRetainHighestByteWithoutRequiringOrReservingUboStorage() {
        val driver = LayoutDriver(false)
        val pushes = PushConstantLayout(listOf(
            PushConstantRange(setOf(ShaderStage.Vertex), 48, 16),
            PushConstantRange(setOf(ShaderStage.Fragment), 48, 32),
        ))
        val layout = driver.device.createPipelineLayout(PipelineLayoutDescription(pushConstants = pushes, label = "ordinary push")) as OpenGLPipelineLayout
        assertEquals(80, layout.pushConstantSizeBytes)
        assertTrue(driver.calls.isEmpty())
        layout.close()
        driver.device.close()
    }

    @Test
    fun declaredArraysAndStageVisibilityCountBeforeShaderElimination() {
        val description = request(binding(0, 2), PushConstantLayout(listOf(PushConstantRange(setOf(ShaderStage.Vertex, ShaderStage.Fragment), 0, 16))))
        for ([parameter, limit] in listOf(0x8A2B to 1, 0x8A2D to 1, 0x8A2E to 3)) {
            val driver = LayoutDriver()
            driver.limits[parameter] = limit
            assertFailsWith<UnsupportedOperationException>("limit $parameter") { driver.device.createPipelineLayout(description) }
            driver.device.close()
        }
        val driver = LayoutDriver()
        driver.maximumBindings = 1
        assertFailsWith<UnsupportedOperationException> { driver.device.createPipelineLayout(description) }
        driver.device.close()
    }

    @Test
    fun minimumExposedRangeIsNotShaderBlockSizeOrActiveUniformStorage() {
        val driver = LayoutDriver()
        driver.limits[0x8A31] = 1024
        driver.limits[0x8A33] = 1024
        for (minimumBytes in listOf(8192L, 65536L, Long.MAX_VALUE)) {
            val layout = driver.device.createPipelineLayout(request(binding(0, 1, minimumBytes))) as OpenGLPipelineLayout
            assertEquals(0, layout.uniformBinding(0, 0, 0).value)
            layout.close()
        }
        assertTrue(driver.calls.none { it in setOf("query:35376", "query:35377", "query:35379") })
        driver.device.close()
    }

    @Test
    fun enormousCountsAreRejectedWithoutOverflowOrElementAllocation() {
        val driver = LayoutDriver()
        assertFailsWith<UnsupportedOperationException> { driver.device.createPipelineLayout(request(binding(0, Int.MAX_VALUE))) }
        driver.device.close()
    }

    @Test
    fun unsupportedResourcesAndMissingCapabilityRejectBeforeLimitQueries() {
        val driver = LayoutDriver()
        val declarations = listOf(
            DescriptorBindingLayout(0, DescriptorType.Sampler, setOf(ShaderStage.Fragment)),
            DescriptorBindingLayout(0, DescriptorType.StorageBuffer(), setOf(ShaderStage.Vertex)),
        )
        for (declaration in declarations) {
            assertFailsWith<UnsupportedOperationException> { driver.device.createPipelineLayout(request(declaration)) }
        }
        assertTrue(driver.calls.isEmpty())
        driver.device.close()
        val legacy = LayoutDriver(false)
        val cpuLayout = legacy.device.createPipelineLayout(request(binding(0)))
        cpuLayout.close()
        assertTrue(legacy.calls.isEmpty())
        legacy.device.close()
    }

    @Test
    fun pendingAndQueryErrorsAreCreationFailuresRatherThanUnsupportedLimits() {
        for (query in listOf<Int?>(null, 0x8A2E)) {
            val driver = LayoutDriver()
            if (query == null) driver.error = 0x0502 else driver.errorParameter = query
            val failure = assertFailsWith<PipelineLayoutCreationException> { driver.device.createPipelineLayout(request(binding(0))) }
            assertEquals(0x0502, (failure.cause as OpenGLOperationException).errorCode)
            driver.device.close()
        }
    }

    @Test
    fun deviceAndContextLifetimeAreCheckedBeforeQueriesOrAddressUse() {
        val driver = LayoutDriver()
        val layout = driver.device.createPipelineLayout(request(binding(0))) as OpenGLPipelineLayout
        val before = driver.calls.toList()
        driver.accessible = false
        assertFailsWith<IllegalStateException> { driver.device.createPipelineLayout(request(binding(0))) }
        assertFailsWith<IllegalStateException> { layout.uniformBinding(0, 0, 0) }
        assertEquals(before, driver.calls)
        driver.accessible = true
        layout.close()
        driver.device.close()
        assertFailsWith<IllegalStateException> { driver.device.createPipelineLayout(PipelineLayoutDescription(label = "closed")) }
    }

    private fun binding(number: Int, count: Int = 1, minimumBytes: Long = 0): DescriptorBindingLayout = DescriptorBindingLayout(
        number,
        DescriptorType.UniformBuffer(minimumBytes),
        setOf(ShaderStage.Vertex, ShaderStage.Fragment),
        count,
    )

    private fun request(binding: DescriptorBindingLayout, pushes: PushConstantLayout? = null): PipelineLayoutDescription = PipelineLayoutDescription(
        listOf(DescriptorSetLayout(listOf(binding))),
        pushes,
        "layout test",
    )
}

private class LayoutDriver(hasUniforms: Boolean = true) {
    val calls = mutableListOf<String>()
    val limits = mutableMapOf(0x8A30 to 16384, 0x8A2E to 24, 0x8A2B to 12, 0x8A2D to 12, 0x8A31 to 65536, 0x8A33 to 65536)
    var maximumBindings = 24
    var error = 0
    var errorParameter: Int? = null
    var accessible = true
    private val uniforms = proxy<OpenGLUniformBufferFunctions> { name, _ ->
        check(name == "getMaximumBindings") { "Unexpected native mutation: $name" }
        calls.add("bindings")
        maximumBindings
    }
    private val framebuffers = proxy<OpenGLFramebufferFunctions> { name, _ -> error("Unexpected framebuffer call: $name") }
    private val functions = proxy<OpenGLFunctions> { name, arguments ->
        when (name) {
            "checkCurrentContext" -> { check(accessible) { "Context unavailable" }; null }
            "getFramebuffers" -> framebuffers
            "getUniformBuffers" -> if (hasUniforms) uniforms else null
            "getError" -> { calls.add("error"); error.also { error = 0 } }
            "getInteger" -> {
                val parameter = arguments!![0] as Int
                calls.add("query:$parameter")
                if (parameter == errorParameter) { error = 0x0502; 0 } else limits.getValue(parameter)
            }
            else -> error("Unexpected native mutation: $name")
        }
    }
    val device = OpenGLGraphicsDevice(functions, canonicalShaderCompiler = RecordingCompiler()).also { calls.clear() }
}

private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments -> invoke(method.name, arguments) } as T
