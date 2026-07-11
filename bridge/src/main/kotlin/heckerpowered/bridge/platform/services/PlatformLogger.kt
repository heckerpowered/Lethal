/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.platform.services

interface PlatformLogger {
    fun debug(message: String)

    fun info(message: String)

    fun warn(message: String)

    fun error(message: String, throwable: Throwable? = null)
}
