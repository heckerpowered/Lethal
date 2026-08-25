/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException

/**
 * Binary-encoded shader code accepted by a graphics backend.
 *
 * [format] defines the exact encoding stored in [bytes]. A shader binary is not necessarily native
 * GPU machine code; intermediate representations may still require validation, specialization,
 * translation, optimization, or driver compilation.
 *
 * Instances are created through [viewOf] or [copyOf], which make the underlying storage ownership
 * explicit.
 */
class ShaderBinary private constructor(
    val storage: ByteBuffer,
    val format: ShaderBinaryFormat,
    override val label: String,
) : ShaderCode {
    /**
     * Returns an independent read-only view containing the complete encoded shader binary.
     *
     * Every returned buffer satisfies the following conditions:
     *
     * - `position() == 0`;
     * - `limit() == capacity()`;
     * - the range `[0, capacity())` contains exactly one binary encoded according to [format];
     * - its byte order is [ShaderBinaryFormat.byteOrder];
     * - attempts to modify its contents throw [ReadOnlyBufferException].
     *
     * Changing the returned buffer's position, limit, mark, or byte order does not affect this
     * object or buffers returned by subsequent accesses.
     *
     * Whether the underlying byte storage is shared with another buffer depends on whether this
     * object was created through [viewOf] or [copyOf].
     */
    val bytes: ByteBuffer
        get() = storage
            .duplicate()
            .order(format.byteOrder)

    val sizeInBytes: Int
        get() = storage.capacity()

    override fun toString(): String =
        "ShaderBinary(format=$format, sizeInBytes=$sizeInBytes, label=$label)"

    companion object {
        /**
         * Creates a shader binary view over the remaining range of [bytes].
         *
         * The range `[bytes.position(), bytes.limit())` becomes the complete binary exposed by the
         * resulting object. The input buffer's position, limit, mark, and byte order are not
         * modified.
         *
         * The resulting object has independent buffer-view state but shares the underlying byte
         * storage with [bytes]. The caller must ensure that the shared bytes remain valid and
         * unchanged for the entire lifetime of the resulting [ShaderBinary].
         *
         * The selected bytes are interpreted according to [format]. This operation does not
         * validate the encoded shader contents.
         *
         * This operation does not copy the encoded binary.
         */
        fun viewOf(bytes: ByteBuffer, format: ShaderBinaryFormat, label: String): ShaderBinary {
            val storage = bytes
                .slice()
                .asReadOnlyBuffer()
                .order(format.byteOrder)

            return ShaderBinary(storage, format, label)
        }

        /**
         * Creates a shader binary containing an independent copy of the remaining range of
         * [bytes].
         *
         * The range `[bytes.position(), bytes.limit())` is copied without modifying the input
         * buffer. No validation of the encoded shader is performed.
         */
        fun copyOf(bytes: ByteBuffer, format: ShaderBinaryFormat, label: String): ShaderBinary {
            val source = bytes.slice()

            val storage = ByteBuffer
                .allocateDirect(bytes.remaining())
                .order(format.byteOrder)
                .apply {
                    put(source)
                    flip()
                }
                .asReadOnlyBuffer()
                .order(format.byteOrder)

            return ShaderBinary(storage, format, label)
        }
    }
}
