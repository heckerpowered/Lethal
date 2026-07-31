/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl3

import heckerpowered.render.opengl.*
import heckerpowered.render.opengl.function.OpenGLUniformBufferFunctions
import org.lwjgl.opengl.*
import java.nio.ByteBuffer

internal class Lwjgl3UniformBufferFunctions private constructor(
    private val entryPoints: Lwjgl3UniformBufferEntryPoints,
    private val bufferEntryPoints: Lwjgl3BufferEntryPoints,
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
            Lwjgl3UniformBufferEntryPoints.Core -> GL31.glGetUniformBlockIndex(program.value, name)
            Lwjgl3UniformBufferEntryPoints.ARB -> ARBUniformBufferObject.glGetUniformBlockIndex(program.value, name)
        }
        return UniformBlockIndex(index)
    }

    override fun bindUniformBlock(program: ProgramName, block: UniformBlockIndex, binding: UniformBufferBindingIndex) =
        when (entryPoints) {
            Lwjgl3UniformBufferEntryPoints.Core -> GL31.glUniformBlockBinding(program.value, block.value, binding.value)
            Lwjgl3UniformBufferEntryPoints.ARB -> ARBUniformBufferObject.glUniformBlockBinding(program.value, block.value, binding.value)
        }

    override fun getBoundUniformBuffer(binding: UniformBufferBindingIndex): BufferName {
        val name = when (entryPoints) {
            Lwjgl3UniformBufferEntryPoints.Core -> GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, binding.value)
            Lwjgl3UniformBufferEntryPoints.ARB -> ARBUniformBufferObject.glGetIntegeri(ARBUniformBufferObject.GL_UNIFORM_BUFFER_BINDING, binding.value)
        }
        return BufferName(name)
    }

    override fun bindUniformBuffer(binding: UniformBufferBindingIndex, buffer: BufferName) = when (entryPoints) {
        Lwjgl3UniformBufferEntryPoints.Core -> GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding.value, buffer.value)
        Lwjgl3UniformBufferEntryPoints.ARB -> ARBUniformBufferObject.glBindBufferBase(ARBUniformBufferObject.GL_UNIFORM_BUFFER, binding.value, buffer.value)
    }

    companion object {
        fun create(capabilities: GLCapabilities, bufferEntryPoints: Lwjgl3BufferEntryPoints): OpenGLUniformBufferFunctions? {
            val entryPoints = when {
                capabilities.OpenGL31 -> Lwjgl3UniformBufferEntryPoints.Core
                capabilities.GL_ARB_uniform_buffer_object -> Lwjgl3UniformBufferEntryPoints.ARB
                else -> return null
            }
            return Lwjgl3UniformBufferFunctions(entryPoints, bufferEntryPoints)
        }
    }
}
