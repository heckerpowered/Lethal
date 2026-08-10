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

    /**
     * Returns the buffer bound to [target].
     *
     * The element-array binding belongs to the currently bound vertex array
     * when vertex array objects are in use.
     */
    fun getBoundBuffer(target: BufferTarget): BufferName

    fun bindBuffer(target: BufferTarget, buffer: BufferName)
    fun bufferData(target: BufferTarget, sizeBytes: Long, usage: BufferUsage)
    fun bufferData(target: BufferTarget, data: ByteBuffer, usage: BufferUsage)
    fun bufferSubData(target: BufferTarget, offsetBytes: Long, data: ByteBuffer)
    fun deleteBuffer(buffer: BufferName)
}

context(function: OpenGLBufferFunctions)
fun createBuffer(): BufferName = function.createBuffer()

context(function: OpenGLBufferFunctions)
fun getBoundBuffer(target: BufferTarget): BufferName = function.getBoundBuffer(target)

context(function: OpenGLBufferFunctions)
fun bindBuffer(target: BufferTarget, buffer: BufferName) = function.bindBuffer(target, buffer)

context(function: OpenGLBufferFunctions)
fun bufferData(target: BufferTarget, sizeBytes: Long, usage: BufferUsage) =
    function.bufferData(target, sizeBytes, usage)

context(function: OpenGLBufferFunctions)
fun bufferData(target: BufferTarget, data: ByteBuffer, usage: BufferUsage) =
    function.bufferData(target, data, usage)

context(function: OpenGLBufferFunctions)
fun bufferSubData(target: BufferTarget, offsetBytes: Long, data: ByteBuffer) =
    function.bufferSubData(target, offsetBytes, data)

context(function: OpenGLBufferFunctions)
fun deleteBuffer(buffer: BufferName) = function.deleteBuffer(buffer)
