/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import java.lang.reflect.Method
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Allocates short-lived native memory from one fixed-capacity region.
 *
 * A rendering context owns its stack and confines it to the rendering thread. Each [frame] stores the previous pointer
 * in the inlined caller frame, so nested allocations require no frame array or frame-depth state. Addresses allocated
 * by a frame become invalid when that frame returns. Every allocation is bounds-checked before the pointer advances.
 */
class MemoryStack @PublishedApi internal constructor(capacity: Int, addressOf: (ByteBuffer) -> NativeAddress) {
    private val capacity = capacity
    private val memory: ByteBuffer
    private val baseAddress: NativeAddress

    @PublishedApi
    internal val limitAddress: NativeAddress

    @PublishedApi
    internal var pointer: NativeAddress

    init {
        require(capacity > 0) { "Memory stack capacity must be positive" }
        memory = ByteBuffer.allocateDirect(capacity).order(NATIVE_ORDER)
        baseAddress = addressOf(memory)

        check(baseAddress.rawValue != 0L) { "Direct memory address must not be zero" }
        pointer = baseAddress
        limitAddress = baseAddress + capacity
    }

    constructor(capacity: Int = DEFAULT_CAPACITY) : this(capacity, DirectBufferAddresses::address)

    inline fun <R> frame(block: MemoryFrame.() -> R): R {
        val frame = pointer
        return try {
            MemoryFrame(this).block()
        } finally {
            pointer = frame
        }
    }

    /**
     * Reserves one aligned region in the current frame.
     *
     * The returned address remains valid until the surrounding [frame] returns.
     */
    @PublishedApi
    internal fun reserve(byteCount: Int, alignment: Int): NativeAddress {
        require(byteCount >= 0) { "Memory reservation size must be non-negative" }
        require(alignment > 0 && alignment and (alignment - 1) == 0) { "Memory reservation alignment must be a positive power of two" }

        val address = alignUp(pointer, alignment)
        val end = address + byteCount
        check(end.rawValue in address.rawValue..limitAddress.rawValue) { "Memory stack overflow: requested=${end.rawValue - pointer.rawValue} bytes, remaining=${limitAddress.rawValue - pointer.rawValue} bytes" }
        pointer = end
        return address
    }

    fun loadFloat(address: NativeAddress): Float = memory.getFloat(offset(address, Float.SIZE_BYTES))

    fun loadInt(address: NativeAddress): Int = memory.getInt(offset(address, Int.SIZE_BYTES))

    fun loadFloats(address: NativeAddress, destination: FloatArray) {
        val offset = offset(address, Math.multiplyExact(destination.size, Float.SIZE_BYTES))
        destination.indices.forEach { index -> destination[index] = memory.getFloat(offset + index * Float.SIZE_BYTES) }
    }

    fun storeFloat(address: NativeAddress, value: Float) {
        memory.putFloat(offset(address, Float.SIZE_BYTES), value)
    }

    fun storeFloat2(address: NativeAddress, x: Float, y: Float) {
        val offset = offset(address, Float.SIZE_BYTES * 2)
        memory.putFloat(offset, x)
        memory.putFloat(offset + Float.SIZE_BYTES, y)
    }

    fun storeFloat3(address: NativeAddress, x: Float, y: Float, z: Float) {
        val offset = offset(address, Float.SIZE_BYTES * 3)
        memory.putFloat(offset, x)
        memory.putFloat(offset + Float.SIZE_BYTES, y)
        memory.putFloat(offset + Float.SIZE_BYTES * 2, z)
    }

    fun storeFloat4(address: NativeAddress, x: Float, y: Float, z: Float, w: Float) {
        val offset = offset(address, Float.SIZE_BYTES * 4)
        memory.putFloat(offset, x)
        memory.putFloat(offset + Float.SIZE_BYTES, y)
        memory.putFloat(offset + Float.SIZE_BYTES * 2, z)
        memory.putFloat(offset + Float.SIZE_BYTES * 3, w)
    }

    fun storeInt(address: NativeAddress, value: Int) {
        memory.putInt(offset(address, Int.SIZE_BYTES), value)
    }

    fun storeFloats(address: NativeAddress, values: FloatArray) {
        val offset = offset(address, Math.multiplyExact(values.size, Float.SIZE_BYTES))
        values.forEachIndexed { index, value -> memory.putFloat(offset + index * Float.SIZE_BYTES, value) }
    }

    fun clear(address: NativeAddress, byteCount: Int) {
        val offset = offset(address, byteCount)
        repeat(byteCount) { memory.put(offset + it, 0) }
    }

    internal fun copyBytes(address: NativeAddress, destination: ByteArray, destinationOffset: Int, byteCount: Int) {
        require(destinationOffset >= 0 && byteCount >= 0 && destinationOffset + byteCount <= destination.size) { "Memory copy exceeds the destination range" }
        val sourceOffset = offset(address, byteCount)
        repeat(byteCount) { index -> destination[destinationOffset + index] = memory.get(sourceOffset + index) }
    }

    internal fun contentEquals(address: NativeAddress, other: ByteArray, otherOffset: Int, byteCount: Int): Boolean {
        require(otherOffset >= 0 && byteCount >= 0 && otherOffset + byteCount <= other.size) { "Memory comparison exceeds the other range" }
        val sourceOffset = offset(address, byteCount)
        repeat(byteCount) { index -> if (memory.get(sourceOffset + index) != other[otherOffset + index]) return false }
        return true
    }

    @PublishedApi
    internal fun alignUp(address: NativeAddress, alignment: Int): NativeAddress = NativeAddress((address.rawValue + alignment - 1L) and -alignment.toLong())

    private fun offset(address: NativeAddress, byteCount: Int): Int {
        val offset = address.rawValue - baseAddress.rawValue
        check(offset >= 0L && offset <= Int.MAX_VALUE && byteCount >= 0 && offset + byteCount <= capacity) { "Memory access is outside this stack" }
        return offset.toInt()
    }

    private companion object {
        private val NATIVE_ORDER = ByteOrder.nativeOrder()

        private const val DEFAULT_CAPACITY = 64 * 1024
    }
}

private object DirectBufferAddresses {
    private val AddressMethod = findAddressMethod()
    private val UnsafeAddress = if (AddressMethod == null) UnsafeDirectBufferAddress() else null

    fun address(buffer: ByteBuffer): NativeAddress {
        val method = AddressMethod ?: return NativeAddress(requireNotNull(UnsafeAddress).address(buffer))
        return NativeAddress((method.invoke(null, buffer) as Number).toLong())
    }

    private fun findAddressMethod(): Method? {
        return runCatching {
            Class.forName("org.lwjgl.system.MemoryUtil").getMethod("memAddress0", ByteBuffer::class.java)
        }.getOrElse {
            runCatching {
                Class.forName("org.lwjgl.MemoryUtil").getMethod("getAddress0", Buffer::class.java)
            }.getOrNull()
        }
    }
}

private class UnsafeDirectBufferAddress {
    private val unsafeClass = Class.forName("sun.misc.Unsafe")
    private val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    private val addressOffset = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java).invoke(unsafe, Buffer::class.java.getDeclaredField("address")) as Long
    private val getLong = unsafeClass.getMethod("getLong", Any::class.java, Long::class.javaPrimitiveType)

    fun address(buffer: ByteBuffer): Long = getLong.invoke(unsafe, buffer, addressOffset) as Long
}