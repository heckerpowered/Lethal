/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.prepare

/**
 * Establishes a point at which resources used by pending GPU work may be released.
 *
 * The implementation must cover every use of resources retired by its caller, not merely command
 * recording on the CPU. Returning normally proves completion; a failed wait leaves completion
 * unknown and the caller must retain potentially active resources for a later retry.
 *
 * Waiting is an explicit fallible operation, not part of final resource destruction.
 */
fun interface GpuQuiescence {
    fun awaitIdle()
}
