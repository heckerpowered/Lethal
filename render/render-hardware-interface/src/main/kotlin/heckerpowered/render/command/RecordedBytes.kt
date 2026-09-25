/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command

import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.memory.Size
import java.nio.ByteBuffer

/**
 * Keeps the host bytes of an upload or push-constant update after its source address expires.
 *
 * A recording can upload A, reuse the same temporary address to prepare B, then upload B. Each
 * command needs its own saved bytes even though both calls supplied the same address. This value
 * captures that input; it does not snapshot GPU resources referenced by copies or draws.
 *
 * Storage is split into bounded host arrays rather than narrowing a [Size] to one Int-sized array.
 * [forEachChunk] exposes read-only buffers in byte order, so a backend can fill its own staging
 * allocation without gaining mutable access to the saved input. Chunk boundaries have no GPU
 * alignment or submission meaning. Host allocation can still fail when memory is exhausted.
 *
 * These are host-owned copies, not mapped GPU storage. A backend may release them after it has
 * copied their contents into independently retained upload storage. That upload storage must
 * separately survive its last GPU access.
 */
class RecordedBytes private constructor(
    val sizeBytes: Size,
    private val chunks: List<ByteArray>,
) {
    /**
     * Visits consecutive byte ranges without exposing writable storage.
     *
     * Each buffer has position zero and contains exactly that chunk's bytes. Its position and
     * limit can be changed by the consumer without affecting a later visit. Bytes are not
     * interpreted as numbers, and no endian conversion is performed.
     */
    fun forEachChunk(consume: (offsetBytes: Size, bytes: ByteBuffer) -> Unit) {
        var offset: Size = 0
        for (chunk in chunks) {
            consume(offset, ByteBuffer.wrap(chunk).asReadOnlyBuffer())
            offset += chunk.size
        }
    }

    /**
     * Supplies the platform operation that reads native host memory into a host array.
     *
     * Implementations copy all requested bytes before returning and retain neither the address
     * nor the writable destination. The caller supplies a readable source range; neither a
     * nonzero address nor this adapter proves that an arbitrary native allocation is valid.
     * This is host memory access, not a GPU readback or a deferred upload operation.
     */
    fun interface Reader {
        fun read(sourceAddress: NativeAddress, destination: ByteArray, destinationOffset: Int, byteCount: Int)
    }

    companion object {
        /** Saves a contiguous host range now; zero bytes do not dereference the address. */
        fun capture(sourceAddress: NativeAddress, sizeBytes: Size, reader: Reader): RecordedBytes {
            require(sizeBytes >= 0) { "Recorded byte count must be non-negative" }
            val builder = Builder(sizeBytes, reader)
            builder.append(sourceAddress, 0, sizeBytes)
            return builder.finish()
        }

        /**
         * Saves only occupied texel rows, removing the source layout's inter-row and slice gaps.
         *
         * The result uses [TextureDataLayout.TightlyPacked]. No gap or final-row padding is read,
         * so a native upload cannot later depend on bytes outside the caller's texel data.
         */
        fun captureTexture(sourceAddress: NativeAddress, footprint: TextureDataLayout.Footprint, reader: Reader): RecordedBytes {
            // The validated footprint contains disjoint rows, so their total fits its enclosing span.
            val size = footprint.rowSizeBytes * footprint.rowCount * footprint.sliceCount
            val builder = Builder(size, reader)
            var slice: Size = 0
            while (slice < footprint.sliceCount) {
                for (row in 0 until footprint.rowCount) {
                    builder.append(sourceAddress, footprint.rowOffsetBytes(row, slice), footprint.rowSizeBytes)
                }
                slice++
            }
            return builder.finish()
        }
    }

    private class Builder(private val size: Size, private val reader: Reader) {
        private val chunks = ArrayList<ByteArray>()
        private var written: Size = 0
        private var current: ByteArray? = null
        private var used = 0

        fun append(baseAddress: NativeAddress, sourceOffset: Size, byteCount: Size) {
            require(byteCount <= size - written) { "Recorded input exceeds its selected size" }
            var copied: Size = 0
            while (copied < byteCount) {
                var chunk = current
                if (chunk == null || used == chunk.size) {
                    chunk = ByteArray(minOf(size - written, 64 * 1024L).toInt())
                    chunks.add(chunk)
                    current = chunk
                    used = 0
                }
                val count = minOf(byteCount - copied, (chunk.size - used).toLong()).toInt()
                val address = addressAt(baseAddress, sourceOffset + copied, count)
                reader.read(address, chunk, used, count)
                used += count
                written += count
                copied += count
            }
        }

        fun finish(): RecordedBytes {
            check(written == size) { "Recorded input is incomplete" }
            return RecordedBytes(size, chunks)
        }
    }
}

private fun addressAt(base: NativeAddress, offset: Size, count: Int): NativeAddress {
    require(base.rawValue != 0L) { "Nonempty host input requires a nonzero address" }
    val address = base.rawValue.toULong()
    require(offset >= 0 && offset.toULong() <= ULong.MAX_VALUE - address) { "Host address offset wraps the address space" }
    val start = address + offset.toULong()
    require((count - 1).toULong() <= ULong.MAX_VALUE - start) { "Host read wraps the address space" }
    return NativeAddress(start.toLong())
}
