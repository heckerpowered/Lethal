/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.opengl.RecordingCompiler

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.opengl.*
import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.opengl.function.OpenGLSpirVShaderFunctions
import heckerpowered.render.opengl.function.OpenGLUniformBufferFunctions
import heckerpowered.render.shader.*
import heckerpowered.render.shader.primitive.PrimitiveShader
import heckerpowered.render.terminateOnFailure
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.test.*

class OpenGLShaderCompilerTest {
    @Test
    fun standardProgramsAreLazyCachedPerDeviceAndReleasedWithTheirModules() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val library = device.primitives
        assertTrue(functions.calls.isEmpty())
        for (shader in PrimitiveShader.entries) {
            val program = library[shader]
            assertSame(program, library[shader])
            assertEquals(shader.takeUnless { it == PrimitiveShader.FillScreen }, (program as OpenGLShaderStages).standardShader)
            if (shader == PrimitiveShader.FillScreen) {
                context(device) { program.requireInterface() }
            }
        }
        assertEquals(6, functions.calls.count { it == "linkProgram" })
        assertEquals(12, functions.calls.count { it == "createShader" })
        assertEquals<List<Pair<Int, String>>>(listOf(0 to "position", 1 to "uv", 2 to "vertexColor"), requireNotNull(functions.attributeBindings[ProgramName(4)]).toList())
        val other = driverDevice(RecordingShaderFunctions())
        assertNotSame(library.position, other.primitives.position)
        other.close()
        device.close()
        device.close()
        assertEquals(6, functions.deletedPrograms.size)
        assertEquals(12, functions.deletedShaders.size)
        val destruction = functions.calls.filter { it.startsWith("delete") }
        assertEquals(List(6) { listOf("deleteProgram", "deleteShader", "deleteShader") }.flatten(), destruction)
        assertFailsWith<IllegalStateException> { library.position }
    }

    @Test
    fun failedStandardProgramLinkReleasesPartialResourcesAndCanBeRetried() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        functions.linkStatus = false
        assertFailsWith<ShaderStagesCreationException> { device.primitives.positionTexture }
        assertEquals(listOf(ProgramName(1)), functions.deletedPrograms)
        assertEquals(listOf(ShaderName(2), ShaderName(1)), functions.deletedShaders)
        functions.linkStatus = true
        val program = device.primitives.positionTexture
        assertSame(program, device.primitives.positionTexture)
        assertEquals(2, functions.calls.count { it == "linkProgram" })
        device.close()
        assertEquals(4, functions.deletedShaders.size)
    }

    @Test
    fun compilesTheRequestedStageWithoutBindingAProgram() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val description = sourceDescription(ShaderStage.Fragment)
        val module = device.compile(description)

        assertEquals(ShaderStage.Fragment, module.stage)
        assertEquals("main", module.entryPoint)
        assertEquals(description.code, ShaderSource(ShaderLanguage.Glsl, functions.sources.single(), "source"))
        assertEquals(listOf(ShaderType.Fragment), functions.shaderTypes)
        assertEquals(ShaderName(1), context(device) { module.requireShader() })

        module.close()
        module.close()
        assertEquals(listOf(ShaderName(1)), functions.deletedShaders)
        assertFailsWith<IllegalStateException> { context(device) { module.requireShader() } }
    }

    @Test
    fun rejectsInvalidSourceRequestsBeforeAllocating() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val description = sourceDescription()
        val source = description.code as ShaderSource
        val invalidDescriptions = listOf(
            description.copy(entryPoint = "other"),
            description.copy(entryPoint = "main\u0000other"),
            description.copy(code = source.copy(text = "${source.text}\u0000")),
        )

        for (invalid in invalidDescriptions) {
            assertFailsWith<IllegalArgumentException> { device.compile(invalid) }
        }
        assertTrue(functions.calls.isEmpty())
    }

    @Test
    fun rejectsBinaryWhenTheAdapterDoesNotProvideSpirV() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val binary = ShaderBinary.copyOf(ByteBuffer.allocate(0), ShaderBinaryFormat.SpirV, "binary")

        assertFailsWith<UnsupportedOperationException> {
            device.compile(ShaderModuleDescription(ShaderStage.Vertex, binary))
        }
        assertTrue(functions.calls.isEmpty())
    }

    @Test
    fun compilationFailureIncludesDiagnosticsAndDeletesOnlyTheNewShader() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val retained = device.compile(sourceDescription())
        functions.compileStatus = false
        val description = sourceDescription(ShaderStage.Fragment)

        val failure = assertFailsWith<ShaderModuleCreationException> { device.compile(description) }

        assertSame(description, failure.description)
        assertEquals("shader diagnostics", failure.diagnostics)
        assertTrue(failure.message.orEmpty().contains(description.label))
        assertEquals(listOf(ShaderName(2)), functions.deletedShaders)
        assertEquals(ShaderName(1), context(device) { retained.requireShader() })
        retained.close()
    }

    @Test
    fun nativeCompilationFailuresReleaseTheAllocatedShader() {
        for (operation in listOf("shaderSource", "compileShader", "getShaderCompileStatus", "getShaderInfoLog")) {
            val functions = RecordingShaderFunctions().apply {
                failureAt = operation
                compileStatus = false
            }
            val device = driverDevice(functions)

            assertSame(functions.failure, assertFailsWith<IllegalStateException>(operation) {
                device.compile(sourceDescription())
            })
            assertEquals(listOf(ShaderName(1)), functions.deletedShaders, operation)
        }
    }

    @Test
    fun failedShaderAllocationDoesNotDeleteZero() {
        val functions = RecordingShaderFunctions().apply { nextShader = 0 }
        val device = driverDevice(functions)

        assertFailsWith<ShaderModuleCreationException> { device.compile(sourceDescription()) }

        assertEquals(listOf("createShader"), functions.calls)
        assertTrue(functions.deletedShaders.isEmpty())
    }

    @Test
    fun linksAndReusesModulesWithoutTakingOwnership() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val vertex = device.compile(sourceDescription())
        val fragment = device.compile(sourceDescription(ShaderStage.Fragment))
        val first = device.link(ShaderStagesDescription(listOf(fragment, vertex), "first"))
        val second = device.link(ShaderStagesDescription(listOf(vertex, fragment), "second"))

        assertEquals(ProgramName(1), context(device) { first.requireProgram() })
        assertEquals(ProgramName(2), context(device) { second.requireProgram() })
        assertEquals(setOf(ShaderName(1), ShaderName(2)), functions.attachments.getValue(ProgramName(1)).toSet())
        first.close()
        first.close()
        assertEquals(listOf(ProgramName(1)), functions.deletedPrograms)
        assertTrue(functions.deletedShaders.isEmpty())
        assertFailsWith<IllegalStateException> { context(device) { first.requireProgram() } }
        assertEquals(ProgramName(2), context(device) { second.requireProgram() })

        second.close()
        fragment.close()
        vertex.close()
    }

    @Test
    fun linkFailureKeepsModulesAvailableForAnotherCombination() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val module = device.compile(sourceDescription())
        val description = ShaderStagesDescription(listOf(module), "link failure")
        functions.linkStatus = false

        val failure = assertFailsWith<ShaderStagesCreationException> { device.link(description) }

        assertSame(description, failure.description)
        assertEquals("program diagnostics", failure.diagnostics)
        assertTrue(failure.message.orEmpty().contains(description.label))
        assertEquals(listOf(ProgramName(1)), functions.deletedPrograms)
        assertTrue(functions.deletedShaders.isEmpty())
        functions.linkStatus = true
        device.link(description).close()
        module.close()
    }

    @Test
    fun nativeLinkFailuresReleaseOnlyTheNewProgram() {
        for (operation in listOf("attachShader", "linkProgram", "getProgramLinkStatus", "getProgramInfoLog")) {
            val functions = RecordingShaderFunctions()
            val device = driverDevice(functions)
            val module = device.compile(sourceDescription())
            functions.failureAt = operation
            functions.linkStatus = false

            assertSame(functions.failure, assertFailsWith<IllegalStateException>(operation) {
                device.link(ShaderStagesDescription(listOf(module), operation))
            })
            assertEquals(listOf(ProgramName(1)), functions.deletedPrograms, operation)
            assertTrue(functions.deletedShaders.isEmpty(), operation)
            module.close()
        }
    }

    @Test
    fun failedProgramAllocationDoesNotDeleteZeroOrTheModules() {
        val functions = RecordingShaderFunctions().apply { nextProgram = 0 }
        val device = driverDevice(functions)
        val module = device.compile(sourceDescription())

        assertFailsWith<ShaderStagesCreationException> {
            device.link(ShaderStagesDescription(listOf(module), "allocation failure"))
        }

        assertTrue(functions.deletedPrograms.isEmpty())
        assertTrue(functions.deletedShaders.isEmpty())
        module.close()
    }

    @Test
    fun rejectsEmptyDuplicateAndUnrecognizedStagesBeforeAllocatingAProgram() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val first = device.compile(sourceDescription())
        val second = device.compile(sourceDescription())
        val unrecognized = object : ShaderModule {
            override val stage = ShaderStage.Fragment
            override val entryPoint = "main"
            override fun close() = terminateOnFailure {}
        }

        for (modules in listOf(emptyList(), listOf(first, first), listOf(first, second), listOf(unrecognized))) {
            assertFailsWith<IllegalArgumentException> {
                device.link(ShaderStagesDescription(modules, "invalid stages"))
            }
        }
        assertFalse("createProgram" in functions.calls)
        second.close()
        first.close()
    }

    @Test
    fun sharedNativeFunctionsDoNotMakeResourcesBelongToTheSameDevice() {
        val functions = RecordingShaderFunctions()
        val firstDevice = driverDevice(functions)
        val secondDevice = driverDevice(functions)
        val module = firstDevice.compile(sourceDescription())

        assertFailsWith<IllegalArgumentException> {
            secondDevice.link(ShaderStagesDescription(listOf(module), "foreign module"))
        }
        assertFalse("createProgram" in functions.calls)
        val stages = firstDevice.link(ShaderStagesDescription(listOf(module), "owned stages"))
        assertFailsWith<IllegalArgumentException> { context(secondDevice) { stages.requireProgram() } }
        stages.close()
        module.close()
    }

    @Test
    fun closedModulesCannotCreateNewStageCombinations() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val module = device.compile(sourceDescription())
        module.close()

        assertFailsWith<IllegalStateException> {
            device.link(ShaderStagesDescription(listOf(module), "closed module"))
        }
        assertFalse("createProgram" in functions.calls)
    }

    @Test
    fun stageDependenciesAreNotChangedByMutatingTheOriginalDescriptionList() {
        val functions = RecordingShaderFunctions()
        val device = driverDevice(functions)
        val module = device.compile(sourceDescription())
        val modules = mutableListOf<ShaderModule>(module)
        val stages = device.link(ShaderStagesDescription(modules, "snapshot"))
        modules.clear()
        module.close()

        assertFailsWith<IllegalStateException> { context(device) { stages.requireProgram() } }

        stages.close()
        assertEquals(listOf(ProgramName(1)), functions.deletedPrograms)
    }

    @Test
    fun accessValidationPrecedesNativeCallsAndResourceUse() {
        val functions = RecordingShaderFunctions()
        var accessible = false
        val device = driverDevice(functions)
        functions.validateAccess = { check(accessible) { "Wrong device access" } }

        assertFailsWith<IllegalStateException> { device.compile(sourceDescription()) }
        assertTrue(functions.calls.isEmpty())
        accessible = true
        val module = device.compile(sourceDescription())
        val stages = device.link(ShaderStagesDescription(listOf(module), "access checks"))
        val callsBefore = functions.calls.toList()
        accessible = false

        assertFailsWith<IllegalStateException> { context(device) { module.requireShader() } }
        assertFailsWith<IllegalStateException> { context(device) { stages.requireProgram() } }
        assertFailsWith<IllegalStateException> { device.link(ShaderStagesDescription(listOf(module), "invalid access")) }
        assertEquals(callsBefore, functions.calls)

        accessible = true
        stages.close()
        module.close()
    }

    @Test
    fun heapBinaryIsConsumedInsideTheFrameWithoutChangingTheCallerSelection() {
        val functions = RecordingShaderFunctions()
        val stack = MemoryStack(64)
        val initialPointer = stack.frame { reserve(0, 1) }
        var loaded: List<Byte>? = null
        var selectedEntry: String? = null
        functions.spirVShaders = object : OpenGLSpirVShaderFunctions {
            override fun shaderBinary(shader: ShaderName, binary: ByteBuffer) {
                assertTrue(binary.isDirect)
                assertEquals(0, binary.position())
                assertEquals(4, binary.remaining())
                assertEquals(ShaderBinaryFormat.SpirV.byteOrder, binary.order())
                // Inspect within the call; a borrowed native input must not escape it.
                loaded = (0 until 4).map(binary::get)
                stack.frame {
                    val end = reserve(0, 1)
                    assertEquals(initialPointer.rawValue + 4, end.rawValue)
                    assertEquals(loaded, (0 until 4).map(asByteBuffer(initialPointer, 4)::get))
                }
            }
            override fun specializeShader(shader: ShaderName, entryPoint: String) {
                selectedEntry = entryPoint
                // Loading, not specialization, owns the client's temporary input lifetime.
                stack.frame {
                    assertEquals(initialPointer, reserve(0, 1))
                    reserveBuffer(64).putInt(0, 99)
                }
            }
        }
        val device = driverDevice(functions, stack)
        // Transport fixture, not an executable SPIR-V module; only the test double consumes it.
        val original = ByteBuffer.wrap(byteArrayOf(99, 3, 2, 35, 7, 98))
        original.position(1)
        original.mark()
        original.limit(5)
        val binary = ShaderBinary.viewOf(original, ShaderBinaryFormat.SpirV, "transport fixture")
        val module = device.compile(ShaderModuleDescription(ShaderStage.Vertex, binary, "selectedEntry"))
        try {
            assertEquals(listOf<Byte>(3, 2, 35, 7), loaded)
            assertEquals("selectedEntry", selectedEntry)
            assertEquals(1, original.position())
            assertEquals(5, original.limit())
            original.reset()
            assertTrue(functions.sources.isEmpty())
            assertFalse("compileShader" in functions.calls)
            assertEquals(initialPointer, stack.frame { reserve(0, 1) })
        } finally {
            module.close()
        }
    }

    @Test
    fun directBinaryNeedsNoScratchReservationEvenWhenTheStackIsFull() {
        val functions = RecordingShaderFunctions()
        val scratch = MemoryStack(1)
        val sourceStack = MemoryStack(16)
        var loads = 0
        sourceStack.frame {
            val source = reserveBuffer(8, 4)
            source.putInt(0, 0x01020304)
            source.putInt(4, 0x05060708)
            val description = ShaderModuleDescription(ShaderStage.Vertex,
                ShaderBinary.viewOf(source, ShaderBinaryFormat.SpirV, "direct transport fixture"))
            scratch.frame {
                reserve(1, 1)
                val before = reserve(0, 1)
                functions.spirVShaders = binaryFunctions { binary ->
                    assertTrue(binary.isDirect)
                    assertTrue(binary.isReadOnly) // The existing ShaderBinary view is passed through.
                    assertEquals((0 until 8).map(source::get), (0 until 8).map(binary::get))
                    assertEquals(before, reserve(0, 1))
                    loads++
                }
                driverDevice(functions, scratch).compile(description).close()
                assertEquals(before, reserve(0, 1))
            }
        }
        assertEquals(1, loads)
    }

    @Test
    fun oversizedHeapBinaryUsesIndependentStorageWithoutConsumingTheStack() {
        val functions = RecordingShaderFunctions()
        val stack = MemoryStack(16)
        val source = ByteArray(70_000) { ((it * 17 + 3) and 255).toByte() }
        var loads = 0
        stack.frame {
            val sentinel = reserve(4, 4)
            storeInt(sentinel, 0x12345678)
            val before = reserve(0, 1)
            functions.spirVShaders = binaryFunctions { binary ->
                assertTrue(binary.isDirect)
                assertEquals(source.size, binary.remaining())
                assertContentEquals(source, ByteArray(binary.remaining()).also { binary.duplicate().get(it) })
                assertEquals(before, reserve(0, 1))
                assertEquals(0x12345678, loadInt(sentinel))
                loads++
            }
            driverDevice(functions, stack).compile(ShaderModuleDescription(ShaderStage.Vertex,
                ShaderBinary.viewOf(ByteBuffer.wrap(source), ShaderBinaryFormat.SpirV, "large transport fixture"))).close()
            assertEquals(before, reserve(0, 1))
        }
        assertEquals(1, loads)
    }

    @Test
    fun nestedBinaryLoadingDoesNotOverwriteAnOuterTemporary() {
        val functions = RecordingShaderFunctions()
        val stack = MemoryStack(64)
        val device = driverDevice(functions, stack)
        var nested = false
        var loads = 0
        val outerBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val innerBytes = byteArrayOf(17, 18, 19, 20, 21, 22, 23, 24)
        functions.spirVShaders = binaryFunctions { binary ->
            loads++
            if (nested) {
                assertEquals(innerBytes.toList(), (0 until 8).map(binary::get))
            } else {
                assertEquals(outerBytes.toList(), (0 until 8).map(binary::get))
                nested = true
                try {
                    device.compile(ShaderModuleDescription(ShaderStage.Fragment,
                        ShaderBinary.viewOf(ByteBuffer.wrap(innerBytes), ShaderBinaryFormat.SpirV, "inner fixture"))).close()
                } finally {
                    nested = false
                }
                assertEquals(outerBytes.toList(), (0 until 8).map(binary::get))
            }
        }
        val before = stack.frame { reserve(0, 1) }
        device.compile(ShaderModuleDescription(ShaderStage.Vertex,
            ShaderBinary.viewOf(ByteBuffer.wrap(outerBytes), ShaderBinaryFormat.SpirV, "outer fixture"))).close()
        assertEquals(2, loads)
        assertEquals(before, stack.frame { reserve(0, 1) })
    }

    @Test
    fun loadingAndSpecializationFailuresRestoreScratchAndReleaseTheNewShader() {
        for (failDuringLoad in listOf(true, false)) {
            val functions = RecordingShaderFunctions()
            val stack = MemoryStack(32)
            val failure = IllegalStateException("binary failure")
            functions.spirVShaders = object : OpenGLSpirVShaderFunctions {
                override fun shaderBinary(shader: ShaderName, binary: ByteBuffer) {
                    if (failDuringLoad) throw failure
                }
                override fun specializeShader(shader: ShaderName, entryPoint: String) {
                    throw failure
                }
            }
            val before = stack.frame { reserve(0, 1) }
            val device = driverDevice(functions, stack)
            assertSame(failure, assertFailsWith<IllegalStateException> {
                device.compile(ShaderModuleDescription(ShaderStage.Vertex,
                    ShaderBinary.viewOf(ByteBuffer.allocate(16), ShaderBinaryFormat.SpirV, "failure fixture")))
            })
            assertEquals(before, stack.frame { reserve(0, 1) })
            assertEquals(listOf(ShaderName(1)), functions.deletedShaders)
        }
    }

    @Test
    fun mixedSourceAndBinaryModulesAreRejectedBeforeLinkAllocation() {
        val functions = RecordingShaderFunctions()
        functions.spirVShaders = object : OpenGLSpirVShaderFunctions {
            override fun shaderBinary(shader: ShaderName, binary: ByteBuffer) {}
            override fun specializeShader(shader: ShaderName, entryPoint: String) {}
        }
        val device = driverDevice(functions)
        val source = device.compile(sourceDescription())
        // No driver consumes these bytes; this case checks the representation-combination rule.
        val binary = device.compile(ShaderModuleDescription(ShaderStage.Fragment,
            ShaderBinary.viewOf(ByteBuffer.allocate(4), ShaderBinaryFormat.SpirV, "transport fixture")))
        try {
            assertFailsWith<IllegalArgumentException> {
                device.link(ShaderStagesDescription(listOf(source, binary), "mixed"))
            }
            assertFalse("createProgram" in functions.calls)
        } finally {
            binary.close()
            source.close()
        }
    }

    @Test
    fun nestedDeviceContextsDoNotRetargetTheOuterResources() {
        val firstFunctions = RecordingShaderFunctions()
        val secondFunctions = RecordingShaderFunctions()
        val firstDevice = driverDevice(firstFunctions)
        val secondDevice = driverDevice(secondFunctions)

        context(firstDevice) {
            val outer = OpenGLShaderCompiler.compile(sourceDescription())
            outer.use { outer ->
                context(secondDevice) {
                    val inner = OpenGLShaderCompiler.compile(sourceDescription(ShaderStage.Fragment))
                    inner.use { inner ->
                        assertEquals(ShaderName(1), context(secondDevice) { inner.requireShader() })
                        assertFailsWith<IllegalArgumentException> {
                            OpenGLShaderCompiler.link(ShaderStagesDescription(listOf(outer), "foreign"))
                        }
                        assertFalse("createProgram" in secondFunctions.calls)
                    }
                }
                // The inner context ended; the same stateless compiler now uses the outer device.
                OpenGLShaderCompiler.link(ShaderStagesDescription(listOf(outer), "outer")).close()
            }
        }
        assertEquals(listOf(ShaderType.Vertex), firstFunctions.shaderTypes)
        assertEquals(listOf(ShaderType.Fragment), secondFunctions.shaderTypes)
        assertEquals(listOf(ProgramName(1)), firstFunctions.deletedPrograms)
        assertTrue(secondFunctions.deletedPrograms.isEmpty())
    }

    @Test
    fun deviceEntryUsesItsOwnFunctionsAndScratchInsteadOfUnrelatedAmbientValues() {
        val functions = RecordingShaderFunctions()
        val scratch = MemoryStack(32)
        val device = driverDevice(functions, scratch)
        val unrelatedFunctions = unexpectedOpenGLCalls()
        val unrelatedStack = MemoryStack(1)
        val initialPointer = scratch.frame { reserve(0, 1) }
        var loads = 0
        functions.spirVShaders = binaryFunctions { binary ->
            assertEquals(listOf<Byte>(1, 2, 3, 4), (0 until 4).map(binary::get))
            scratch.frame {
                assertEquals(initialPointer.rawValue + 4, reserve(0, 1).rawValue)
                assertEquals(listOf<Byte>(1, 2, 3, 4), (0 until 4).map(asByteBuffer(initialPointer, 4)::get))
            }
            loads++
        }
        unrelatedStack.frame {
            val sentinel = reserveBuffer(1)
            sentinel.put(0, 91)
            context(unrelatedFunctions, unrelatedStack) {
                context(device) {
                    OpenGLShaderCompiler.compile(ShaderModuleDescription(ShaderStage.Vertex,
                        ShaderBinary.viewOf(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)),
                            ShaderBinaryFormat.SpirV, "context transport fixture"))).close()
                }
            }
            assertEquals(91.toByte(), sentinel.get(0))
        }
        assertEquals(1, loads)
        assertEquals(initialPointer, scratch.frame { reserve(0, 1) })
    }

    @Test
    fun closingUnderAnotherKotlinContextStillUsesTheCreatingDevice() {
        val firstFunctions = RecordingShaderFunctions()
        val secondFunctions = RecordingShaderFunctions()
        val firstDevice = driverDevice(firstFunctions)
        val secondDevice = driverDevice(secondFunctions)
        val module = context(firstDevice) { OpenGLShaderCompiler.compile(sourceDescription()) }
        val stages = context(firstDevice) {
            OpenGLShaderCompiler.link(ShaderStagesDescription(listOf(module), "owned"))
        }

        // This changes only Kotlin's supplied value, not which native context is current.
        context(secondDevice) {
            stages.close()
            module.close()
        }
        assertEquals(listOf(ShaderName(1)), firstFunctions.deletedShaders)
        assertEquals(listOf(ProgramName(1)), firstFunctions.deletedPrograms)
        assertTrue(secondFunctions.deletedShaders.isEmpty())
        assertTrue(secondFunctions.deletedPrograms.isEmpty())
    }

    private fun sourceDescription(stage: ShaderStage = ShaderStage.Vertex): ShaderModuleDescription {
        val text = when (stage) {
            ShaderStage.Vertex -> "#version 120\nvoid main() { gl_Position = vec4(0.0, 0.0, 0.0, 1.0); }"
            ShaderStage.Fragment -> "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }"
        }
        return ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, text, "source"), label = "$stage module")
    }
}

private class RecordingShaderFunctions : OpenGLFunctions by unexpectedOpenGLCalls() {
    var validateAccess: () -> Unit = {}
    override fun checkCurrentContext() = validateAccess()
    override fun getError(): Int = 0
    override val framebuffers: OpenGLFramebufferFunctions = Proxy.newProxyInstance(
        OpenGLFramebufferFunctions::class.java.classLoader,
        arrayOf(OpenGLFramebufferFunctions::class.java),
    ) { _, method, _ -> throw AssertionError("Unexpected framebuffer call: ${method.name}") } as OpenGLFramebufferFunctions
    override var spirVShaders: OpenGLSpirVShaderFunctions? = null
    override val uniformBuffers: OpenGLUniformBufferFunctions? = null
    override val supportsLegacyPixelTransfer: Boolean = true
    override fun getString(parameter: Int): String {
        assertEquals(0x8B8C, parameter)
        return "1.20"
    }

    override fun getProgramInteger(program: ProgramName, parameter: Int): Int {
        record("getProgramInteger")
        return when (parameter) {
            0x8B86, 0x8B89 -> 0 // This lifecycle fixture models all artifact inputs and uniforms optimized out.
            else -> error("Unexpected program query: $parameter")
        }
    }
    val calls = mutableListOf<String>()
    val sources = mutableListOf<String>()
    val shaderTypes = mutableListOf<ShaderType>()
    val deletedShaders = mutableListOf<ShaderName>()
    val deletedPrograms = mutableListOf<ProgramName>()
    val attributeBindings = mutableMapOf<ProgramName, MutableList<Pair<Int, String>>>()
    val attachments = mutableMapOf<ProgramName, MutableList<ShaderName>>()
    val failure = IllegalStateException("Native operation failed")
    var failureAt: String? = null
    var compileStatus = true
    var linkStatus = true
    var nextShader = 1
    var nextProgram = 1

    private fun record(operation: String) {
        calls += operation
        if (failureAt == operation) throw failure
    }

    override fun createShader(type: ShaderType): ShaderName {
        record("createShader")
        shaderTypes += type
        return ShaderName(nextShader++)
    }

    override fun shaderSource(shader: ShaderName, source: CharSequence) {
        record("shaderSource")
        sources += source.toString()
    }

    override fun compileShader(shader: ShaderName) = record("compileShader")

    override fun getShaderCompileStatus(shader: ShaderName): Boolean {
        record("getShaderCompileStatus")
        return compileStatus
    }

    override fun getShaderInfoLog(shader: ShaderName): String {
        record("getShaderInfoLog")
        return "shader diagnostics"
    }

    override fun deleteShader(shader: ShaderName) {
        record("deleteShader")
        deletedShaders += shader
    }

    override fun createProgram(): ProgramName {
        record("createProgram")
        return ProgramName(nextProgram++)
    }

    override fun attachShader(program: ProgramName, shader: ShaderName) {
        record("attachShader")
        attachments.getOrPut(program) { mutableListOf() } += shader
    }

    override fun bindVertexAttributeLocation(program: ProgramName, index: VertexAttributeIndex, name: CharSequence) {
        record("bindVertexAttributeLocation")
        attributeBindings.getOrPut(program) { mutableListOf() } += index.value to name.toString()
    }

    override fun linkProgram(program: ProgramName) = record("linkProgram")

    override fun getProgramLinkStatus(program: ProgramName): Boolean {
        record("getProgramLinkStatus")
        return linkStatus
    }

    override fun getProgramInfoLog(program: ProgramName): String {
        record("getProgramInfoLog")
        return "program diagnostics"
    }

    override fun deleteProgram(program: ProgramName) {
        record("deleteProgram")
        deletedPrograms += program
    }
}

private fun unexpectedOpenGLCalls(): OpenGLFunctions = Proxy.newProxyInstance(
    OpenGLFunctions::class.java.classLoader,
    arrayOf(OpenGLFunctions::class.java),
) { _, method, _ -> throw AssertionError("Unexpected OpenGL call: ${method.name}") } as OpenGLFunctions

private fun driverDevice(
    functions: RecordingShaderFunctions,
    memoryStack: MemoryStack = MemoryStack(),
): OpenGLGraphicsDevice = OpenGLGraphicsDevice(functions, memoryStack, canonicalShaderCompiler = RecordingCompiler())

// These test conveniences enter the real contextual API; no compiler instance is created.
private fun OpenGLGraphicsDevice.compile(description: ShaderModuleDescription): OpenGLShaderModule =
    context(this) { OpenGLShaderCompiler.compile(description) }

private fun OpenGLGraphicsDevice.link(description: ShaderStagesDescription): OpenGLShaderStages =
    context(this) { OpenGLShaderCompiler.link(description) }

private fun binaryFunctions(load: (ByteBuffer) -> Unit): OpenGLSpirVShaderFunctions =
    object : OpenGLSpirVShaderFunctions {
        override fun shaderBinary(shader: ShaderName, binary: ByteBuffer) = load(binary)
        override fun specializeShader(shader: ShaderName, entryPoint: String) = Unit
    }
