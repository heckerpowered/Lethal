/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge

/**
 * Verifies the shared JVM identity used by host objects enhanced through Mixin.
 *
 * These conversions do not create adapters. The host object and its bridge view must be the same object, so a failed
 * cast reports a broken platform implementation at the boundary where it is first observed.
 */
object MixinInterop {
    @JvmStatic
    fun <Access : Any> requireAccess(host: Any, accessType: Class<Access>): Access = accessType.cast(host)

    @JvmStatic
    fun <Host : Any> requireHost(access: Any, hostType: Class<Host>): Host = hostType.cast(access)
}

inline fun <reified Access : Any> requireAccess(host: Any): Access = MixinInterop.requireAccess(host, Access::class.java)

inline fun <reified Host : Any> requireHost(access: Any): Host = MixinInterop.requireHost(access, Host::class.java)
