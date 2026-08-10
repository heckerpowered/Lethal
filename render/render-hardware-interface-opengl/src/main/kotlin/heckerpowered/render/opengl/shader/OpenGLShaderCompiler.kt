/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.opengl.ShaderName
import heckerpowered.render.opengl.function.*
import heckerpowered.render.opengl.toOpenGLShaderType
import heckerpowered.render.shader.*
import heckerpowered.render.terminateOnFailure

/**
 * Creates OpenGL shader modules from backend-neutral shader descriptions.
 *
 * This compiler translates the shader representation described by [ShaderModuleDescription]
 * into an OpenGL shader object. GLSL source is compiled directly, while SPIR-V binaries are
 * loaded and specialized through the current context's optional SPIR-V capability.
 *
 * This component is intentionally limited to creation of individual stage-specific shader
 * modules. It does not create or link OpenGL program objects or perform cross-stage validation.
 *
 * The compiler is stateless. All OpenGL operations are provided by the current [OpenGLFunctions]
 * context.
 */
internal object OpenGLShaderCompiler {
    /**
     * Creates an OpenGL shader module described by [description].
     *
     * The description is first checked for requirements that can be determined by this backend
     * without validating the shader code itself. A shader object is then created and either
     * compiled from GLSL source or loaded and specialized from SPIR-V.
     *
     * Compilation or specialization success is determined from the shader object's compile
     * status after the corresponding OpenGL operation completes. Failure diagnostics are
     * retrieved from the shader info log and reported through [ShaderModuleCreationException].
     *
     * If creation fails after the OpenGL shader object has been allocated, the shader object is
     * deleted before the failure is propagated. On success, ownership of the shader object is
     * transferred to the returned [OpenGLShaderModule].
     *
     * SPIR-V binaries supplied by [description] must already have been validated for the OpenGL
     * execution environment. This compiler does not perform SPIR-V validation before submitting the
     * module to OpenGL.
     *
     * @throws IllegalArgumentException if [description] cannot be represented by this OpenGL
     * backend.
     * @throws IllegalStateException if a capability required by [description] is unavailable in
     * the current OpenGL context.
     * @throws ShaderModuleCreationException if OpenGL fails to compile or specialize the shader.
     */
    context(functions: OpenGLFunctions)
    fun compile(description: ShaderModuleDescription): OpenGLShaderModule {
        validate(description)

        val shaderType = description.stage.toOpenGLShaderType()
        val shader = createShader(shaderType)
        check(shader != ShaderName.None) { "OpenGL failed to create a $shaderType shader object" }

        try {
            when (val code = description.code) {
                is ShaderSource -> compileSource(shader, code)
                is ShaderBinary -> context(functions.spirVShaders!!) {
                    specializeSpirV(shader, code, description.entryPoint)
                }
            }

            if (!getShaderCompileStatus(shader)) {
                throw ShaderModuleCreationException(description, getShaderInfoLog(shader))
            }

            return OpenGLShaderModule(functions, shader, description.stage, description.entryPoint)
        } catch (exception: Throwable) {
            terminateOnFailure { deleteShader(shader) }
            throw exception
        }
    }

    /**
     * Loads [source] into [shader] and requests GLSL compilation.
     *
     * This function only performs the compilation operation. Successful return does not indicate
     * that the shader compiled successfully. OpenGL stores the result in the shader object's compile
     * status, which must subsequently be queried through [getShaderCompileStatus].
     *
     * Additional compilation diagnostics can be retrieved through [getShaderInfoLog].
     *
     * See [glCompileShader](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glCompileShader.xhtml).
     */
    context(_: OpenGLShaderFunctions)
    private fun compileSource(shader: ShaderName, source: ShaderSource) {
        shaderSource(shader, source.text)
        compileShader(shader)
    }

    /**
     * Loads [binary] into [shader] and specializes the selected [entryPoint].
     *
     * [binary] must contain a SPIR-V module that has already been validated for the OpenGL
     * execution environment. OpenGL explicitly requires SPIR-V modules to be validated before
     * submission and permits invalid modules to result in undefined behavior.
     *
     * This function only performs the SPIR-V loading and specialization operations. Successful
     * return does not indicate that specialization succeeded. OpenGL stores the result in the
     * shader object's compile status, which must subsequently be queried through
     * [getShaderCompileStatus].
     *
     * Additional specialization diagnostics can be retrieved through [getShaderInfoLog].
     *
     * No specialization constant overrides are supplied, so all specialization constants retain
     * their default values declared by the SPIR-V module.
     *
     * See [ARB_gl_spirv — SPIR-V Modules and Shader Specialization](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_gl_spirv.txt).
     */
    context(_: OpenGLFunctions, _: OpenGLSpirVShaderFunctions)
    private fun specializeSpirV(shader: ShaderName, binary: ShaderBinary, entryPoint: String) {
        shaderBinary(shader, binary.bytes)
        specializeShader(shader, entryPoint)
    }

    /**
     * Validates the OpenGL-facing requirements of [description].
     *
     * This validation is intentionally limited to conditions that can be determined without parsing
     * or validating the shader code itself, including:
     *
     * - whether the shader-code representation is supported by this backend;
     * - whether the current context provides the required SPIR-V entry points;
     * - whether the selected entry point can be represented as the NUL-terminated UTF-8 string
     *   required by OpenGL;
     * - whether source-specific restrictions, such as the GLSL `"main"` entry point, are satisfied.
     *
     * This function does not validate GLSL or SPIR-V contents.
     *
     * GLSL validity is determined by OpenGL compilation. SPIR-V is different: a [ShaderBinary] using
     * [ShaderBinaryFormat.SpirV] must already contain a valid SPIR-V module before it reaches this
     * backend. OpenGL requires submitted SPIR-V modules to have been validated and permits invalid
     * modules to result in undefined behavior.
     *
     * The existence of the selected SPIR-V entry point, compatibility of its execution model with
     * [ShaderModuleDescription.stage], and specialization success are determined by OpenGL during
     * specialization.
     *
     * See [ARB_gl_spirv](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_gl_spirv.txt)
     * and [SPIR-V Unified Specification](https://registry.khronos.org/SPIR-V/specs/unified1/SPIRV.html).
     *
     * @throws IllegalArgumentException if [description] cannot be represented by this OpenGL backend.
     * @throws IllegalStateException if the current OpenGL context lacks a required capability.
     */
    context(functions: OpenGLFunctions)
    private fun validate(description: ShaderModuleDescription) {
        require('\u0000' !in description.entryPoint) { "Shader entry point must not contain a NUL character" }

        when (val code = description.code) {
            is ShaderSource -> {
                require(code.language == ShaderLanguage.Glsl) { "OpenGL does not support shader source language ${code.language}" }
                require(description.entryPoint == "main") { "OpenGL GLSL source shaders must use the \"main\" entry point." }
            }

            is ShaderBinary -> {
                require(code.format == ShaderBinaryFormat.SpirV) { "OpenGL does not support shader binary format ${code.format}" }
                checkNotNull(functions.spirVShaders) { "SPIR-V shader modules are not supported by the current OpenGL context" }
            }
        }
    }
}