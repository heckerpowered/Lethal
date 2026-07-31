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