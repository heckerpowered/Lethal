/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.BufferName
import heckerpowered.render.opengl.BufferTarget
import heckerpowered.render.opengl.BufferUsage
import java.nio.ByteBuffer

/**
 * Allocates and updates buffer-object storage.
 *
 * Adapters may normalize `ARB_vertex_buffer_object` entry points.
 */
interface OpenGLBufferFunctions {
    fun createBuffer(): BufferName
    fun getBoundBuffer(target: BufferTarget): BufferName
    fun bindBuffer(target: BufferTarget, buffer: BufferName)
    fun bufferData(target: BufferTarget, sizeBytes: Long, usage: BufferUsage)
    fun bufferData(target: BufferTarget, data: ByteBuffer, usage: BufferUsage)
    fun bufferSubData(target: BufferTarget, offsetBytes: Long, data: ByteBuffer)
    fun deleteBuffer(buffer: BufferName)
}