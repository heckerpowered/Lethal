/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reserves and accesses temporary memory during a [MemoryStack.frame] call.
 *
 * Address-based writers and NIO views use the same storage. Nested frames can read outer
 * reservations without copying them; exiting a frame releases only the space reserved since
 * it began. This receiver and any views of that temporary space must not escape their scope.
 */
@JvmInline
value class MemoryFrame @PublishedApi internal constructor(@PublishedApi internal val memoryStack: MemoryStack) {
    /**
     * Reserves an aligned range without initializing its bytes.
     *
     * Alignment includes any padding before the returned address, even for a zero-byte range.
     * Invalid arguments or insufficient capacity leave the allocation pointer unchanged.
     */
    fun reserve(byteCount: Int, alignment: Int): NativeAddress {
        return checkNotNull(tryReserve(byteCount, alignment)) { reservationFailure(byteCount, alignment) }
    }

    /**
     * Reserves an aligned native range, returning null when the remaining storage cannot hold it.
     *
     * Failure leaves the allocation pointer unchanged. Invalid sizes or alignments still throw;
     * the caller chooses any fallback storage and must keep the address within its frame.
     */
    fun tryReserve(byteCount: Int, alignment: Int): NativeAddress? {
        val address = reservationAddress(byteCount, alignment)
        if (!reservationFits(address, byteCount)) return null
        pointer = address + byteCount
        return address
    }

    /**
     * Reserves temporary native bytes for an API that accepts a [ByteBuffer] rather than an address.
     *
     * For example, a native state query can write into `reserveBuffer(64, 4).asIntBuffer()`.
     * The buffer shares this stack's existing storage; no separate native allocation is made and
     * its contents are not initialized. The returned writable direct view starts at position zero,
     * has limit and capacity [byteCount], and uses native byte order. NIO view objects may still be
     * allocated. Changing their position or limit does not change the stack's allocation pointer.
     *
     * The view and any slices or typed views derived from it must not outlive the surrounding
     * [MemoryStack.frame]. A native call must finish consuming the bytes before that frame returns;
     * retaining a Java buffer reference does not prevent the stack from reusing its storage.
     *
     * [alignment] is a positive power of two applied to the native address, including for a
     * zero-byte request. Insufficient space, including alignment padding, fails without advancing
     * the pointer. Use [tryReserveBuffer] when the caller can choose another storage path.
     *
     * @throws IllegalArgumentException if [byteCount] is negative or [alignment] is invalid.
     * @throws IllegalStateException if the aligned reservation does not fit in this stack.
     */
    fun reserveBuffer(byteCount: Int, alignment: Int = 1): ByteBuffer =
        asByteBuffer(reserve(byteCount, alignment), byteCount)

    /**
     * Tries the same reservation as [reserveBuffer], returning `null` only when it does not fit.
     *
     * A shader loader can use this to copy small heap-backed binaries into the stack, while
     * choosing independent storage for larger inputs. This method itself never grows the stack
     * or allocates fallback storage. A capacity failure leaves the pointer unchanged, including
     * any alignment padding; invalid arguments still throw rather than masquerading as exhaustion.
     * Successful views have the contents, byte order, bounds, and lifetime of [reserveBuffer].
     *
     * @throws IllegalArgumentException if [byteCount] is negative or [alignment] is invalid.
     */
    fun tryReserveBuffer(byteCount: Int, alignment: Int = 1): ByteBuffer? {
        val address = tryReserve(byteCount, alignment) ?: return null
        return asByteBuffer(address, byteCount)
    }

    /**
     * Views already reserved stack memory as a [ByteBuffer], without copying or reserving again.
     *
     * Use this when an address-based writer and a NIO-based native call need the same bytes.
     * For example, reserve sixteen bytes, fill them with [storeFloat4], then pass the resulting
     * byte-buffer view to the native call while still inside the frame.
     *
     * [address] must select live memory from this stack. The writable direct view starts at
     * position zero, has limit and capacity [byteCount], and uses native byte order. Both access
     * forms observe the same contents. Even `clear()` cannot expand the view beyond that range.
     * Creating or repositioning the view does not advance the allocation pointer.
     *
     * Bounds are checked against this stack and its currently reserved prefix, not individual
     * allocation records. The caller remains responsible for selecting a live reservation rather
     * than padding or a stale address reused by a later frame. The view and its derived views must
     * not escape this frame. This method cannot wrap arbitrary externally allocated native memory.
     *
     * @throws IllegalArgumentException if [byteCount] is negative.
     * @throws IllegalStateException if the range is outside this stack's reserved memory.
     */
    fun asByteBuffer(address: NativeAddress, byteCount: Int): ByteBuffer {
        require(byteCount >= 0) { "Buffer view size must be non-negative" }
        val start = offset(address, byteCount)
        val reservedBytes = pointer.rawValue - memoryStack.baseAddress.rawValue
        val fitsReservedMemory = start.toLong() + byteCount <= reservedBytes
        check(fitsReservedMemory) { "Buffer view extends beyond this stack's reserved memory" }
        return bufferAt(start, byteCount)
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
    internal fun alignUp(address: NativeAddress, alignment: Int): NativeAddress =
        NativeAddress((address.rawValue + alignment - 1L) and -alignment.toLong())

    private val memory: ByteBuffer
        get() = memoryStack.memory

    private fun bufferAt(start: Int, byteCount: Int): ByteBuffer {
        val selection = memory.duplicate()
        selection.position(start)
        selection.limit(start + byteCount)
        // A slice bounds capacity as well as limit, so clear() cannot expose neighboring allocations.
        return selection.slice().order(ByteOrder.nativeOrder())
    }

    private fun reservationAddress(byteCount: Int, alignment: Int): NativeAddress {
        require(byteCount >= 0) { "Memory reservation size must be non-negative" }
        val alignmentIsPowerOfTwo = alignment > 0 && alignment and (alignment - 1) == 0
        require(alignmentIsPowerOfTwo) { "Memory reservation alignment must be a positive power of two" }
        return alignUp(pointer, alignment)
    }

    private fun reservationFits(address: NativeAddress, byteCount: Int): Boolean =
        address.rawValue >= pointer.rawValue && address.rawValue <= limitAddress.rawValue &&
                byteCount.toLong() <= limitAddress.rawValue - address.rawValue

    private fun reservationFailure(byteCount: Int, alignment: Int): String =
        "Memory stack overflow: requested=$byteCount bytes, alignment=$alignment, remaining=${limitAddress.rawValue - pointer.rawValue} bytes"

    private fun offset(address: NativeAddress, byteCount: Int): Int {
        val offset = address.rawValue - memoryStack.baseAddress.rawValue
        val offsetFitsBuffer = offset >= 0L && offset <= Int.MAX_VALUE
        val rangeFitsStorage = byteCount >= 0 && offset + byteCount <= memory.capacity()
        check(offsetFitsBuffer && rangeFitsStorage) { "Memory access is outside this stack" }
        return offset.toInt()
    }
}
