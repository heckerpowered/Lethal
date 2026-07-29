/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.memory.NativeAddress

/**
 * Bridges the few JVM wrapper signatures that differ between OpenGL bindings.
 *
 * OpenGL operations whose JVM signatures are stable remain direct driver calls in the RHI backend.
 */
interface OpenGLBinding {
    fun queryContextCapabilities(): OpenGLContextCapabilities

    fun getFloat(parameter: Int, outputAddress: NativeAddress)

    fun getInteger(parameter: Int, outputAddress: NativeAddress)

    fun getIndexedInteger(parameter: Int, index: Int): Int

    fun uniformMatrix4(location: Int, transpose: Boolean, valuesAddress: NativeAddress)

    fun bufferSubData(target: Int, offsetBytes: Long, sizeBytes: Long, dataAddress: NativeAddress)

    fun bindBufferBase(target: Int, index: Int, buffer: Int)

    fun getUniformBlockIndex(program: Int, name: String): Int

    fun uniformBlockBinding(program: Int, blockIndex: Int, binding: Int)
}
