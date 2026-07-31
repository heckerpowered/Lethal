/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.*
import java.nio.FloatBuffer

/**
 * Creates, links, binds, and updates GLSL shader and program objects.
 *
 * Adapters may provide these semantics through the ARB shader-object, vertex-shader,
 * and fragment-shader extensions when the core entry points are absent.
 */
interface OpenGLShaderFunctions {
    fun createShader(type: ShaderType): ShaderName
    fun shaderSource(shader: ShaderName, source: CharSequence)
    fun compileShader(shader: ShaderName)
    fun getShaderCompileStatus(shader: ShaderName): Boolean
    fun getShaderInfoLog(shader: ShaderName): String
    fun deleteShader(shader: ShaderName)

    fun createProgram(): ProgramName
    fun attachShader(program: ProgramName, shader: ShaderName)
    fun bindVertexAttributeLocation(program: ProgramName, index: VertexAttributeIndex, name: CharSequence)
    fun linkProgram(program: ProgramName)
    fun getProgramLinkStatus(program: ProgramName): Boolean
    fun getProgramInfoLog(program: ProgramName): String
    fun useProgram(program: ProgramName)
    fun deleteProgram(program: ProgramName)

    fun getUniformLocation(program: ProgramName, name: CharSequence): UniformLocation
    fun uniformInt(location: UniformLocation, value: Int)
    fun uniformFloat(location: UniformLocation, value: Float)
    fun uniformFloat2(location: UniformLocation, x: Float, y: Float)
    fun uniformFloat3(location: UniformLocation, x: Float, y: Float, z: Float)
    fun uniformFloat4(location: UniformLocation, x: Float, y: Float, z: Float, w: Float)
    fun uniformMatrix4(location: UniformLocation, transpose: Boolean, value: FloatBuffer)
}