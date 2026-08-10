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

context(function: OpenGLShaderFunctions)
fun createShader(type: ShaderType): ShaderName = function.createShader(type)

context(function: OpenGLShaderFunctions)
fun shaderSource(shader: ShaderName, source: CharSequence) = function.shaderSource(shader, source)

context(function: OpenGLShaderFunctions)
fun compileShader(shader: ShaderName) = function.compileShader(shader)

context(function: OpenGLShaderFunctions)
fun getShaderCompileStatus(shader: ShaderName): Boolean = function.getShaderCompileStatus(shader)

context(function: OpenGLShaderFunctions)
fun getShaderInfoLog(shader: ShaderName): String = function.getShaderInfoLog(shader)

context(function: OpenGLShaderFunctions)
fun deleteShader(shader: ShaderName) = function.deleteShader(shader)

context(function: OpenGLShaderFunctions)
fun createProgram(): ProgramName = function.createProgram()

context(function: OpenGLShaderFunctions)
fun attachShader(program: ProgramName, shader: ShaderName) = function.attachShader(program, shader)

context(function: OpenGLShaderFunctions)
fun bindVertexAttributeLocation(program: ProgramName, index: VertexAttributeIndex, name: CharSequence) =
    function.bindVertexAttributeLocation(program, index, name)

context(function: OpenGLShaderFunctions)
fun linkProgram(program: ProgramName) = function.linkProgram(program)

context(function: OpenGLShaderFunctions)
fun getProgramLinkStatus(program: ProgramName): Boolean = function.getProgramLinkStatus(program)

context(function: OpenGLShaderFunctions)
fun getProgramInfoLog(program: ProgramName): String = function.getProgramInfoLog(program)

context(function: OpenGLShaderFunctions)
fun useProgram(program: ProgramName) = function.useProgram(program)

context(function: OpenGLShaderFunctions)
fun deleteProgram(program: ProgramName) = function.deleteProgram(program)

context(function: OpenGLShaderFunctions)
fun getUniformLocation(program: ProgramName, name: CharSequence): UniformLocation =
    function.getUniformLocation(program, name)

context(function: OpenGLShaderFunctions)
fun uniformInt(location: UniformLocation, value: Int) = function.uniformInt(location, value)

context(function: OpenGLShaderFunctions)
fun uniformFloat(location: UniformLocation, value: Float) = function.uniformFloat(location, value)

context(function: OpenGLShaderFunctions)
fun uniformFloat2(location: UniformLocation, x: Float, y: Float) = function.uniformFloat2(location, x, y)

context(function: OpenGLShaderFunctions)
fun uniformFloat3(location: UniformLocation, x: Float, y: Float, z: Float) =
    function.uniformFloat3(location, x, y, z)

context(function: OpenGLShaderFunctions)
fun uniformFloat4(location: UniformLocation, x: Float, y: Float, z: Float, w: Float) =
    function.uniformFloat4(location, x, y, z, w)

context(function: OpenGLShaderFunctions)
fun uniformMatrix4(location: UniformLocation, transpose: Boolean, value: FloatBuffer) =
    function.uniformMatrix4(location, transpose, value)
