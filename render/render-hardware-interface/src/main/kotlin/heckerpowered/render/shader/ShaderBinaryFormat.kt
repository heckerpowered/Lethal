/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encoding of a binary shader representation.
 *
 * A binary format defines the exact byte representation expected in [ShaderBinary.bytes],
 * including its byte order, structural alignment, and container-level layout.
 *
 * This describes how shader code is encoded, not where the encoded data was obtained. In
 * particular, a binary loaded from a cache is not necessarily a backend-native cache artifact.
 *
 * The presence of a format in this enumeration does not guarantee that every graphics backend or
 * device supports it.
 */
enum class ShaderBinaryFormat(
    /**
     * Canonical byte order used to encode multibyte values in this format.
     *
     * This property describes the actual byte representation. The mutable byte-order state of a
     * [ByteBuffer] does not itself convert or reorder any bytes.
     */
    val byteOrder: ByteOrder,
) {
    /**
     * One complete SPIR-V module encoded as a little-endian sequence of 32-bit words.
     *
     * The encoded byte sequence must satisfy the following requirements:
     *
     * - it contains at least the five-word SPIR-V header;
     * - its size is a multiple of four bytes;
     * - its first word is the SPIR-V magic number `0x07230203`;
     * - it contains exactly one complete SPIR-V module;
     * - it contains no external header, byte-order mark, compression wrapper, or trailing
     *   application metadata.
     *
     * SPIR-V is an intermediate representation rather than guaranteed native GPU machine code.
     * A graphics backend may still validate, specialize, translate, optimize, or compile it.
     */
    SpirV(ByteOrder.LITTLE_ENDIAN),
}
