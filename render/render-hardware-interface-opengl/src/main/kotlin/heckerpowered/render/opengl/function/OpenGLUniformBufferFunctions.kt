/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.*
import java.nio.ByteBuffer

/**
 * Allocates and updates uniform-buffer storage, then assigns linked uniform
 * blocks to indexed binding points.
 *
 * Adapters may normalize `ARB_uniform_buffer_object` entry points. The reported
 * [maximumBindings] belongs to the current context.
 */
interface OpenGLUniformBufferFunctions {
    val maximumBindings: Int

    fun getBoundUniformBuffer(): BufferName
    fun bindUniformBuffer(buffer: BufferName)
    fun uniformBufferData(sizeBytes: Long, usage: BufferUsage)
    fun uniformBufferData(data: ByteBuffer, usage: BufferUsage)
    fun uniformBufferSubData(offsetBytes: Long, data: ByteBuffer)

    fun getUniformBlockIndex(program: ProgramName, name: CharSequence): UniformBlockIndex
    fun bindUniformBlock(program: ProgramName, block: UniformBlockIndex, binding: UniformBufferBindingIndex)
    fun getBoundUniformBuffer(binding: UniformBufferBindingIndex): BufferName
    fun bindUniformBuffer(binding: UniformBufferBindingIndex, buffer: BufferName)
}

context(function: OpenGLUniformBufferFunctions)
fun getBoundUniformBuffer(): BufferName = function.getBoundUniformBuffer()

context(function: OpenGLUniformBufferFunctions)
fun bindUniformBuffer(buffer: BufferName) = function.bindUniformBuffer(buffer)

context(function: OpenGLUniformBufferFunctions)
fun uniformBufferData(sizeBytes: Long, usage: BufferUsage) = function.uniformBufferData(sizeBytes, usage)

context(function: OpenGLUniformBufferFunctions)
fun uniformBufferData(data: ByteBuffer, usage: BufferUsage) = function.uniformBufferData(data, usage)

context(function: OpenGLUniformBufferFunctions)
fun uniformBufferSubData(offsetBytes: Long, data: ByteBuffer) = function.uniformBufferSubData(offsetBytes, data)

context(function: OpenGLUniformBufferFunctions)
fun getUniformBlockIndex(program: ProgramName, name: CharSequence): UniformBlockIndex =
    function.getUniformBlockIndex(program, name)

context(function: OpenGLUniformBufferFunctions)
fun bindUniformBlock(program: ProgramName, block: UniformBlockIndex, binding: UniformBufferBindingIndex) =
    function.bindUniformBlock(program, block, binding)

context(function: OpenGLUniformBufferFunctions)
fun getBoundUniformBuffer(binding: UniformBufferBindingIndex): BufferName =
    function.getBoundUniformBuffer(binding)

context(function: OpenGLUniformBufferFunctions)
fun bindUniformBuffer(binding: UniformBufferBindingIndex, buffer: BufferName) =
    function.bindUniformBuffer(binding, buffer)
