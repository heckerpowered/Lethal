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
     * A SPIR-V module encoded as a little-endian sequence of 32-bit words.
     *
     * The binary must contain a valid SPIR-V module for the execution environment in which it
     * will be consumed. [ShaderBinary] only represents the encoded bytes and does not perform
     * SPIR-V validation.
     *
     * Some graphics APIs require SPIR-V to have been validated before it is submitted. In
     * particular, OpenGL permits invalid SPIR-V modules to result in undefined behavior.
     *
     * See [SPIR-V Unified Specification](https://registry.khronos.org/SPIR-V/specs/unified1/SPIRV.html)
     * and [ARB_gl_spirv](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_gl_spirv.txt).
     */
    SpirV(ByteOrder.LITTLE_ENDIAN),
}
