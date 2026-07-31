/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import heckerpowered.render.opengl.*
import heckerpowered.render.opengl.function.OpenGLUniformBufferFunctions
import org.lwjgl.opengl.*
import java.nio.ByteBuffer

internal class Lwjgl2UniformBufferFunctions private constructor(
    private val entryPoints: Lwjgl2UniformBufferEntryPoints,
    private val bufferEntryPoints: Lwjgl2BufferEntryPoints,
) : OpenGLUniformBufferFunctions {
    override val maximumBindings: Int
        get() = GL11.glGetInteger(GL31.GL_MAX_UNIFORM_BUFFER_BINDINGS)

    override fun getBoundUniformBuffer(): BufferName {
        return BufferName(GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING))
    }

    override fun bindUniformBuffer(buffer: BufferName) {
        bufferEntryPoints.bindBuffer(GL31.GL_UNIFORM_BUFFER, buffer.value)
    }

    override fun uniformBufferData(sizeBytes: Long, usage: BufferUsage) {
        bufferEntryPoints.bufferData(GL31.GL_UNIFORM_BUFFER, sizeBytes, usage.toOpenGL())
    }

    override fun uniformBufferData(data: ByteBuffer, usage: BufferUsage) {
        bufferEntryPoints.bufferData(GL31.GL_UNIFORM_BUFFER, data, usage.toOpenGL())
    }

    override fun uniformBufferSubData(offsetBytes: Long, data: ByteBuffer) {
        bufferEntryPoints.bufferSubData(GL31.GL_UNIFORM_BUFFER, offsetBytes, data)
    }

    override fun getUniformBlockIndex(program: ProgramName, name: CharSequence): UniformBlockIndex {
        val index = when (entryPoints) {
            Lwjgl2UniformBufferEntryPoints.Core -> GL31.glGetUniformBlockIndex(program.value, name)
            Lwjgl2UniformBufferEntryPoints.ARB -> ARBUniformBufferObject.glGetUniformBlockIndex(program.value, name)
        }
        return UniformBlockIndex(index)
    }

    override fun bindUniformBlock(program: ProgramName, block: UniformBlockIndex, binding: UniformBufferBindingIndex) =
        when (entryPoints) {
            Lwjgl2UniformBufferEntryPoints.Core -> GL31.glUniformBlockBinding(program.value, block.value, binding.value)
            Lwjgl2UniformBufferEntryPoints.ARB -> ARBUniformBufferObject.glUniformBlockBinding(program.value, block.value, binding.value)
        }

    override fun getBoundUniformBuffer(binding: UniformBufferBindingIndex): BufferName {
        val name = when (entryPoints) {
            Lwjgl2UniformBufferEntryPoints.Core -> GL30.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING, binding.value)
            Lwjgl2UniformBufferEntryPoints.ARB -> ARBUniformBufferObject.glGetInteger(ARBUniformBufferObject.GL_UNIFORM_BUFFER_BINDING, binding.value)
        }
        return BufferName(name)
    }

    override fun bindUniformBuffer(binding: UniformBufferBindingIndex, buffer: BufferName) = when (entryPoints) {
        Lwjgl2UniformBufferEntryPoints.Core -> GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding.value, buffer.value)
        Lwjgl2UniformBufferEntryPoints.ARB -> ARBUniformBufferObject.glBindBufferBase(ARBUniformBufferObject.GL_UNIFORM_BUFFER, binding.value, buffer.value)
    }

    companion object {
        fun create(capabilities: ContextCapabilities, bufferEntryPoints: Lwjgl2BufferEntryPoints): OpenGLUniformBufferFunctions? {
            val entryPoints = when {
                capabilities.OpenGL31 -> Lwjgl2UniformBufferEntryPoints.Core
                capabilities.GL_ARB_uniform_buffer_object -> Lwjgl2UniformBufferEntryPoints.ARB
                else -> return null
            }
            return Lwjgl2UniformBufferFunctions(entryPoints, bufferEntryPoints)
        }
    }
}
