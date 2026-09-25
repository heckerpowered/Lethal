/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

/**
 * Runs [operation] and terminates the process immediately if it throws.
 *
 * This function is intended for failures that cannot be meaningfully recovered
 * from, such as failures during resource destruction. It must not be used as a
 * replacement for ordinary exception handling.
 */
inline fun <R> terminateOnFailure(operation: () -> R): R {
    try {
        return operation()
    } catch (failure: Throwable) {
        terminateProcess(failure)
    }
}

@PublishedApi
internal fun terminateProcess(failure: Throwable): Nothing {
    failure.printStackTrace(System.err)

    val runtime = Runtime.getRuntime()
    while (true) {
        runCatching { runtime.halt(1) }
    }
}