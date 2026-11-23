/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import com.mojang.logging.LogUtils
import heckerpowered.bridge.platform.services.PlatformLogger

/**
 * Routes bridge diagnostics to Minecraft’s logger at the requested severity. Errors can carry the
 * original throwable so the host log retains the failure context.
 */
class MinecraftPlatformLogger : PlatformLogger {
    private val logger = LogUtils.getLogger()

    override fun debug(message: String) = logger.debug(message)

    override fun info(message: String) = logger.info(message)

    override fun warn(message: String) = logger.warn(message)

    override fun error(message: String, throwable: Throwable?) = logger.error(message, throwable)
}
