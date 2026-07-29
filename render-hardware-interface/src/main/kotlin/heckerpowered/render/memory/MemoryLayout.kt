/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

/**
 * Describes one aligned region within a [MemoryStack] allocation.
 */
@JvmInline
value class MemoryLayout private constructor(private val encoded: Long) {
    val byteCount: Int
        get() = encoded.toInt()

    val alignment: Int
        get() = (encoded ushr Int.SIZE_BITS).toInt()

    companion object {
        fun of(byteCount: Int, alignment: Int): MemoryLayout {
            require(byteCount >= 0) { "Memory layout size must be non-negative" }
            require(alignment > 0 && alignment and (alignment - 1) == 0) { "Memory layout alignment must be a positive power of two" }
            return MemoryLayout(alignment.toLong() shl Int.SIZE_BITS or (byteCount.toLong() and 0xFFFF_FFFFL))
        }
    }
}

fun bytes(count: Int): MemoryLayout = MemoryLayout.of(count, Byte.SIZE_BYTES)

fun floats(count: Int): MemoryLayout = MemoryLayout.of(Math.multiplyExact(count, Float.SIZE_BYTES), Float.SIZE_BYTES)

fun ints(count: Int): MemoryLayout = MemoryLayout.of(Math.multiplyExact(count, Int.SIZE_BYTES), Int.SIZE_BYTES)
