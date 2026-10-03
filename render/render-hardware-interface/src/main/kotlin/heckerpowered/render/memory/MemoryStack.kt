/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import java.lang.reflect.Method
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.getOrSet

/**
 * Keeps a fixed-capacity native region for reuse by nested memory frames.
 *
 * Keep one stack for the rendering context and use the receiver supplied by [frame] to reserve,
 * read, write, or view its memory. Each frame saves the allocation pointer in the caller's scope
 * and restores it on exit, so temporary regions can be reused without a frame registry.
 * Addresses and NIO views must not outlive the frame that reserved their storage. The stack
 * neither grows nor moves that storage while addresses are in use, and is confined to one thread.
 */
class MemoryStack(capacity: Int, addressOf: (ByteBuffer) -> NativeAddress) {
    internal val memory: ByteBuffer
    internal val baseAddress: NativeAddress

    @PublishedApi
    internal val limitAddress: NativeAddress

    @PublishedApi
    internal var pointer: NativeAddress

    init {
        require(capacity > 0) { "Memory stack capacity must be positive" }
        memory = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        baseAddress = addressOf(memory)

        check(baseAddress.rawValue != 0L) { "Direct memory address must not be zero" }
        pointer = baseAddress
        limitAddress = baseAddress + capacity
    }

    constructor(capacity: Int = DEFAULT_CAPACITY) : this(capacity, DirectBufferAddresses::address)

    inline fun <R> frame(block: MemoryFrame.() -> R): R {
        val previous = pointer
        return try {
            MemoryFrame(this).block()
        } finally {
            pointer = previous
        }
    }

    companion object {
        private const val DEFAULT_CAPACITY = 64 * 1024
    }
}

/**
 * Returns the native address corresponding to index zero of a direct buffer.
 *
 * The address is independent of the buffer's position and limit. For a slice, index zero is
 * the start of that slice's selected region, which can differ from the underlying allocation's
 * base address. Direct read-only views are also supported.
 *
 * The address does not extend the storage's lifetime. The caller must keep the buffer and
 * its native storage valid until all uses of the address have finished.
 */
fun directBufferAddress(buffer: ByteBuffer): NativeAddress {
    require(buffer.isDirect) { "A native address requires a direct buffer" }
    val address = DirectBufferAddresses.address(buffer)
    check(address.rawValue != 0L) { "Direct memory address must not be zero" }
    return address
}

private object DirectBufferAddresses {
    private val AddressMethod = findAddressMethod()

    fun address(buffer: ByteBuffer): NativeAddress {
        val method = AddressMethod ?: return NativeAddress(UnsafeNativeMemory.address(buffer))
        return NativeAddress((invokeMemoryMethod(method, null, buffer) as Number).toLong())
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

private val threadMemoryLocal = ThreadLocal<MemoryStack>()

val memoryStack: MemoryStack
    get() = threadMemoryLocal.getOrSet { MemoryStack() }
