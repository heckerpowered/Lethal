/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

/**
 * Identifies one byte address in native host memory.
 *
 * Construction remains inside the memory implementation. Backend interoperation may use [rawValue] only when an
 * external API explicitly requires the machine address as a numeric value.
 */
@JvmInline
value class NativeAddress @PublishedApi internal constructor(val rawValue: Long) {
    operator fun plus(byteOffset: Int): NativeAddress = NativeAddress(rawValue + byteOffset)
}
