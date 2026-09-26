/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.ShaderName
import java.nio.ByteBuffer

/**
 * Loads and specializes SPIR-V modules for OpenGL shader objects.
 *
 * SPIR-V shader consumption entered core OpenGL in 4.6 and may be supplied on
 * older contexts through equivalent ARB entry points. Specialization uses the
 * module's default values for all specialization constants.
 */
interface OpenGLSpirVShaderFunctions {
    /**
     * Loads the selected bytes into the shader before returning.
     *
     * The caller supplies a direct buffer and may reuse its storage immediately after this call.
     * Implementations must not retain the buffer for deferred loading; later specialization uses
     * the shader object's loaded module, not this client-memory pointer.
     */
    fun shaderBinary(shader: ShaderName, binary: ByteBuffer)

    fun specializeShader(shader: ShaderName, entryPoint: String)
}

context(function: OpenGLSpirVShaderFunctions)
fun shaderBinary(shader: ShaderName, binary: ByteBuffer) = function.shaderBinary(shader, binary)

context(function: OpenGLSpirVShaderFunctions)
fun specializeShader(shader: ShaderName, entryPoint: String) = function.specializeShader(shader, entryPoint)
