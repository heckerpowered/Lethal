/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

/**
 * Exposes allocations that remain valid until the surrounding [MemoryStack.frame] returns.
 *
 * A frame is a zero-allocation view of its owning stack. It must not escape the block that receives it.
 */
@JvmInline
value class MemoryFrame @PublishedApi internal constructor(@PublishedApi internal val memoryStack: MemoryStack) {
    fun reserve(byteCount: Int, alignment: Int): NativeAddress = memoryStack.reserve(byteCount, alignment)

    fun loadFloat(address: NativeAddress): Float = memoryStack.loadFloat(address)

    fun loadInt(address: NativeAddress): Int = memoryStack.loadInt(address)

    fun loadFloats(address: NativeAddress, destination: FloatArray) {
        memoryStack.loadFloats(address, destination)
    }

    fun storeFloat(address: NativeAddress, value: Float) {
        memoryStack.storeFloat(address, value)
    }

    fun storeFloat2(address: NativeAddress, x: Float, y: Float) {
        memoryStack.storeFloat2(address, x, y)
    }

    fun storeFloat3(address: NativeAddress, x: Float, y: Float, z: Float) {
        memoryStack.storeFloat3(address, x, y, z)
    }

    fun storeFloat4(address: NativeAddress, x: Float, y: Float, z: Float, w: Float) {
        memoryStack.storeFloat4(address, x, y, z, w)
    }

    fun storeInt(address: NativeAddress, value: Int) {
        memoryStack.storeInt(address, value)
    }

    fun storeFloats(address: NativeAddress, values: FloatArray) {
        memoryStack.storeFloats(address, values)
    }

    fun clear(address: NativeAddress, byteCount: Int) {
        memoryStack.clear(address, byteCount)
    }

    @PublishedApi
    internal val limitAddress: NativeAddress
        get() = memoryStack.limitAddress

    @PublishedApi
    internal var pointer: NativeAddress
        get() = memoryStack.pointer
        set(value) {
            memoryStack.pointer = value
        }

    @PublishedApi
    internal fun alignUp(address: NativeAddress, alignment: Int): NativeAddress = memoryStack.alignUp(address, alignment)
}
