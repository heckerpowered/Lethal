/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.opengl.OpenGLGraphicsDevice
import heckerpowered.render.opengl.ProgramName
import heckerpowered.render.opengl.ShaderName
import heckerpowered.render.opengl.function.*
import heckerpowered.render.opengl.toOpenGLShaderType
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure
import java.nio.ByteBuffer

/**
 * Compiles code already prepared for OpenGL and links its shader modules into native programs.
 *
 * GLSL source goes directly to the driver. An OpenGL-compatible SPIR-V binary can instead use
 * the optional binary-loading entry points. This component does not run external tools, translate
 * Vulkan resource declarations, rewrite coordinates, or infer a pipeline layout. Those operations
 * need a separate, explicit source-preparation contract before a drawing backend can use them.
 *
 * The calling device supplies the function table, scratch stack, and resource identity together.
 * The compiler retains none of them. Modules and linked programs retain their creating device;
 * using the same function table from another device does not transfer those resources to it.
 * A Kotlin context supplies dependencies but does not make a native GL context current.
 *
 * Heap-backed binary input is consumed inside a frame of that device's existing memory stack.
 * Creating a module or stage combination does not bind a program or execute drawing.
 */
internal object OpenGLShaderCompiler {
    /**
     * Creates one shader module without taking ownership of the input source storage.
     *
     * Driver compilation establishes source validity. SPIR-V must already have been validated
     * for the OpenGL execution environment; a binary format tag alone does not establish that.
     * Failed creation releases its native name. Success does not establish pipeline compatibility.
     */
    context(device: OpenGLGraphicsDevice)
    fun compile(description: ShaderModuleDescription): OpenGLShaderModule = context(device.functions, device.memoryStack) {
        device.checkAccess()
        validate(description)
        val shader = createShader(description.stage.toOpenGLShaderType())
        if (shader == ShaderName.None) {
            throw ShaderModuleCreationException(description, "OpenGL did not allocate a shader object")
        }

        // The native name stays here until the wrapper exists. finally covers Error exits too,
        // without catching the creation failure or treating failed cleanup as recoverable.
        var transferred = false
        try {
            compileCode(shader, description)
            if (!getShaderCompileStatus(shader)) {
                throw ShaderModuleCreationException(description, getShaderInfoLog(shader))
            }
            val module = OpenGLShaderModule(device, shader, description)
            transferred = true
            module
        } finally {
            if (!transferred) terminateOnFailure { deleteShader(shader) }
        }
    }

    /**
     * Links modules without binding a program or taking ownership of the source modules.
     * A link failure releases only the new program. Layout reflection remains a separate operation.
     */
    context(device: OpenGLGraphicsDevice)
    fun link(description: ShaderStagesDescription): OpenGLShaderStages = context(device.functions) {
        device.checkAccess()
        val modules = requireModules(description)
        val program = createProgram()
        if (program == ProgramName.None) {
            throw ShaderStagesCreationException(description, "OpenGL did not allocate a program object")
        }

        // Linking and result construction can both fail; retain the new name until both finish.
        var transferred = false
        try {
            for (module in modules) attachShader(program, module.requireShader())
            linkProgram(program)
            if (!getProgramLinkStatus(program)) {
                throw ShaderStagesCreationException(description, getProgramInfoLog(program))
            }
            val stages = OpenGLShaderStages(device, program, modules, description.label)
            transferred = true
            stages
        } finally {
            if (!transferred) terminateOnFailure { deleteProgram(program) }
        }
    }

    context(_: OpenGLGraphicsDevice)
    private fun requireModules(description: ShaderStagesDescription): List<OpenGLShaderModule> {
        val modules = description.modules.map { module ->
            require(module is OpenGLShaderModule) { "Shader stages '${description.label}' contain a foreign module" }
            module.requireShader()
            module
        }
        require(modules.isNotEmpty()) { "Shader stages '${description.label}' require at least one module" }
        val stagesAreUnique = modules.map { it.stage }.distinct().size == modules.size
        val representationsMatch = modules.all { it.isSpirV == modules.first().isSpirV }
        require(stagesAreUnique) { "Shader stages '${description.label}' contain a repeated stage" }
        require(representationsMatch) { "An OpenGL program cannot mix GLSL-source and SPIR-V shader modules" }
        return modules
    }

    context(_: OpenGLFunctions)
    private fun validate(description: ShaderModuleDescription) {
        val entryPointIsValid = description.entryPoint.isNotEmpty() && '\u0000' !in description.entryPoint
        require(entryPointIsValid) { "Shader entry point must be nonempty and must not contain a NUL character" }
        when (val code = description.code) {
            is ShaderSource -> {
                require(code.language == ShaderLanguage.Glsl) { "The native OpenGL compiler requires GLSL source" }
                require(description.entryPoint == "main") { "OpenGL GLSL source uses the main entry point" }
                require('\u0000' !in code.text) { "Shader source must not contain a NUL character" }
            }

            is ShaderBinary -> {
                require(code.format == ShaderBinaryFormat.SpirV) { "Unsupported OpenGL shader binary format" }
                if (spirVShaders == null) {
                    throw UnsupportedOperationException("The current adapter does not provide OpenGL SPIR-V loading")
                }
            }
        }
    }

    context(_: OpenGLFunctions, _: MemoryStack)
    private fun compileCode(shader: ShaderName, description: ShaderModuleDescription) {
        when (val code = description.code) {
            is ShaderSource -> compileSource(shader, code)
            is ShaderBinary -> context(checkNotNull(spirVShaders)) {
                loadBinary(shader, code)
                specializeShader(shader, description.entryPoint)
            }
        }
    }

    context(_: OpenGLShaderFunctions)
    private fun compileSource(shader: ShaderName, source: ShaderSource) {
        shaderSource(shader, source.text)
        compileShader(shader)
    }

    context(_: OpenGLSpirVShaderFunctions, stack: MemoryStack)
    private fun loadBinary(shader: ShaderName, binary: ShaderBinary) {
        val source = binary.bytes
        if (source.isDirect) {
            shaderBinary(shader, source)
            return
        }
        stack.frame {
            // Large binaries must not become unsupported merely because the scratch stack is full.
            val temporary = (tryReserveBuffer(source.remaining(), Int.SIZE_BYTES)
                ?: ByteBuffer.allocateDirect(source.remaining())).order(binary.format.byteOrder)
            temporary.put(source)
            temporary.flip()
            // glShaderBinary consumes client bytes here; specialization uses the shader object.
            shaderBinary(shader, temporary)
        }
    }
}
