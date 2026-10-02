/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.memory.directBufferAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Keeps a host-byte snapshot until preparation records its upload to GPU storage.
 *
 * The input array is copied immediately, so later caller changes do not affect prepared data.
 * Bytes are retained as supplied; this value does not infer element layouts or convert byte order.
 */
class UploadData(bytes: ByteArray) {
    private val data = bytes.copyOf()
    val sizeBytes: Int get() = data.size
    fun copyTo(destination: ByteBuffer) {
        destination.put(data)
    }

    /**
     * Makes non-empty snapshot bytes available at a temporary native address during [consume].
     *
     * The consumer must finish reading the bytes before returning and must not retain the address.
     * The supplied stack is reused when its remaining storage fits the snapshot. Otherwise,
     * independent staging holds this upload, including snapshots larger than the stack capacity.
     * Both paths restore the supplied frame on failure. Independent staging remains alive until
     * the consumer exits. Use the stack on its owning thread; empty snapshots are not accepted.
     */
    fun consumeNative(stack: MemoryStack, consume: (NativeAddress) -> Unit) {
        require(data.isNotEmpty())
        stack.frame {
            val address = tryReserve(data.size, 1)
            if (address != null) {
                asByteBuffer(address, data.size).put(data)
                consume(address)
                return@frame
            }
            val staging = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder())
            staging.put(data)
            try {
                consume(directBufferAddress(staging))
            } finally {
                // The native address does not retain its allocation; keep staging live through consumption.
                staging.clear()
            }
        }
    }
}
