/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

/**
 * Runs [operation], performing [cleanup] only if it fails before returning its result.
 *
 * Successful completion transfers responsibility for the result to the caller. Failure cleanup
 * must restore ownership or release resources; if it fails, the process terminates. Otherwise,
 * the original failure propagates unchanged, including Errors. Neither callback allows a
 * non-local return that could bypass the successful handoff.
 */
inline fun <R> withFailureCleanup(crossinline operation: () -> R, crossinline cleanup: () -> Unit): R {
    var completed = false
    try {
        val result = operation()
        completed = true
        return result
    } finally {
        if (!completed) terminateOnFailure { cleanup() }
    }
}
