/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.security

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Untrusted data is being unwrapped without contextual validation",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class UnsafeUntrustedAccess

@JvmInline
value class Untrusted<out T>(private val value: T) {
    @UnsafeUntrustedAccess
    internal fun unsafeUnwrap(): T {
        return value
    }

    override fun toString(): String {
        return "<Untrusted>"
    }
}