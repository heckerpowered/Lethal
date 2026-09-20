/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.binding

/**
 * Limits what a shader may do through one storage binding.
 *
 * A buffer can permit storage access while a particular binding exposes it only for reading.
 * Another binding can allow writing the same resource. This describes access through the slot,
 * not a change to the resource's usage or a dependency between those accesses.
 */
enum class StorageAccess {
    /** Allows reads, but not writes or read-modify-write atomics. */
    ReadOnly,

    /** Allows writes without reading the previous value; read-modify-write atomics are excluded. */
    WriteOnly,

    /** Allows reads and writes. Atomics additionally require operation, format, and device support. */
    ReadWrite,
}
