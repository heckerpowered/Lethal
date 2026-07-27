/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import java.nio.*

/**
 * Allocates short-lived direct buffers from a fixed-capacity, thread-local stack of native memory.
 *
 * Every allocation belongs to the current [stackAlloc] block and becomes invalid when that block returns. Capacity
 * exhaustion is reported immediately instead of replacing the backing memory while earlier allocations may still be
 * in use.
 */
class MemoryStack private constructor() {
    private val memory = ByteBuffer.allocateDirect(CAPACITY_BYTES).order(ByteOrder.nativeOrder())
    private val frameOffsets = IntArray(FRAME_CAPACITY)
    private var frameDepth = 0
    private var position = 0

    private fun push() {
        check(frameDepth < frameOffsets.size) { "Memory stack frame overflow: maximum depth=${frameOffsets.size}" }
        frameOffsets[frameDepth++] = position
    }

    private fun pop() {
        check(frameDepth > 0) { "Memory stack has no frame to pop" }
        position = frameOffsets[--frameDepth]
    }

    fun bytes(byteCount: Int): ByteBuffer = allocate(byteCount, Byte.SIZE_BYTES) { slice().order(NATIVE_ORDER) }
    fun floats(elementCount: Int): FloatBuffer = allocate(Math.multiplyExact(elementCount, Float.SIZE_BYTES), Float.SIZE_BYTES,ByteBuffer::asFloatBuffer)
    fun ints(elementCount: Int): IntBuffer = allocate(Math.multiplyExact(elementCount, Int.SIZE_BYTES), Int.SIZE_BYTES, ByteBuffer::asIntBuffer)

    private inline fun <B : Buffer> allocate(byteCount: Int, alignment: Int, createView: ByteBuffer.() -> B): B {
        require(byteCount >= 0) { "Memory allocation size must be non-negative" }

        val offset = Math.addExact(position, alignment - 1) and -alignment
        val end = Math.addExact(offset, byteCount)

        check(end <= memory.capacity()) { "Memory stack overflow: requested=$byteCount bytes, remaining=${memory.capacity() - offset} bytes, capacity=${memory.capacity()} bytes" }

        position = end
        memory.position(offset).limit(end)

        return try {
            memory.createView()
        } finally {
            memory.clear()
        }
    }

    companion object {
        private val NATIVE_ORDER = ByteOrder.nativeOrder()

        private const val CAPACITY_BYTES = 64 * 1024
        private const val FRAME_CAPACITY = 16

        private val Current = ThreadLocal.withInitial(::MemoryStack)

        internal fun <R> allocate(block: MemoryStack.() -> R): R {
            val stack = Current.get()
            stack.push()
            return try {
                stack.block()
            } finally {
                stack.pop()
            }
        }
    }
}

/**
 * Executes [block] with a temporary frame on the current thread's fixed-capacity memory stack.
 */
fun <R> stackAlloc(block: MemoryStack.() -> R): R = MemoryStack.allocate(block)
